package com.tuganire.conversation;

import com.tuganire.admin.LlmUsageTracker;
import com.tuganire.config.LlmConfig;
import com.tuganire.golden.GoldenDictionaryService;
import com.tuganire.llm.LlmSettings;
import com.tuganire.llm.prompt.TranslationPromptBuilder;
import com.tuganire.postprocessor.KinyarwandaPostProcessor;
import com.tuganire.shared.util.PipelineMetrics;
import com.tuganire.translation.TranslationResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Default {@link StreamTranslationService}: runs the correction + translation pipeline on a per-request virtual thread
 * and emits SSE events as tokens arrive.
 *
 * <p>
 * Correction and translation are produced by a <b>single</b> streamed LLM call (system prompt from
 * {@link TranslationPromptBuilder#buildCorrectAndTranslateSystemPrompt}): the model writes the corrected source, a
 * separator line, then the translation, and {@link CorrectionTranslationSplitter} routes the tokens to the
 * {@code correction} / {@code translation} events. This saves a full LLM round-trip compared with two chained calls.
 *
 * <p>
 * The golden dictionary is checked on the raw transcript (instant answer, no LLM call) and again on the corrected
 * source (the validated translation then replaces the LLM one). {@link KinyarwandaPostProcessor} cleans the final RW
 * output. Provider routing (OpenAI vs Anthropic) follows the active model in {@link LlmSettings}. Every stage is timed
 * via {@link PipelineMetrics}, and real token usage (read from the final streamed chunk) goes to
 * {@link LlmUsageTracker}.
 *
 * <p>
 * Streaming is consumed synchronously: {@code stream().chatResponse()} returns a reactive {@code Flux} which is drained
 * with {@code toIterable()} on the virtual thread, so blocking is cheap.
 */
@Service
@Slf4j
class StreamTranslationServiceImpl implements StreamTranslationService {

    static final String PROVIDER_ANTHROPIC = "anthropic";
    static final String PROVIDER_OPENAI = "openai";
    static final String FEATURE_CORRECT_TRANSLATE = "stream-correct-translate";

    private static final double ANTHROPIC_TEMPERATURE = 0.1;
    private static final int MAX_TOKENS = 1024;
    private static final String LANG_RW = "rw";

    /** Word "imana" as a standalone token, any case — enforced to "Imana" deterministically. */
    private static final Pattern IMANA = Pattern.compile("\\bimana\\b", Pattern.CASE_INSENSITIVE);

    private static final String EVENT_CORRECTION = "correction";
    private static final String EVENT_CORRECTION_DONE = "correction-done";
    private static final String EVENT_TRANSLATION = "translation";
    private static final String EVENT_TRANSLATION_DONE = "translation-done";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    private static final String AUDIO_URL_TEMPLATE = "/api/v1/audio/speak.mp3?lang=%s&text=%s";

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final OpenAiChatModel openAiModel;
    private final AnthropicChatModel anthropicModel;
    private final LlmSettings llmSettings;
    private final TranslationPromptBuilder promptBuilder;
    private final GoldenDictionaryService goldenDictionaryService;
    private final KinyarwandaPostProcessor postProcessor;
    private final LlmUsageTracker usageTracker;
    private final MeterRegistry meterRegistry;
    private final List<String> reasoningModels;
    private final @Nullable String reasoningEffort;

    StreamTranslationServiceImpl(@Qualifier("openAiChatModel") OpenAiChatModel openAiModel,
            @Qualifier("anthropicChatModel") AnthropicChatModel anthropicModel, LlmSettings llmSettings,
            TranslationPromptBuilder promptBuilder, GoldenDictionaryService goldenDictionaryService,
            KinyarwandaPostProcessor postProcessor, LlmUsageTracker usageTracker, MeterRegistry meterRegistry,
            LlmConfig llmConfig) {
        this.openAiModel = openAiModel;
        this.anthropicModel = anthropicModel;
        this.llmSettings = llmSettings;
        this.promptBuilder = promptBuilder;
        this.goldenDictionaryService = goldenDictionaryService;
        this.postProcessor = postProcessor;
        this.usageTracker = usageTracker;
        this.meterRegistry = meterRegistry;
        this.reasoningModels = List.copyOf(llmConfig.openai().temperatureLockedModels());
        this.reasoningEffort = llmConfig.openai().reasoningEffort();
    }

    @Override
    public void stream(SseEmitter emitter, StreamTranslationRequest request) {
        executor.submit(() -> runPipeline(emitter, request));
    }

    /** Runs the full pipeline on a virtual thread; completes the emitter (or completes-with-error) before returning. */
    private void runPipeline(SseEmitter emitter, StreamTranslationRequest request) {
        long start = System.nanoTime();
        String lang = request.sourceLang();
        try {
            Locale srcLocale = Locale.forLanguageTag(request.sourceLang());
            Locale tgtLocale = Locale.forLanguageTag(request.targetLang());
            String rawSource = request.text();

            Optional<TranslationResponse> golden = goldenDictionaryService.lookup(rawSource, srcLocale, tgtLocale);
            if (golden.isPresent()) {
                // Exact golden hit on the transcript: answer instantly, no LLM call at all.
                log.debug("Golden short-circuit on raw transcript");
                send(emitter, EVENT_CORRECTION_DONE, new DoneText(finishSource(rawSource, lang)));
                finish(emitter, request.targetLang(), golden.get().translatedText());
                PipelineMetrics.record(meterRegistry, PipelineMetrics.STAGE_STREAM_TOTAL, start,
                        PipelineMetrics.TAG_LANG, lang, PipelineMetrics.TAG_OUTCOME, PipelineMetrics.OUTCOME_GOLDEN);
                return;
            }

            CorrectionTranslationSplitter result = streamCorrectAndTranslate(emitter, rawSource, srcLocale, tgtLocale);
            String corrected = result.isSplit() ? finishSource(result.correction(), lang) : "";
            String correctedSource = StringUtils.hasText(corrected) ? corrected : rawSource;
            if (!result.isSplit()) {
                // Model ignored the two-part format: treat everything it wrote as the translation.
                log.warn("Merged correction+translation output had no separator; using raw source as corrected text");
                send(emitter, EVENT_CORRECTION_DONE, new DoneText(correctedSource));
            }
            String rawTranslation = result.isSplit() ? result.translation() : result.correction();
            String finalTranslation = translateFinal(correctedSource, rawTranslation, srcLocale, tgtLocale);
            finish(emitter, request.targetLang(), finalTranslation);
            PipelineMetrics.record(meterRegistry, PipelineMetrics.STAGE_STREAM_TOTAL, start, PipelineMetrics.TAG_LANG,
                    lang, PipelineMetrics.TAG_OUTCOME, PipelineMetrics.OUTCOME_SUCCESS);
        } catch (Exception ex) {
            log.warn("Stream translation pipeline failed: {}", ex.getMessage(), ex);
            PipelineMetrics.record(meterRegistry, PipelineMetrics.STAGE_STREAM_TOTAL, start, PipelineMetrics.TAG_LANG,
                    lang, PipelineMetrics.TAG_OUTCOME, PipelineMetrics.OUTCOME_ERROR);
            tryEmitError(emitter, ex);
        }
    }

    /**
     * Final translation: a golden-dictionary hit on the corrected source wins over the LLM output; otherwise the LLM
     * output, run through the Kinyarwanda post-processor when the target is RW.
     */
    private String translateFinal(String correctedSource, String rawTranslation, Locale srcLocale, Locale tgtLocale) {
        Optional<TranslationResponse> golden = goldenDictionaryService.lookup(correctedSource, srcLocale, tgtLocale);
        if (golden.isPresent()) {
            return golden.get().translatedText();
        }
        if (LANG_RW.equalsIgnoreCase(tgtLocale.getLanguage())) {
            return postProcessor.process(rawTranslation, correctedSource, srcLocale, tgtLocale).text();
        }
        return rawTranslation;
    }

    /** Emits {@code translation-done} (with the lazy TTS URL) then {@code done}, and completes the emitter. */
    private void finish(SseEmitter emitter, String targetLang, String translation) {
        send(emitter, EVENT_TRANSLATION_DONE, new TranslationDone(translation, buildAudioUrl(targetLang, translation)));
        send(emitter, EVENT_DONE, new Empty());
        emitter.complete();
    }

    /** Kinyarwanda source text gets the deterministic {@link #tidy(String)} pass; French is returned trimmed. */
    private static String finishSource(String text, String lang) {
        return LANG_RW.equalsIgnoreCase(lang) ? tidy(text) : text == null ? "" : text.trim();
    }

    // -------------------------------------------------------------------------
    // Merged correction + translation — one streamed LLM call
    // -------------------------------------------------------------------------

    /**
     * Streams the merged call: correction tokens become {@code correction} events, {@code correction-done} is sent as
     * soon as the separator arrives (so the source bubble is final before the translation starts), and translation
     * tokens become {@code translation} events. Returns the splitter holding both full texts.
     */
    private CorrectionTranslationSplitter streamCorrectAndTranslate(SseEmitter emitter, String rawSource,
            Locale srcLocale, Locale tgtLocale) {
        String lang = srcLocale.getLanguage();
        CorrectionTranslationSplitter splitter = new CorrectionTranslationSplitter(
                TranslationPromptBuilder.CORRECTION_SEPARATOR,
                token -> send(emitter, EVENT_CORRECTION, new Token(token)),
                token -> send(emitter, EVENT_TRANSLATION, new Token(token)),
                correction -> send(emitter, EVENT_CORRECTION_DONE, new DoneText(finishSource(correction, lang))));

        String modelId = llmSettings.getModel();
        String provider = resolveProvider(modelId);
        ChatClient client = buildClient(promptBuilder.buildCorrectAndTranslateSystemPrompt(srcLocale, tgtLocale),
                provider);
        ChatOptions.Builder<?> options = buildOptions(provider, modelId);

        long start = System.nanoTime();
        boolean firstToken = true;
        Usage usage = null;
        for (ChatResponse response : client.prompt().user(rawSource).options(options).stream().chatResponse()
                .toIterable()) {
            Usage chunkUsage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
            if (chunkUsage != null && chunkUsage.getTotalTokens() != null && chunkUsage.getTotalTokens() > 0) {
                usage = chunkUsage;
            }
            String chunk = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            if (firstToken) {
                PipelineMetrics.record(meterRegistry, PipelineMetrics.STAGE_LLM_FIRST_TOKEN, start,
                        PipelineMetrics.TAG_PROVIDER, provider, PipelineMetrics.TAG_MODEL, modelId);
                firstToken = false;
            }
            splitter.accept(chunk);
        }
        splitter.finish();
        PipelineMetrics.record(meterRegistry, PipelineMetrics.STAGE_LLM_TOTAL, start, PipelineMetrics.TAG_PROVIDER,
                provider, PipelineMetrics.TAG_MODEL, modelId);
        recordUsage(provider, modelId, usage);
        return splitter;
    }

    /** Records real token usage from the final streamed chunk; zero when the provider did not report it. */
    private void recordUsage(String provider, String modelId, @Nullable Usage usage) {
        int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        usageTracker.track(provider, modelId, FEATURE_CORRECT_TRANSLATE, promptTokens, completionTokens);
    }

    /**
     * Deterministic Kinyarwanda safety net, mirroring {@code KinyarwandaCorrectionServiceImpl#tidy}: capitalise
     * {@code Imana}, capitalise the first letter, and ensure terminal punctuation. Re-implemented here because the
     * original is package-private to {@code com.tuganire.stt}.
     */
    private static String tidy(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return t;
        }
        t = IMANA.matcher(t).replaceAll("Imana");
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        char last = t.charAt(t.length() - 1);
        if (last != '.' && last != '!' && last != '?') {
            t = t + ".";
        }
        return t;
    }

    // -------------------------------------------------------------------------
    // Spring AI plumbing
    // -------------------------------------------------------------------------

    /** Builds a streaming {@link ChatClient} for the given system prompt on the given provider's model. */
    private ChatClient buildClient(String systemPrompt, String provider) {
        if (PROVIDER_ANTHROPIC.equals(provider)) {
            return ChatClient.builder(anthropicModel).defaultSystem(systemPrompt).build();
        }
        return ChatClient.builder(openAiModel).defaultSystem(systemPrompt).build();
    }

    /**
     * Anthropic: low temperature + explicit maxTokens. OpenAI: no temperature (GPT-5.x rejects it), usage reported in
     * the final chunk ({@code stream_options.include_usage}), and the configured {@code reasoning_effort} for the
     * reasoning models listed in {@code tuganire.llm.openai.temperature-locked-models}.
     */
    private ChatOptions.Builder<?> buildOptions(String provider, String modelId) {
        if (PROVIDER_ANTHROPIC.equals(provider)) {
            return AnthropicChatOptions.builder().model(modelId).temperature(ANTHROPIC_TEMPERATURE)
                    .maxTokens(MAX_TOKENS);
        }
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(modelId).streamUsage(true);
        if (StringUtils.hasText(reasoningEffort) && reasoningModels.contains(modelId)) {
            options.reasoningEffort(reasoningEffort);
        }
        return options;
    }

    /** Resolves the owning provider for a model id from the configured selectable models; defaults to OpenAI. */
    private String resolveProvider(String modelId) {
        return llmSettings.getAvailableModels().stream().filter(m -> m.id().equals(modelId)).map(m -> m.provider())
                .findFirst().orElse(PROVIDER_OPENAI);
    }

    // -------------------------------------------------------------------------
    // SSE helpers
    // -------------------------------------------------------------------------

    /** Builds the lazy TTS audio URL pointing at the existing {@code GET /api/v1/audio/speak.mp3} endpoint. */
    private String buildAudioUrl(String lang, String text) {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return String.format(AUDIO_URL_TEMPLATE, lang, encoded);
    }

    /** Sends a named SSE event with a JSON {@code data:} payload; surfaces I/O failures to the caller. */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            SseEventBuilder builder = SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON);
            emitter.send(builder);
        } catch (IOException ex) {
            throw new StreamSendException(event, ex);
        }
    }

    /** Best-effort emission of the terminal {@code error} event followed by {@code completeWithError}. */
    private void tryEmitError(SseEmitter emitter, Exception cause) {
        try {
            send(emitter, EVENT_ERROR, new ErrorMessage(messageOf(cause)));
        } catch (Exception sendFailure) {
            log.debug("Could not emit SSE error event: {}", sendFailure.getMessage());
        }
        emitter.completeWithError(cause);
    }

    private static String messageOf(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    // -------------------------------------------------------------------------
    // SSE payload records
    // -------------------------------------------------------------------------

    /** {@code correction} / {@code translation} payload: an incremental text chunk. */
    record Token(String token) {
    }

    /** {@code correction-done} payload: the full cleaned text. */
    record DoneText(String text) {
    }

    /** {@code translation-done} payload: the final translation plus its lazy TTS audio URL. */
    record TranslationDone(String text, String audioUrl) {
    }

    /** {@code error} payload: a human-readable message. */
    record ErrorMessage(String message) {
    }

    /** {@code done} payload: an empty object {@code {}}. */
    record Empty() {
    }

    /** Wraps an {@link IOException} raised while writing an SSE event so the pipeline can fail fast. */
    private static final class StreamSendException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        StreamSendException(String event, IOException cause) {
            super("Failed to send SSE event '" + event + "'", cause);
        }
    }
}
