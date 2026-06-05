package com.tuganire.llm;

import com.tuganire.admin.LlmUsageTracker;
import com.tuganire.config.LlmConfig;
import com.tuganire.llm.prompt.TranslationPromptBuilder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * LLM provider backed by OpenAI chat models.
 *
 * <p>
 * Builds a {@link ChatClient} with the translation system prompt at construction time, then sets the model — and, when
 * the model supports it, the configured temperature — on every call (ADR-008: Spring AI types confined to impls). The
 * orchestrator decides which model id is used per call (see {@link LlmProvider#translate}); the temperature is read
 * from {@link LlmSettings} on every call so a settings change takes effect immediately.
 *
 * <p>
 * Some OpenAI models (the GPT-5.x reasoning family) reject a custom temperature value, so the provider omits it for any
 * model whose id appears in {@code tuganire.llm.openai.temperature-locked-models}.
 */
@Component
@Slf4j
public class OpenAiLlmProvider extends AbstractChatClientLlmProvider {

    static final String PROVIDER_NAME = "openai";

    private final LlmSettings llmSettings;
    private final List<String> temperatureLockedModels;

    public OpenAiLlmProvider(@Qualifier("openAiChatModel") OpenAiChatModel model,
            TranslationPromptBuilder promptBuilder, LlmUsageTracker usageTracker, LlmSettings llmSettings,
            LlmConfig llmConfig) {
        super(ChatClient.builder(model), promptBuilder, usageTracker);
        this.llmSettings = llmSettings;
        this.temperatureLockedModels = List.copyOf(llmConfig.openai().temperatureLockedModels());
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    /**
     * Builds per-call chat options. The translation temperature is only applied for models that accept a custom value:
     * GPT-5.x reasoning models reject anything but the default ({@code temperature=1}, HTTP 400), so we omit it for the
     * model ids listed in {@code tuganire.llm.openai.temperature-locked-models} and let the API use its default.
     *
     * @param modelId
     *            the model id being called
     * @return an options builder carrying the model and, when supported, the configured temperature
     */
    @Override
    protected ChatOptions.Builder<?> buildOptions(String modelId) {
        ChatOptions.Builder<?> options = ChatOptions.builder();
        options.model(modelId);
        if (supportsCustomTemperature(modelId)) {
            options.temperature(llmSettings.getTemperature());
        }
        return options;
    }

    /** Membership test against the configured locked-model list (no string-prefix heuristic). */
    private boolean supportsCustomTemperature(String modelId) {
        return !temperatureLockedModels.contains(modelId);
    }
}
