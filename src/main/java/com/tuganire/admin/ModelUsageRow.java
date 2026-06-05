package com.tuganire.admin;

import java.math.BigDecimal;

/**
 * Per-model usage breakdown row for the admin dashboard.
 *
 * @param provider
 *            the LLM provider name
 * @param model
 *            the model id
 * @param calls
 *            number of recorded calls
 * @param promptTokens
 *            total prompt tokens
 * @param completionTokens
 *            total completion tokens
 * @param cost
 *            total estimated cost in USD
 */
public record ModelUsageRow(String provider, String model, long calls, long promptTokens, long completionTokens,
        BigDecimal cost) {

    /**
     * Total tokens (prompt + completion) for this model.
     *
     * @return the combined token count
     */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
