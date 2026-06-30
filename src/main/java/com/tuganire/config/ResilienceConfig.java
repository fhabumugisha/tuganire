package com.tuganire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Enables Spring Framework's declarative resilience annotations ({@code @Retryable}, {@code @ConcurrencyLimit}).
 *
 * <p>
 * Used by the MMS providers to ride out the scale-to-zero MMS server's cold start: gateway 5xx are retried with
 * backoff, and {@code @ConcurrencyLimit} caps how many inference calls hit the single MMS instance at once so a wake
 * spike cannot amplify load against it. The request threads are virtual ({@code spring.threads.virtual.enabled}), so a
 * call parked waiting on a permit or a slow wake is cheap and does not pin a platform thread.
 */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}
