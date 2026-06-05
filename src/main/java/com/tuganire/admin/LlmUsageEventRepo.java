package com.tuganire.admin;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persists and aggregates {@link LlmUsageEvent} rows for the admin usage dashboard. */
public interface LlmUsageEventRepo extends JpaRepository<LlmUsageEvent, Long> {

    @Query("select coalesce(sum(e.promptTokens), 0) from LlmUsageEvent e")
    long sumPromptTokens();

    @Query("select coalesce(sum(e.completionTokens), 0) from LlmUsageEvent e")
    long sumCompletionTokens();

    @Query("select coalesce(sum(e.estimatedCost), 0) from LlmUsageEvent e")
    BigDecimal sumEstimatedCost();

    /**
     * Aggregates usage grouped by provider and model, most expensive first.
     *
     * @return one row per (provider, model) with call count, token totals and cost total
     */
    @Query("""
            select e.provider as provider, e.model as model,
                   count(e) as calls,
                   coalesce(sum(e.promptTokens), 0) as promptTokens,
                   coalesce(sum(e.completionTokens), 0) as completionTokens,
                   coalesce(sum(e.estimatedCost), 0) as cost
            from LlmUsageEvent e
            group by e.provider, e.model
            order by coalesce(sum(e.estimatedCost), 0) desc
            """)
    List<ModelUsageAggregate> aggregateByModel();

    /** Projection for {@link #aggregateByModel()}. */
    interface ModelUsageAggregate {

        String getProvider();

        String getModel();

        long getCalls();

        long getPromptTokens();

        long getCompletionTokens();

        BigDecimal getCost();
    }
}
