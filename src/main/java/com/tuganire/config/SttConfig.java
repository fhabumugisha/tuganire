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
 */
@ConfigurationProperties(prefix = "tuganire.stt")
@Validated
public record SttConfig(@NotBlank String defaultProvider) {
}
