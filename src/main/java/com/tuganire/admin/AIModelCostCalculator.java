package com.tuganire.admin;

import com.tuganire.admin.AiModelCostProperties.ModelCost;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Computes the estimated USD cost of an LLM call from token counts and the per-model pricing configured under
 * {@code tuganire.ai-models}.
 *
 * <p>
 * Unknown models resolve to a zero cost — usage tracking is best-effort analytics and must never fail a translation.
 */
@Component
@RequiredArgsConstructor
public class AIModelCostCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final int COST_SCALE = 6;

    private final AiModelCostProperties properties;

    /**
     * Estimates the cost of a single call, in USD.
     *
     * @param model
     *            the model id (e.g. {@code "gpt-4o"})
     * @param promptTokens
     *            number of prompt (input) tokens; treated as 0 when negative
     * @param completionTokens
     *            number of completion (output) tokens; treated as 0 when negative
     * @return the estimated cost rounded to {@value #COST_SCALE} decimal places, never {@code null}
     */
    public BigDecimal estimateCost(String model, int promptTokens, int completionTokens) {
        ModelCost cost = properties.models().get(model);
        if (cost == null) {
            return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal prompt = BigDecimal.valueOf(Math.max(0, promptTokens));
        BigDecimal completion = BigDecimal.valueOf(Math.max(0, completionTokens));
        BigDecimal inputCost = prompt.multiply(cost.inputPer1k()).divide(THOUSAND, COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal outputCost = completion.multiply(cost.outputPer1k()).divide(THOUSAND, COST_SCALE,
                RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
