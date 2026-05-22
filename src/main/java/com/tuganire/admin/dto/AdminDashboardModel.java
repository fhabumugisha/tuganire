package com.tuganire.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardModel {

    private long totalUsers;
    private long premiumUsers;
    private long freeUsers;
    private long signupsToday;
    private BigDecimal llmCostMtd;
    private List<DailyCostPoint> dailyCostLast30Days;
    private List<ModelCostBreakdown> costByModelMtd;
    private List<UserCostRow> topUsersByCost;
    private String dailyCostJson;
    private String costByModelJson;
    private String topUsersJson;
}
