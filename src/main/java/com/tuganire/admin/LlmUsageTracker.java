package com.tuganire.admin;

/**
 * Records LLM usage events for admin analytics.
 *
 * <p>
 * Tracking is <strong>best-effort</strong>: implementations must never propagate an exception, so a tracking failure
 * cannot break the originating call (e.g. a translation). The anonymous-session MVP has no user concept, so usage is
 * attributed only to provider / model / feature.
 */
public interface LlmUsageTracker {

    /**
     * Records a single LLM call. The estimated cost is computed from the configured per-model pricing.
     *
     * @param provider
     *            the LLM provider name (e.g. {@code "openai"})
     * @param model
     *            the model id (e.g. {@code "gpt-4o"})
     * @param feature
     *            the application feature that triggered the call (e.g. {@code "translation"})
     * @param promptTokens
     *            number of prompt (input) tokens
     * @param completionTokens
     *            number of completion (output) tokens
     */
    void track(String provider, String model, String feature, int promptTokens, int completionTokens);
}
