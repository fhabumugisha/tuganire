package com.tuganire.common.service;

import com.tuganire.common.config.ModelCostProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AIModelCostCalculator {

    private final ModelCostProperties costProperties;

    public AIModelCostCalculator(@Autowired(required = false) ModelCostProperties costProperties) {
        this.costProperties = costProperties;
        if (costProperties == null) {
            log.warn("ModelCostProperties not configured - cost calculation will return 0");
        }
    }

    /**
     * Calculates the estimated cost in USD for LLM API calls based on token usage. Uses configuration-based
     * per-million-token pricing.
     *
     * @param provider
     *            the AI provider (OpenAI or Anthropic)
     * @param model
     *            the specific model name
     * @param promptTokens
     *            number of tokens in the prompt
     * @param completionTokens
     *            number of tokens in the completion
     * @return estimated cost in USD, or 0.0 if tokens are null or config missing
     */
    public Double calculateCost(String provider, String model, Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null || completionTokens == null) {
            return 0.0;
        }

        if (costProperties == null || costProperties.getModels() == null) {
            log.warn("Cost properties not available for model: {}", model);
            return 0.0;
        }

        // Find model cost configuration
        ModelCostProperties.ModelCost modelCost = findModelCost(model);

        if (modelCost == null || modelCost.getInputRatePerMillionTokens() == null
                || modelCost.getOutputRatePerMillionTokens() == null) {
            log.warn("No cost configuration found for model: {}", model);
            return 0.0;
        }

        // Calculate cost using per-million-token rates
        double inputCost = (promptTokens / 1_000_000.0) * modelCost.getInputRatePerMillionTokens();
        double outputCost = (completionTokens / 1_000_000.0) * modelCost.getOutputRatePerMillionTokens();

        return inputCost + outputCost;
    }

    /**
     * Finds the cost configuration for a given model name. Handles model name variations (e.g., "gpt-4o-mini" matches
     * "openai-gpt4o-mini" config key).
     */
    private ModelCostProperties.ModelCost findModelCost(String modelName) {
        if (costProperties.getModels() == null) {
            return null;
        }

        // Try exact match first
        for (ModelCostProperties.ModelCost cost : costProperties.getModels().values()) {
            if (modelName.equals(cost.getName())) {
                return cost;
            }
        }

        // Try partial match (e.g., "gpt-4o-mini" contains "gpt4o-mini")
        for (ModelCostProperties.ModelCost cost : costProperties.getModels().values()) {
            if (cost.getName() != null && modelName.contains(cost.getName())) {
                return cost;
            }
        }

        // Try key-based match (e.g., model "claude-sonnet-4-5" matches key "anthropic-sonnet")
        for (String key : costProperties.getModels().keySet()) {
            ModelCostProperties.ModelCost cost = costProperties.getModels().get(key);
            if (cost.getName() != null && cost.getName().contains(modelName)) {
                return cost;
            }
        }

        return null;
    }
}
