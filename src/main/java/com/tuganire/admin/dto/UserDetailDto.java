package com.tuganire.admin.dto;

import com.tuganire.admin.model.LlmUsageEvent;
import com.tuganire.payment.model.Subscription;
import com.tuganire.shared.constant.PlanType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDto {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private PlanType plan;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private boolean admin;
    private Subscription subscription;
    private BigDecimal totalLlmCost;
    private List<LlmUsageEvent> recentLlmEvents;
}
