package com.tuganire.cache;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the translation cache. Bound from {@code tuganire.cache.*} in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "tuganire.cache")
public record CacheProperties(@Min(1) int translationTtlDays) {
}
