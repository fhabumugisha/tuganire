package com.tuganire.admin.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelCostBreakdown {

    private String model;
    private BigDecimal cost;
    private long callCount;
}
