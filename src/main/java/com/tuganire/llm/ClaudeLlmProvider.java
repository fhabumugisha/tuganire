package com.tuganire.llm;

import com.tuganire.admin.LlmUsageTracker;
import com.tuganire.llm.prompt.TranslationPromptBuilder;
import com.tuganire.translation.StructuredTranslation;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * LLM provider backed by the Anthropic Claude family.
 *
 * <p>
 * Used for any user-selected Claude model — Sonnet, Haiku, etc. — and also as the fallback when an OpenAI call fails.
 * Uses the Spring AI 2.0 Anthropic SDK integration; the orchestrator passes the model id per call, and the temperature
 * is read from {@link LlmSettings} on every call (ADR-008: Spring AI types confined to impls).
 */
@Component
@Slf4j
public class ClaudeLlmProvider extends AbstractChatClientLlmProvider {

    static final String PROVIDER_NAME = "anthropic";

    private final LlmSettings llmSettings;

    public ClaudeLlmProvider(@Qualifier("anthropicChatModel") AnthropicChatModel model,
            TranslationPromptBuilder promptBuilder, LlmUsageTracker usageTracker, LlmSettings llmSettings) {
        super(ChatClient.builder(model), promptBuilder, usageTracker);
        this.llmSettings = llmSettings;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    protected ChatOptions.Builder<?> buildOptions(String modelId) {
        return AnthropicChatOptions.builder().model(modelId).temperature(llmSettings.getTemperature());
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>TODO:</b> the user prompt sent here is the same plain-text translation prompt used by {@code translate} — it
     * does <i>not</i> describe the {@link StructuredTranslation} schema to Claude. Spring AI's structured-output
     * converter parses whatever JSON-shaped response comes back, so fields may be {@code null} or partial. Re-validate
     * (and probably add a schema-aware system prompt) before any caller starts depending on this method.
     */
    @Override
    public StructuredTranslation translateStructured(String text, Locale src, Locale tgt, String modelId) {
        return super.translateStructured(text, src, tgt, modelId);
    }
}
