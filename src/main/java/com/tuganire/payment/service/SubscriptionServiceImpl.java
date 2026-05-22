package com.tuganire.payment.service;

import com.tuganire.auth.model.User;
import com.tuganire.payment.constant.SubscriptionPlan;
import com.tuganire.payment.constant.SubscriptionStatus;
import com.tuganire.payment.dto.SubscriptionResponse;
import com.tuganire.payment.model.Subscription;
import com.tuganire.payment.repository.SubscriptionRepository;
import com.tuganire.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StripeService stripeService;

    @Override
    public String createSubscription(User user, SubscriptionPlan plan) {
        Optional<Subscription> existingSubscription = subscriptionRepository.findByUserId(user.getId());

        if (existingSubscription.isPresent() && existingSubscription.get().getStatus() == SubscriptionStatus.ACTIVE) {
            throw new BusinessException("payment.error.already.subscribed");
        }

        return stripeService.createCheckoutSession(user, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getUserSubscription(Long userId) {
        return subscriptionRepository.findByUserId(userId).map(this::toResponse);
    }

    @Override
    @Transactional
    public void cancelUserSubscription(Long userId, boolean immediately) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("payment.error.no.subscription"));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessException("payment.error.subscription.not.active");
        }

        stripeService.cancelSubscription(userId, immediately);
        log.info("Canceled subscription for user {} (immediately: {})", userId, immediately);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSubscriptionActive(Long userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE
                        && subscription.getCurrentPeriodEnd().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return SubscriptionResponse.builder().id(subscription.getId()).plan(subscription.getPlan())
                .status(subscription.getStatus()).currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd()).canceledAt(subscription.getCanceledAt())
                .createdAt(subscription.getCreatedAt()).build();
    }
}
