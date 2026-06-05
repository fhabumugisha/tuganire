package com.tuganire.translation;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import com.tuganire.cache.TranslationCache;
import com.tuganire.golden.GoldenDictionaryService;
import com.tuganire.llm.LlmCallResult;
import com.tuganire.llm.LlmProvider;
import com.tuganire.llm.LlmProviderFactory;
import com.tuganire.llm.LlmSettings;
import com.tuganire.postprocessor.KinyarwandaPostProcessor;
import com.tuganire.postprocessor.ProcessedTranslation;
import com.tuganire.translation.normalizer.FrenchNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Canonical implementation of the 6-step translation pipeline (ARCHI section 5).
 *
 * <p>
 * Pipeline order:
 * <ol>
 * <li>Cache hit → return immediately ({@code fromCache=true}).
 * <li>Normalise source text (French only; Kinyarwanda skips this step).
 * <li>Golden-dictionary lookup → cache + return ({@code fromGoldenDictionary=true}).
 * <li>LLM translation with default provider, falling back to the configured fallback on <b>transient</b> errors only.
 * <li>Kinyarwanda post-processing (5 correction rules).
 * <li>Build response, write to cache, and return.
 * </ol>
 *
 * <p>
 * Each stage is wrapped in a Micrometer {@link Timer} tagged with {@code stage} for latency observability. A
 * {@code tuganire.translations.total} counter tracks all translation outcomes with tags for source language, target
 * language, cache status, golden-dictionary status, success, and (when an LLM call ran) the {@code provider} /
 * {@code model} / {@code fallback_used} that served it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class TranslationServiceImpl implements TranslationService {

    // -------------------------------------------------------------------------
    // Metric constants
    // -------------------------------------------------------------------------

    private static final String TIMER_NAME = "tuganire.translations.latency.seconds";
    private static final String COUNTER_NAME = "tuganire.translations.total";

    private static final String TAG_STAGE = "stage";
    private static final String STAGE_NORMALIZE = "normalize";
    private static final String STAGE_GOLDEN = "golden";
    private static final String STAGE_LLM = "llm";
    private static final String STAGE_POSTPROCESS = "postprocess";

    private static final String TAG_SRC = "src";
    private static final String TAG_TGT = "tgt";
    private static final String TAG_CACHED = "cached";
    private static final String TAG_GOLDEN = "golden";
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_MODEL = "model";
    private static final String TAG_FALLBACK_USED = "fallback_used";
    /** Sentinel value used when a translation completed before reaching the LLM stage (cache/golden). */
    private static final String TAG_VALUE_NONE = "none";

    /**
     * BCP-47 language tag for French — used to gate the French-only {@link FrenchNormalizer} (the golden-dictionary
     * service itself works for either direction).
     */
    private static final String LANG_FRENCH = "fr";

    // -------------------------------------------------------------------------
    // Collaborators (injected by constructor via @RequiredArgsConstructor)
    // -------------------------------------------------------------------------

    private final TranslationCache cache;
    private final FrenchNormalizer normalizer;
    private final GoldenDictionaryService goldenDict;
    private final LlmProviderFactory llmFactory;
    private final LlmSettings llmSettings;
    private final KinyarwandaPostProcessor postProcessor;
    private final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------------
    // TranslationService interface
    // -------------------------------------------------------------------------

    @Override
    public TranslationResponse translate(TranslationRequest request) {
        return translate(request.sourceText(), request.sourceLanguage(), request.targetLanguage());
    }

    @Override
    public TranslationResponse translate(String sourceText, Locale src, Locale tgt) {
        log.debug("translate: src={}, tgt={}, text='{}'", src, tgt, sourceText);

        // Step 0 — cache lookup
        Optional<TranslationResponse> cached = cache.find(sourceText, src, tgt);
        if (cached.isPresent()) {
            log.debug("Cache hit for text='{}'", sourceText);
            incrementCounter(src, tgt, true, false, true, TAG_VALUE_NONE, TAG_VALUE_NONE, false);
            return cached.get();
        }

        // Step 1 — normalise (French source only)
        String normalized = timed(STAGE_NORMALIZE,
                () -> LANG_FRENCH.equals(src.getLanguage()) ? normalizer.normalize(sourceText) : sourceText);
        log.debug("Normalized text='{}'", normalized);

        // Step 2 — golden-dictionary lookup
        Optional<TranslationResponse> golden = timed(STAGE_GOLDEN, () -> goldenDict.lookup(normalized, src, tgt));
        if (golden.isPresent()) {
            log.debug("Golden-dictionary hit for normalized='{}'", normalized);
            cache.put(sourceText, src, tgt, golden.get());
            incrementCounter(src, tgt, false, true, true, TAG_VALUE_NONE, TAG_VALUE_NONE, false);
            return golden.get();
        }

        // Step 3 — LLM translation with fallback (tagged latency + counter recorded inside the helper)
        LlmCallResult llmResult;
        try {
            llmResult = translateWithFallback(normalized, src, tgt);
        } catch (RuntimeException ex) {
            // Both providers failed (or the primary failed non-transiently): record success=false and re-raise so
            // the global exception handler turns this into a ProblemDetail. We do NOT increment the counter as a
            // successful translation in this branch.
            incrementCounter(src, tgt, false, false, false, TAG_VALUE_NONE, TAG_VALUE_NONE, false);
            throw ex;
        }

        // Step 4 — Kinyarwanda post-processing
        ProcessedTranslation processed = timed(STAGE_POSTPROCESS,
                () -> postProcessor.process(llmResult.text(), src, tgt));
        log.debug("Post-processor applied {} correction(s)", processed.appliedCorrections().size());

        // Step 5 — build response, cache, return
        TranslationResponse response = TranslationResponse.fromLlm(sourceText, processed.text(),
                processed.appliedCorrections());
        cache.put(sourceText, src, tgt, response);

        incrementCounter(src, tgt, false, false, true, llmResult.provider(), llmResult.model(),
                llmResult.fallbackUsed());
        return response;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Calls the provider that owns the model selected in settings; on a <i>transient</i> exception falls back to the
     * other configured provider. Non-retriable 4xx errors (bad prompt, content policy, model-not-found, auth) propagate
     * immediately — we must not pay for a second call when the first failure was caused by the input itself.
     *
     * <p>
     * The LLM-stage {@link Timer} is recorded manually here so the latency sample carries the {@code provider},
     * {@code model}, {@code success}, and {@code fallback_used} tags reflecting what actually ran.
     */
    private LlmCallResult translateWithFallback(String text, Locale src, Locale tgt) {
        LlmProvider primary = llmFactory.get(llmSettings.getActiveProvider());
        String primaryModel = llmSettings.getModel();
        long start = System.nanoTime();
        try {
            String result = primary.translate(text, src, tgt, primaryModel);
            recordLlmTimer(start, primary.name(), primaryModel, true, false);
            log.debug("LLM translation via provider='{}', model='{}' succeeded", primary.name(), primaryModel);
            return new LlmCallResult(result, primary.name(), primaryModel, false);
        } catch (RuntimeException ex) {
            if (!isTransient(ex)) {
                recordLlmTimer(start, primary.name(), primaryModel, false, false);
                log.warn("LLM provider '{}' failed non-transiently ({}); NOT falling back", primary.name(),
                        ex.toString());
                throw ex;
            }
            // Pick whichever configured provider is not the one that just failed.
            LlmProvider fallback = primary.name().equals(llmFactory.getDefault().name())
                    ? llmFactory.getFallback()
                    : llmFactory.getDefault();
            String fallbackModel = defaultModelFor(fallback);
            log.warn("LLM provider '{}' failed transiently ({}); falling back to '{}' (model={})", primary.name(),
                    ex.getMessage(), fallback.name(), fallbackModel);
            long fbStart = System.nanoTime();
            try {
                String result = fallback.translate(text, src, tgt, fallbackModel);
                recordLlmTimer(fbStart, fallback.name(), fallbackModel, true, true);
                return new LlmCallResult(result, fallback.name(), fallbackModel, true);
            } catch (RuntimeException fbEx) {
                recordLlmTimer(fbStart, fallback.name(), fallbackModel, false, true);
                log.error("Fallback provider '{}' also failed ({}); giving up", fallback.name(), fbEx.toString());
                throw fbEx;
            }
        }
    }

    /**
     * Classifies an exception thrown by an LLM call as retriable (transient infrastructure / overload / rate-limit) or
     * not (client-side / content / auth / model-not-found). Only transient errors should trigger a fallback call.
     */
    private static boolean isTransient(Throwable ex) {
        // Generic transport-level failures — always retriable.
        if (ex instanceof IOException) {
            return true;
        }
        // OpenAI Java SDK
        if (ex instanceof OpenAIRetryableException || ex instanceof OpenAIIoException) {
            return true;
        }
        if (ex instanceof OpenAIServiceException openAiSvc) {
            int status = openAiSvc.statusCode();
            return status == 429 || status >= 500;
        }
        // Anthropic Java SDK
        if (ex instanceof AnthropicRetryableException || ex instanceof AnthropicIoException) {
            return true;
        }
        if (ex instanceof AnthropicServiceException anthropicSvc) {
            int status = anthropicSvc.statusCode();
            return status == 429 || status >= 500;
        }
        // Unknown wrapper exceptions from either SDK: prefer to surface the original error rather than burn budget
        // on a potentially-doomed retry — unwrap if the cause is transient, otherwise treat as non-transient.
        if (ex instanceof OpenAIException || ex instanceof AnthropicException) {
            Throwable cause = ex.getCause();
            return cause != null && cause != ex && isTransient(cause);
        }
        // Last-resort: inspect the cause chain once. Avoid infinite loops.
        Throwable cause = ex.getCause();
        return cause != null && cause != ex && isTransient(cause);
    }

    /**
     * Returns the model id to call when a provider runs as the fallback (i.e. the selected model belonged to the other
     * provider). Picks the first configured model that names this provider, falling back to the active model id if the
     * fallback owns it (which would mean it is not actually a cross-provider fallback).
     */
    private String defaultModelFor(LlmProvider provider) {
        return llmSettings.getAvailableModels().stream().filter(m -> m.provider().equals(provider.name())).findFirst()
                .map(m -> m.id()).orElse(llmSettings.getModel());
    }

    /**
     * Wraps a supplier in a Micrometer {@link Timer} tagged with {@code stage}. Used for stages whose latency does not
     * depend on a specific LLM provider/model (the LLM stage itself is timed manually with richer tags — see
     * {@link #recordLlmTimer}).
     */
    private <T> T timed(String stage, java.util.function.Supplier<T> supplier) {
        return Timer.builder(TIMER_NAME).tag(TAG_STAGE, stage).register(meterRegistry).record(supplier);
    }

    /** Records a latency sample for the LLM stage with provider/model/success/fallback tags. */
    private void recordLlmTimer(long startNanos, String provider, String model, boolean success, boolean fallbackUsed) {
        long elapsed = System.nanoTime() - startNanos;
        Timer.builder(TIMER_NAME).tag(TAG_STAGE, STAGE_LLM).tag(TAG_PROVIDER, provider).tag(TAG_MODEL, model)
                .tag(TAG_SUCCESS, Boolean.toString(success)).tag(TAG_FALLBACK_USED, Boolean.toString(fallbackUsed))
                .register(meterRegistry).record(Duration.ofNanos(elapsed).toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Increments the translations total counter. {@code provider} / {@code model} are {@value #TAG_VALUE_NONE} when the
     * pipeline returned before the LLM ran (cache or golden-dictionary hit) or when both providers failed.
     */
    private void incrementCounter(Locale src, Locale tgt, boolean fromCache, boolean fromGolden, boolean success,
            String provider, String model, boolean fallbackUsed) {
        meterRegistry.counter(COUNTER_NAME, TAG_SRC, src.getLanguage(), TAG_TGT, tgt.getLanguage(), TAG_CACHED,
                Boolean.toString(fromCache), TAG_GOLDEN, Boolean.toString(fromGolden), TAG_SUCCESS,
                Boolean.toString(success), TAG_PROVIDER, provider, TAG_MODEL, model, TAG_FALLBACK_USED,
                Boolean.toString(fallbackUsed)).increment();
    }
}
