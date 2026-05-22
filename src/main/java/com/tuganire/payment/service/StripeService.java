package com.tuganire.payment.service;

import com.tuganire.auth.model.User;
import com.tuganire.payment.constant.SubscriptionPlan;

public interface StripeService {

    String createCheckoutSession(User user, SubscriptionPlan plan);

    void handleCheckoutComplete(String sessionId);

    void handleSubscriptionUpdated(String subscriptionId);

    void handleSubscriptionDeleted(String subscriptionId);

    void cancelSubscription(Long userId, boolean immediately);

    String getPortalSession(String customerId);
}
