package com.tuganire.admin.service;

import com.tuganire.admin.model.LlmUsageEvent;
import com.tuganire.admin.repository.LlmUsageEventRepository;
import com.tuganire.auth.model.User;
import com.tuganire.common.service.AIModelCostCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Provider-agnostic LLM usage tracker. Call {@link #track} after each LLM API call to record token counts and estimated
 * cost.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmUsageTracker {

    private final LlmUsageEventRepository repository;
    private final AIModelCostCalculator costCalculator;

    @Async
    public void track(User user, String provider, String model, String feature, Integer promptTokens,
            Integer completionTokens) {
        try {
            int safePrompt = promptTokens != null ? promptTokens : 0;
            int safeCompletion = completionTokens != null ? completionTokens : 0;
            double rawCost = costCalculator.calculateCost(provider, model, safePrompt, safeCompletion);
            BigDecimal cost = BigDecimal.valueOf(rawCost);

            LlmUsageEvent event = LlmUsageEvent.builder().user(user).provider(provider).model(model).feature(feature)
                    .promptTokens(safePrompt).completionTokens(safeCompletion).totalTokens(safePrompt + safeCompletion)
                    .costUsd(cost).createdAt(LocalDateTime.now()).build();

            repository.save(event);
            log.debug("Tracked LLM usage: provider={} model={} feature={} tokens={}+{}={} cost=${}", provider, model,
                    feature, safePrompt, safeCompletion, safePrompt + safeCompletion, cost);
        } catch (Exception e) {
            log.error("Failed to track LLM usage for provider={} model={}: {}", provider, model, e.getMessage(), e);
        }
    }
}
