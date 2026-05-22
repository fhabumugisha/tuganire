package com.tuganire.common.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AI model cost calculation. Maps to tuganire.ai-models.* in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "tuganire.ai-models")
@Data
public class ModelCostProperties {

    private Map<String, ModelCost> models;

    @Data
    public static class ModelCost {

        private String name;
        private boolean enabled;
        private Double inputRatePerMillionTokens;
        private Double outputRatePerMillionTokens;
        private String currency;
    }
}
