package com.tuganire.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the STT subsystem.
 *
 * <p>
 * Binds from {@code tuganire.stt.*} in {@code application.yml}.
 *
 * @param defaultProvider
 *            bean name / identifier of the default STT provider
 * @param kinyProvider
 *            provider used for Kinyarwanda ({@code "openai"} or {@code "mms"}), switchable via
 *            {@code KINY_STT_PROVIDER} without a code change
 * @param openaiModel
 *            OpenAI transcription model used by the {@code "openai"} provider (e.g. {@code "gpt-transcribe"})
 */
@ConfigurationProperties(prefix = "tuganire.stt")
@Validated
public record SttConfig(@NotBlank String defaultProvider, @NotBlank String kinyProvider, @NotBlank String openaiModel) {
}
