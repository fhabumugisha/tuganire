package com.tuganire.admin;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-model LLM pricing, bound from {@code tuganire.ai-models.*} in {@code application.yml}.
 *
 * <p>
 * Prices are expressed in USD per 1,000 tokens. {@link AIModelCostCalculator} reads these values to estimate the cost
 * of each recorded {@link LlmUsageEvent}. Models not present in the map default to a zero cost (cost is best-effort
 * analytics, never a hard dependency).
 *
 * @param models
 *            map of model id (e.g. {@code "gpt-4o"}) to its {@link ModelCost}
 */
@ConfigurationProperties(prefix = "tuganire.ai-models")
public record AiModelCostProperties(Map<String, ModelCost> models) {

    public AiModelCostProperties {
        models = models == null ? Map.of() : models;
    }

    /**
     * Pricing for a single model, in USD per 1,000 tokens.
     *
     * @param inputPer1k
     *            cost per 1,000 prompt (input) tokens
     * @param outputPer1k
     *            cost per 1,000 completion (output) tokens
     */
    public record ModelCost(BigDecimal inputPer1k, BigDecimal outputPer1k) {

        public ModelCost {
            inputPer1k = inputPer1k == null ? BigDecimal.ZERO : inputPer1k;
            outputPer1k = outputPer1k == null ? BigDecimal.ZERO : outputPer1k;
        }
    }
}
