package com.tuganire.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the LLM subsystem.
 *
 * <p>
 * Binds from {@code tuganire.llm.*} in {@code application.yml}. When the {@code defaultProvider} fails or is
 * unavailable, the pipeline falls back to {@code fallbackProvider}.
 *
 * @param defaultProvider
 *            bean name / identifier of the primary LLM provider
 * @param fallbackProvider
 *            bean name / identifier of the fallback LLM provider
 * @param defaultTranslationModel
 *            id of the model used for FR→RW translation until changed in settings
 * @param translationModels
 *            cross-provider models a user may pick from in {@code /settings}
 * @param openai
 *            provider-specific knobs for the OpenAI integration
 */
@ConfigurationProperties(prefix = "tuganire.llm")
@Validated
public record LlmConfig(@NotBlank String defaultProvider, @NotBlank String fallbackProvider,
        @NotBlank String defaultTranslationModel, @NotEmpty List<ModelOption> translationModels,
        @NotNull OpenAiConfig openai) {

    /**
     * A selectable translation model and the provider that runs it.
     *
     * @param id
     *            the model id passed to the provider's SDK (e.g. {@code "gpt-5.5"}, {@code "claude-sonnet-4-6"})
     * @param provider
     *            name of the {@code LlmProvider} that owns this model (e.g. {@code "openai"}, {@code "anthropic"})
     * @param label
     *            human-friendly label shown in the settings dropdown
     */
    public record ModelOption(@NotBlank String id, @NotBlank String provider, @NotBlank String label) {
    }

    /**
     * OpenAI-specific configuration.
     *
     * <p>
     * Some OpenAI models reject a custom temperature value (HTTP 400) and only accept their default. These are listed
     * here by exact id rather than matched by name prefix so the policy is data-driven and auditable in
     * {@code application.yml}.
     *
     * @param temperatureLockedModels
     *            exact model ids that reject a custom {@code temperature} option (e.g. the GPT-5.x reasoning models).
     *            The provider omits {@code temperature} for these and lets the API use its server-side default.
     */
    public record OpenAiConfig(@NotNull List<String> temperatureLockedModels) {
    }
}
