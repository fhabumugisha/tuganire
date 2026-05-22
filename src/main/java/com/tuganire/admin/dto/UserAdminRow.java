package com.tuganire.admin.dto;

import com.tuganire.shared.constant.PlanType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminRow {

    private Long id;
    private String email;
    private String fullName;
    private PlanType plan;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private BigDecimal totalLlmCost;
    private boolean admin;
}
