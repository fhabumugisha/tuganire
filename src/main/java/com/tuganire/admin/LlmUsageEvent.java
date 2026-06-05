package com.tuganire.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A single recorded LLM call (provider, model, feature, token usage, estimated cost).
 *
 * <p>
 * Written by {@link LlmUsageTracker} on the translation path and aggregated by the {@code /admin} dashboard. The cost
 * is computed at write time so reporting does not depend on later pricing changes.
 */
@Entity
@Table(name = "llm_usage_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class LlmUsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 50)
    private String feature;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "estimated_cost", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCost = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public LlmUsageEvent(String provider, String model, String feature, int promptTokens, int completionTokens,
            BigDecimal estimatedCost) {
        this.provider = provider;
        this.model = model;
        this.feature = feature;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.estimatedCost = estimatedCost == null ? BigDecimal.ZERO : estimatedCost;
    }
}
