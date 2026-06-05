package com.tuganire.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the API rate limiter. Bound from {@code tuganire.rate-limit.*} in application.yml.
 *
 * <p>
 * Both per-minute and per-day ceilings are enforced independently; the per-minute check runs first.
 */
@Validated
@ConfigurationProperties(prefix = "tuganire.rate-limit")
public record RateLimitProperties(@Min(1) int requestsPerMinute, @Min(1) int requestsPerDay) {
}
