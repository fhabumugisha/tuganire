package com.tuganire.payment.dto;

import com.tuganire.payment.constant.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotNull(message = "{payment.validation.plan.notnull}")
    private SubscriptionPlan plan;
}
