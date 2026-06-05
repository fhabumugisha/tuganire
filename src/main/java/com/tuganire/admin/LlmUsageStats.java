package com.tuganire.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated LLM-usage analytics for the admin dashboard.
 *
 * @param totalCalls
 *            total number of recorded LLM calls
 * @param totalPromptTokens
 *            total prompt tokens across all calls
 * @param totalCompletionTokens
 *            total completion tokens across all calls
 * @param totalCost
 *            total estimated cost in USD
 * @param byModel
 *            per-model breakdown, most expensive first
 */
public record LlmUsageStats(long totalCalls, long totalPromptTokens, long totalCompletionTokens, BigDecimal totalCost,
        List<ModelUsageRow> byModel) {

    /**
     * Total tokens (prompt + completion) across all calls.
     *
     * @return the combined token count
     */
    public long totalTokens() {
        return totalPromptTokens + totalCompletionTokens;
    }
}
