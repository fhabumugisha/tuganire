package com.tuganire.conversation;

import com.tuganire.admin.LlmUsageTracker;
import com.tuganire.golden.GoldenDictionaryService;
import com.tuganire.llm.LlmSettings;
import com.tuganire.llm.prompt.TranslationPromptBuilder;
import com.tuganire.postprocessor.KinyarwandaPostProcessor;
import com.tuganire.translation.TranslationResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Default {@link StreamTranslationService}: runs the correction → translation pipeline on a per-request virtual thread
 * and emits SSE events as tokens arrive.
 *
 * <p>
 * Reuses existing assets: the French/Kinyarwanda correction system prompts (mirrored here as constants so a streaming
 * {@link ChatClient} can use them), {@link TranslationPromptBuilder} for the translation prompt,
 * {@link GoldenDictionaryService} for the instant golden short-circuit, and {@link KinyarwandaPostProcessor} for the
 * final RW cleanup. Provider routing (OpenAI vs Anthropic) follows the same rule as the non-streaming impls: the
 * provider that owns the active model in {@link LlmSettings}.
 *
 * <p>
 * Streaming is consumed synchronously: {@code chatClient.prompt().user(...).stream().content()} returns a reactive
 * {@code Flux<String>} which is drained with {@code toIterable()} on the virtual thread, so blocking is cheap.
 */
@Service
@Slf4j
class StreamTranslationServiceImpl implements StreamTranslationService {

    static final String PROVIDER_ANTHROPIC = "anthropic";
    static final String PROVIDER_OPENAI = "openai";
    static final String FEATURE_CORRECTION = "stream-correction";
    static final String FEATURE_TRANSLATION = "stream-translation";

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

    /** Mirrors {@code FrenchCorrectionServiceImpl#SYSTEM_PROMPT} so streaming uses the same instructions. */
    private static final String FR_CORRECTION_PROMPT = """
            Tu es un correcteur de français. On te donne la transcription brute d'une phrase \
            dite à l'oral, parfois par une personne dont le français n'est pas la langue maternelle. \
            Réécris-la en français correct et naturel, en respectant fidèlement le sens et l'intention. \
            Corrige la grammaire, les accords, la conjugaison, les accents et les mots mal transcrits. \
            Applique l'orthographe et la ponctuation correctes : mets une majuscule au début de la phrase \
            et aux noms propres, et termine par la ponctuation appropriée (point, point d'interrogation \
            pour une question, point d'exclamation), avec les espaces typographiques françaises. \
            Exemple : « on va manger quoi » devient « On va manger quoi ? ». \
            N'ajoute aucune information, ne réponds pas à la phrase, ne la traduis pas. \
            Renvoie UNIQUEMENT la phrase corrigée, sans guillemets ni commentaire.""";

    /** Single source of the streamed Kinyarwanda correction instructions (the only LLM RW correction in the app). */
    private static final String RW_CORRECTION_PROMPT = """
            Tu reçois la transcription brute d'une phrase dite en KINYARWANDA, produite par un modèle de \
            reconnaissance vocale. Elle est en minuscules, sans ponctuation, et peut contenir de petites erreurs \
            d'accord ou d'orthographe. Réécris-la en kinyarwanda correct :
            - ajoute la ponctuation (points, virgules, point d'interrogation pour une question) ;
            - mets une majuscule au début de la phrase et aux noms propres ;
            - le mot « Imana » (Dieu) s'écrit TOUJOURS avec une majuscule, où qu'il apparaisse ;
            - corrige uniquement les accords et l'orthographe ÉVIDENTS.
            Préserve FIDÈLEMENT les mots et le sens : ne change pas le vocabulaire, n'ajoute ni ne supprime \
            d'information, ne réponds pas à la phrase, ne la traduis pas. \
            Renvoie UNIQUEMENT la phrase corrigée, sans guillemets ni commentaire.""";

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final OpenAiChatModel openAiModel;
    private final AnthropicChatModel anthropicModel;
    private final LlmSettings llmSettings;
    private final TranslationPromptBuilder promptBuilder;
    private final GoldenDictionaryService goldenDictionaryService;
    private final KinyarwandaPostProcessor postProcessor;
    private final LlmUsageTracker usageTracker;

    StreamTranslationServiceImpl(@Qualifier("openAiChatModel") OpenAiChatModel openAiModel,
            @Qualifier("anthropicChatModel") AnthropicChatModel anthropicModel, LlmSettings llmSettings,
            TranslationPromptBuilder promptBuilder, GoldenDictionaryService goldenDictionaryService,
            KinyarwandaPostProcessor postProcessor, LlmUsageTracker usageTracker) {
        this.openAiModel = openAiModel;
        this.anthropicModel = anthropicModel;
        this.llmSettings = llmSettings;
        this.promptBuilder = promptBuilder;
        this.goldenDictionaryService = goldenDictionaryService;
        this.postProcessor = postProcessor;
        this.usageTracker = usageTracker;
    }

    @Override
    public void stream(SseEmitter emitter, StreamTranslationRequest request) {
        executor.submit(() -> runPipeline(emitter, request));
    }

    /** Runs the full pipeline on a virtual thread; completes the emitter (or completes-with-error) before returning. */
    private void runPipeline(SseEmitter emitter, StreamTranslationRequest request) {
        try {
            Locale srcLocale = Locale.forLanguageTag(request.sourceLang());
            Locale tgtLocale = Locale.forLanguageTag(request.targetLang());

            String correctedSource = streamCorrection(emitter, request.text(), request.sourceLang());
            send(emitter, EVENT_CORRECTION_DONE, new DoneText(correctedSource));

            String finalTranslation = streamTranslation(emitter, correctedSource, srcLocale, tgtLocale,
                    request.targetLang());
            String audioUrl = buildAudioUrl(request.targetLang(), finalTranslation);
            send(emitter, EVENT_TRANSLATION_DONE, new TranslationDone(finalTranslation, audioUrl));

            send(emitter, EVENT_DONE, new Empty());
            emitter.complete();
        } catch (Exception ex) {
            log.warn("Stream translation pipeline failed: {}", ex.getMessage(), ex);
            tryEmitError(emitter, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Correction (source cleanup) — streamed token by token
    // -------------------------------------------------------------------------

    /**
     * Streams the cleaned SOURCE text token by token via {@code correction} events and returns the full cleaned text.
     * Kinyarwanda output is additionally run through the deterministic {@link #tidy(String)} pass before being returned
     * (the streamed tokens are the raw model output; the {@code correction-done} text is the tidied final).
     */
    private String streamCorrection(SseEmitter emitter, String text, String lang) {
        boolean rw = LANG_RW.equalsIgnoreCase(lang);
        String systemPrompt = rw ? RW_CORRECTION_PROMPT : FR_CORRECTION_PROMPT;
        StringBuilder accumulated = new StringBuilder();
        try {
            String activeModel = llmSettings.getModel();
            ChatClient client = buildClient(systemPrompt, activeModel);
            consumeStream(client, activeModel, text, FEATURE_CORRECTION, chunk -> {
                accumulated.append(chunk);
                send(emitter, EVENT_CORRECTION, new Token(chunk));
            });
        } catch (Exception ex) {
            // Correction is best-effort: fall back to the raw source so the pipeline can still translate.
            log.warn("Stream correction failed ({}): {}", lang, ex.getMessage());
            if (accumulated.length() == 0) {
                accumulated.append(text);
            }
        }
        String cleaned = accumulated.toString().trim();
        if (!StringUtils.hasText(cleaned)) {
            cleaned = text;
        }
        return rw ? tidy(cleaned) : cleaned;
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
    // Translation — golden short-circuit, else streamed token by token
    // -------------------------------------------------------------------------

    /**
     * Translates {@code correctedSource}. On an exact golden-dictionary hit, returns the golden text immediately with
     * no {@code translation} token events. Otherwise streams the LLM translation token by token, then applies the
     * Kinyarwanda post-processor (when the target is RW) to the accumulated text and returns the post-processed result.
     */
    private String streamTranslation(SseEmitter emitter, String correctedSource, Locale srcLocale, Locale tgtLocale,
            String targetLang) {
        Optional<TranslationResponse> golden = goldenDictionaryService.lookup(correctedSource, srcLocale, tgtLocale);
        if (golden.isPresent()) {
            log.debug("Golden short-circuit for stream translation");
            return golden.get().translatedText();
        }

        StringBuilder accumulated = new StringBuilder();
        String userPrompt = promptBuilder.buildUserPrompt(correctedSource, srcLocale, tgtLocale);
        String activeModel = llmSettings.getModel();
        ChatClient client = buildClient(promptBuilder.buildSystemPrompt(), activeModel);
        consumeStream(client, activeModel, userPrompt, FEATURE_TRANSLATION, chunk -> {
            accumulated.append(chunk);
            send(emitter, EVENT_TRANSLATION, new Token(chunk));
        });

        String raw = accumulated.toString().trim();
        if (LANG_RW.equalsIgnoreCase(targetLang)) {
            return postProcessor.process(raw, correctedSource, srcLocale, tgtLocale).text();
        }
        return raw;
    }

    // -------------------------------------------------------------------------
    // Spring AI streaming plumbing
    // -------------------------------------------------------------------------

    /**
     * Builds a streaming {@link ChatClient} for the given system prompt, routed to the provider that owns
     * {@code modelId}. OpenAI keeps the server-side default temperature (GPT-5.x rejects a custom value); Anthropic
     * gets a low temperature and an explicit {@code maxTokens}.
     */
    private ChatClient buildClient(String systemPrompt, String modelId) {
        String provider = resolveProvider(modelId);
        if (PROVIDER_ANTHROPIC.equals(provider)) {
            return ChatClient.builder(anthropicModel).defaultSystem(systemPrompt).build();
        }
        return ChatClient.builder(openAiModel).defaultSystem(systemPrompt).build();
    }

    /**
     * Drives a Spring AI streaming call synchronously: builds per-call options, drains the reactive
     * {@code Flux<String>} via {@code toIterable()} on the calling (virtual) thread, and hands every non-empty chunk to
     * {@code onChunk}.
     */
    private void consumeStream(ChatClient client, String modelId, String userPrompt, String feature,
            Consumer<String> onChunk) {
        String provider = resolveProvider(modelId);
        ChatOptions.Builder<?> options = buildOptions(provider, modelId);
        for (String chunk : client.prompt().user(userPrompt).options(options).stream().content().toIterable()) {
            if (chunk != null && !chunk.isEmpty()) {
                onChunk.accept(chunk);
            }
        }
        // Token usage is not exposed on the streamed Flux<String>; record a best-effort zero-token event so the
        // feature still appears in admin analytics (tracker never throws).
        usageTracker.track(provider, modelId, feature, 0, 0);
    }

    /** OpenAI omits temperature (GPT-5.x rejects it); Anthropic gets a low temperature + explicit maxTokens. */
    private ChatOptions.Builder<?> buildOptions(String provider, String modelId) {
        if (PROVIDER_ANTHROPIC.equals(provider)) {
            return AnthropicChatOptions.builder().model(modelId).temperature(ANTHROPIC_TEMPERATURE)
                    .maxTokens(MAX_TOKENS);
        }
        return ChatOptions.builder().model(modelId);
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
