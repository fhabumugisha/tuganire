package com.tuganire.stt;

import com.tuganire.admin.LlmUsageTracker;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OpenAI-backed implementation of {@link FrenchCorrectionService}.
 *
 * <p>
 * Mirrors the {@code OpenAiLlmProvider} pattern (ADR-008: Spring AI types confined to impls): builds a
 * {@link ChatClient} with a correction system prompt at construction, and overrides model + temperature per call.
 * Temperature is low (0.1) because correction should be faithful, not creative. Token usage is recorded via
 * {@link LlmUsageTracker} so admin analytics include this feature.
 */
@Service
@Slf4j
class FrenchCorrectionServiceImpl implements FrenchCorrectionService {

    static final String PROVIDER_NAME = "openai";
    static final String MODEL = "gpt-4o-mini";
    static final double TEMPERATURE = 0.1;
    static final String FEATURE_CORRECTION = "french-correction";

    /**
     * System prompt: rewrite the transcript into correct French, preserving meaning, returning only the corrected
     * sentence (no commentary, no quotes).
     */
    private static final String SYSTEM_PROMPT = """
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

    private final ChatClient chatClient;
    private final LlmUsageTracker usageTracker;

    FrenchCorrectionServiceImpl(@Qualifier("openAiChatModel") OpenAiChatModel model, LlmUsageTracker usageTracker) {
        this.usageTracker = usageTracker;
        this.chatClient = ChatClient.builder(model).defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(ChatOptions.builder().temperature(TEMPERATURE)).build();
    }

    @Override
    public String correct(String rawTranscript) {
        if (!StringUtils.hasText(rawTranscript)) {
            return rawTranscript;
        }
        try {
            ChatResponse response = chatClient.prompt().user(rawTranscript)
                    .options(ChatOptions.builder().model(MODEL).temperature(TEMPERATURE)).call().chatResponse();
            recordUsage(response);
            String corrected = response != null && response.getResult() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            if (!StringUtils.hasText(corrected)) {
                return rawTranscript;
            }
            String trimmed = corrected.trim();
            log.debug("French correction: '{}' -> '{}'", rawTranscript, trimmed);
            return trimmed;
        } catch (Exception ex) {
            log.warn("French correction failed; using raw transcript: {}", ex.getMessage());
            return rawTranscript;
        }
    }

    /** Records token usage from the chat response; best-effort (tracker never throws). */
    private void recordUsage(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        usageTracker.track(PROVIDER_NAME, MODEL, FEATURE_CORRECTION, promptTokens, completionTokens);
    }
}
