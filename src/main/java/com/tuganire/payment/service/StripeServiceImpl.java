package com.tuganire.payment.service;

import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.payment.constant.SubscriptionPlan;
import com.tuganire.payment.constant.SubscriptionStatus;
import com.tuganire.payment.exception.StripeException;
import com.tuganire.payment.model.Subscription;
import com.tuganire.payment.repository.SubscriptionRepository;
import com.tuganire.shared.constant.PlanType;
import com.tuganire.shared.service.EmailService;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class StripeServiceImpl implements StripeService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${stripe.price-id-monthly}")
    private String priceIdMonthly;

    @Value("${stripe.price-id-yearly}")
    private String priceIdYearly;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${tuganire.base-url}")
    private String baseUrl;

    @Override
    public String createCheckoutSession(User user, SubscriptionPlan plan) {
        try {
            String customerId = getOrCreateCustomer(user);

            String priceId = plan == SubscriptionPlan.MONTHLY ? priceIdMonthly : priceIdYearly;

            SessionCreateParams params = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                    .setSuccessUrl(successUrl).setCancelUrl(cancelUrl).setAllowPromotionCodes(true)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.AUTO)
                    .putMetadata("userId", user.getId().toString()).putMetadata("plan", plan.name()).build();

            Session session = Session.create(params);
            log.info("Created checkout session {} for user {}", session.getId(), user.getId());

            return session.getUrl();

        } catch (com.stripe.exception.StripeException e) {
            log.error("Failed to create checkout session for user {}", user.getId(), e);
            throw new StripeException("payment.error.stripe", e);
        }
    }

    @Override
    @Transactional
    public void handleCheckoutComplete(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);
            String subscriptionId = session.getSubscription();
            String userId = session.getMetadata().get("userId");

            com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription.retrieve(subscriptionId);

            User user = userRepository.findById(Long.parseLong(userId))
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Get the first subscription item's billing period
            var subscriptionItem = stripeSubscription.getItems().getData().get(0);

            Subscription subscription = Subscription.builder().user(user)
                    .stripeSubscriptionId(stripeSubscription.getId()).stripeCustomerId(stripeSubscription.getCustomer())
                    .plan(SubscriptionPlan.valueOf(session.getMetadata().get("plan"))).status(SubscriptionStatus.ACTIVE)
                    .currentPeriodStart(toLocalDateTime(subscriptionItem.getCurrentPeriodStart()))
                    .currentPeriodEnd(toLocalDateTime(subscriptionItem.getCurrentPeriodEnd())).cancelAtPeriodEnd(false)
                    .build();

            subscriptionRepository.save(subscription);

            user.setPlan(PlanType.PREMIUM);
            userRepository.save(user);

            log.info("Subscription {} activated for user {}", subscriptionId, userId);

            // Send confirmation email
            String planName = subscription.getPlan() == SubscriptionPlan.MONTHLY ? "Premium Mensuel" : "Premium Annuel";
            String nextBillingDate = subscription.getCurrentPeriodEnd()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            emailService.sendSubscriptionConfirmationEmail(user.getEmail(), user.getFirstName(), planName,
                    nextBillingDate);

        } catch (Exception e) {
            log.error("Failed to handle checkout complete for session {}", sessionId, e);
            throw new StripeException("payment.error.stripe", e);
        }
    }

    @Override
    @Transactional
    public void handleSubscriptionUpdated(String subscriptionId) {
        try {
            com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription.retrieve(subscriptionId);

            Subscription subscription = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                    .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

            SubscriptionStatus oldStatus = subscription.getStatus();
            SubscriptionStatus newStatus = mapStripeStatus(stripeSubscription.getStatus());

            // Get the first subscription item's billing period
            var subscriptionItem = stripeSubscription.getItems().getData().get(0);

            subscription.setStatus(newStatus);
            subscription.setCurrentPeriodStart(toLocalDateTime(subscriptionItem.getCurrentPeriodStart()));
            subscription.setCurrentPeriodEnd(toLocalDateTime(subscriptionItem.getCurrentPeriodEnd()));
            subscription.setCancelAtPeriodEnd(stripeSubscription.getCancelAtPeriodEnd());

            if ((newStatus == SubscriptionStatus.CANCELED || newStatus == SubscriptionStatus.PAST_DUE)
                    && oldStatus == SubscriptionStatus.ACTIVE) {
                User user = subscription.getUser();
                user.setPlan(PlanType.FREE);
                userRepository.save(user);
                log.info("Downgraded user {} to FREE due to subscription status change", user.getId());
            }

            subscriptionRepository.save(subscription);
            log.info("Updated subscription {} status to {}", subscriptionId, newStatus);

        } catch (Exception e) {
            log.error("Failed to handle subscription update for {}", subscriptionId, e);
            throw new StripeException("payment.error.stripe", e);
        }
    }

    @Override
    @Transactional
    public void handleSubscriptionDeleted(String subscriptionId) {
        try {
            Subscription subscription = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                    .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setCanceledAt(LocalDateTime.now());

            User user = subscription.getUser();
            user.setPlan(PlanType.FREE);

            subscriptionRepository.save(subscription);
            userRepository.save(user);

            log.info("Subscription {} deleted, user {} downgraded to FREE", subscriptionId, user.getId());

        } catch (Exception e) {
            log.error("Failed to handle subscription deletion for {}", subscriptionId, e);
            throw new StripeException("payment.error.stripe", e);
        }
    }

    @Override
    @Transactional
    public void cancelSubscription(Long userId, boolean immediately) {
        try {
            Subscription subscription = subscriptionRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("No active subscription found for user: " + userId));

            com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription
                    .retrieve(subscription.getStripeSubscriptionId());

            if (immediately) {
                stripeSubscription.cancel();
                subscription.setStatus(SubscriptionStatus.CANCELED);
                subscription.setCanceledAt(LocalDateTime.now());

                User user = subscription.getUser();
                user.setPlan(PlanType.FREE);
                userRepository.save(user);

                log.info("Immediately canceled subscription for user {}", userId);
            } else {
                com.stripe.param.SubscriptionUpdateParams params = com.stripe.param.SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true).build();
                stripeSubscription.update(params);
                subscription.setCancelAtPeriodEnd(true);

                log.info("Scheduled subscription cancellation at period end for user {}", userId);
            }

            subscriptionRepository.save(subscription);

        } catch (Exception e) {
            log.error("Failed to cancel subscription for user {}", userId, e);
            throw new StripeException("payment.error.stripe", e);
        }
    }

    @Override
    public String getPortalSession(String customerId) {
        try {
            com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams
                    .builder().setCustomer(customerId).setReturnUrl(baseUrl + "/profile").build();

            com.stripe.model.billingportal.Session portalSession = com.stripe.model.billingportal.Session
                    .create(params);

            log.info("Created portal session {} for customer {}", portalSession.getId(), customerId);
            return portalSession.getUrl();

        } catch (com.stripe.exception.StripeException e) {
            log.error("Failed to create portal session for customer {}", customerId, e);
            throw new StripeException("payment.error.stripe", e);
        }
    }

    private String getOrCreateCustomer(User user) throws com.stripe.exception.StripeException {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isEmpty()) {
            return user.getStripeCustomerId();
        }

        CustomerCreateParams params = CustomerCreateParams.builder().setEmail(user.getEmail())
                .setName(user.getFirstName() + " " + user.getLastName()).putMetadata("userId", user.getId().toString())
                .build();

        Customer customer = Customer.create(params);
        user.setStripeCustomerId(customer.getId());
        userRepository.save(user);

        log.info("Created Stripe customer {} for user {}", customer.getId(), user.getId());
        return customer.getId();
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            default -> SubscriptionStatus.ACTIVE;
        };
    }
}
