package com.tuganire.admin;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link LlmUsageTracker}: computes the call cost via {@link AIModelCostCalculator} and persists an
 * {@link LlmUsageEvent}.
 *
 * <p>
 * Runs in its own {@link Propagation#REQUIRES_NEW} transaction and swallows every exception, so a tracking failure can
 * never roll back or break the caller's translation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class LlmUsageTrackerImpl implements LlmUsageTracker {

    private final LlmUsageEventRepo repo;
    private final AIModelCostCalculator costCalculator;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void track(String provider, String model, String feature, int promptTokens, int completionTokens) {
        try {
            BigDecimal cost = costCalculator.estimateCost(model, promptTokens, completionTokens);
            LlmUsageEvent event = LlmUsageEvent.builder().provider(provider).model(model).feature(feature)
                    .promptTokens(promptTokens).completionTokens(completionTokens).estimatedCost(cost).build();
            repo.save(event);
            log.debug("Recorded LLM usage: provider={} model={} feature={} tokens={}+{} cost={}", provider, model,
                    feature, promptTokens, completionTokens, cost);
        } catch (Exception ex) {
            log.warn("Failed to record LLM usage (provider={}, model={}): {}", provider, model, ex.getMessage());
        }
    }
}
