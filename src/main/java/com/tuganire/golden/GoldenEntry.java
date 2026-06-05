package com.tuganire.golden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "golden_dictionary")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class GoldenEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceText;

    @Column(nullable = false, length = 2)
    private String sourceLang;

    @Column(nullable = false)
    private String targetText;

    @Column(nullable = false, length = 2)
    private String targetLang;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] alternatives;

    @Column(length = 100)
    private String context;

    @Column(length = 50)
    private String errorCategory;

    @Column(nullable = false, length = 100)
    private String validatedBy;

    // Business timestamp: when a native speaker validated this entry.
    // Distinct from JPA audit createdAt — set explicitly on import.
    @Column(nullable = false)
    private Instant validatedAt = Instant.now();

    @Column(nullable = false)
    private Integer usageCount = 0;

    @Column(precision = 3, scale = 1)
    private BigDecimal scoreAvg;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant lastModifiedAt;
}
