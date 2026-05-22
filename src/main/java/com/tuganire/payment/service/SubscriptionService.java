package com.tuganire.payment.service;

import com.tuganire.auth.model.User;
import com.tuganire.payment.constant.SubscriptionPlan;
import com.tuganire.payment.dto.SubscriptionResponse;
import java.util.Optional;

public interface SubscriptionService {

    String createSubscription(User user, SubscriptionPlan plan);

    Optional<SubscriptionResponse> getUserSubscription(Long userId);

    void cancelUserSubscription(Long userId, boolean immediately);

    boolean isSubscriptionActive(Long userId);
}
