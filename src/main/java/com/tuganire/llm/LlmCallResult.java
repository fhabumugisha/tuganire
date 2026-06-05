package com.tuganire.llm;

/**
 * Outcome of a single LLM translation call, carrying both the produced text and the provenance needed for metrics
 * tagging.
 *
 * <p>
 * Returned by the orchestrator helper that wraps primary + fallback so the caller can record the LLM-stage Timer and
 * the translations counter with the actual {@code provider} / {@code model} that served the request (which may be the
 * fallback's, not the active selection).
 *
 * @param text
 *            the raw translation produced by the LLM (possibly empty, never {@code null})
 * @param provider
 *            name of the provider that actually served the call (e.g. {@code "openai"}, {@code "anthropic"})
 * @param model
 *            id of the model that was called
 * @param fallbackUsed
 *            {@code true} if the primary provider threw and we recovered via the configured fallback
 */
public record LlmCallResult(String text, String provider, String model, boolean fallbackUsed) {
}
