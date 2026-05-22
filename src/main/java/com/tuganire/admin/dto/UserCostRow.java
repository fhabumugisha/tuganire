package com.tuganire.admin.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCostRow {

    private Long userId;
    private String email;
    private BigDecimal cost;
}
