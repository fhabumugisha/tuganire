package com.tuganire.stt;

import com.tuganire.admin.LlmUsageTracker;
import com.tuganire.llm.LlmSettings;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * LLM-backed implementation of {@link KinyarwandaCorrectionService}.
 *
 * <p>
 * Routes to OpenAI or Anthropic based on the provider that owns {@code modelId} (resolved via {@link LlmSettings}), so
 * a single call can be made with {@code gpt-5.5} or {@code claude-sonnet-4-6}. Mirrors the
 * {@code FrenchCorrectionService} pattern (Spring AI types confined to this impl). Temperature is omitted for OpenAI
 * (some GPT-5.x models reject a custom value) and kept low for Anthropic — correction must be faithful, not creative.
 */
@Service
@Slf4j
class KinyarwandaCorrectionServiceImpl implements KinyarwandaCorrectionService {

    static final String FEATURE = "kinyarwanda-correction";
    private static final double ANTHROPIC_TEMPERATURE = 0.1;
    private static final int MAX_TOKENS = 1024;
    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_OPENAI = "openai";

    /** Word "imana" as a standalone token, any case — enforced to "Imana" deterministically. */
    private static final Pattern IMANA = Pattern.compile("\\bimana\\b", Pattern.CASE_INSENSITIVE);

    private static final String SYSTEM_PROMPT = """
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

    private final ChatClient openAiClient;
    private final ChatClient anthropicClient;
    private final LlmSettings llmSettings;
    private final LlmUsageTracker usageTracker;

    KinyarwandaCorrectionServiceImpl(@Qualifier("openAiChatModel") OpenAiChatModel openAiModel,
            @Qualifier("anthropicChatModel") AnthropicChatModel anthropicModel, LlmSettings llmSettings,
            LlmUsageTracker usageTracker) {
        this.llmSettings = llmSettings;
        this.usageTracker = usageTracker;
        this.openAiClient = ChatClient.builder(openAiModel).defaultSystem(SYSTEM_PROMPT).build();
        this.anthropicClient = ChatClient.builder(anthropicModel).defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public String correct(String rawTranscript, String modelId) {
        if (!StringUtils.hasText(rawTranscript)) {
            return rawTranscript;
        }
        try {
            String provider = resolveProvider(modelId);
            ChatClient client = PROVIDER_ANTHROPIC.equals(provider) ? anthropicClient : openAiClient;
            var options = ChatOptions.builder().model(modelId).maxTokens(MAX_TOKENS);
            // OpenAI GPT-5.x rejects a custom temperature; only set it for Anthropic.
            if (PROVIDER_ANTHROPIC.equals(provider)) {
                options.temperature(ANTHROPIC_TEMPERATURE);
            }
            ChatResponse response = client.prompt().user(rawTranscript).options(options).call().chatResponse();
            recordUsage(provider, modelId, response);
            String cleaned = response != null && response.getResult() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            String result = StringUtils.hasText(cleaned) ? tidy(cleaned) : tidy(rawTranscript);
            log.debug("RW correction [{}]: '{}' -> '{}'", modelId, rawTranscript, result);
            return result;
        } catch (Exception ex) {
            log.warn("RW correction failed [{}]; tidying raw transcript: {}", modelId, ex.getMessage());
            return tidy(rawTranscript);
        }
    }

    /** Resolves the owning provider for a model id from the configured selectable models; defaults to OpenAI. */
    private String resolveProvider(String modelId) {
        return llmSettings.getAvailableModels().stream().filter(m -> m.id().equals(modelId)).map(m -> m.provider())
                .findFirst().orElse(PROVIDER_OPENAI);
    }

    /**
     * Deterministic safety net the LLM might miss: capitalise {@code Imana}, capitalise the first letter, and ensure
     * terminal punctuation.
     */
    static String tidy(String text) {
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

    /** Records token usage from the chat response; best-effort (tracker never throws). */
    private void recordUsage(String provider, String modelId, @Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        usageTracker.track(provider, modelId, FEATURE, promptTokens, completionTokens);
    }
}
