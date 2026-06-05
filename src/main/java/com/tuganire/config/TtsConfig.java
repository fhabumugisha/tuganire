package com.tuganire.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the TTS subsystem.
 *
 * <p>
 * Binds from {@code tuganire.tts.*} in {@code application.yml}. The {@code defaultProvider} selects which
 * {@link com.tuganire.tts.TtsProvider} is used when no language-specific override applies; {@code kinyProvider} and
 * {@code frenchProvider} route Kinyarwanda and French respectively.
 *
 * @param defaultProvider
 *            bean name / identifier of the fallback TTS provider
 * @param kinyProvider
 *            bean name / identifier of the Kinyarwanda TTS provider
 * @param frenchProvider
 *            bean name / identifier of the French TTS provider
 */
@ConfigurationProperties(prefix = "tuganire.tts")
@Validated
public record TtsConfig(@NotBlank String defaultProvider, @NotBlank String kinyProvider,
        @NotBlank String frenchProvider) {
}
