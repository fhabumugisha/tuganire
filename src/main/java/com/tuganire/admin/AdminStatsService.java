package com.tuganire.admin;

/** Aggregates back-office analytics (feedback metrics + LLM usage) for the {@code /admin} dashboard. */
public interface AdminStatsService {

    /**
     * Returns feedback counts plus the most recent feedback rows.
     *
     * @return the aggregated {@link FeedbackStats}
     */
    FeedbackStats feedbackStats();

    /**
     * Returns aggregated LLM-usage analytics (totals + per-model breakdown).
     *
     * @return the aggregated {@link LlmUsageStats}
     */
    LlmUsageStats llmUsageStats();
}
