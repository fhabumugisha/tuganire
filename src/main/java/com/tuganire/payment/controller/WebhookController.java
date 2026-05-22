package com.tuganire.payment.controller;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.tuganire.payment.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final StripeService stripeService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {

        Event event;

        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
            log.info("Received Stripe webhook event: {}", event.getType());
        } catch (Exception e) {
            log.error("Webhook signature verification failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed" :
                    handleCheckoutSessionCompleted(event);
                    break;

                case "customer.subscription.updated" :
                    handleSubscriptionUpdated(event);
                    break;

                case "customer.subscription.deleted" :
                    handleSubscriptionDeleted(event);
                    break;

                case "invoice.payment_failed" :
                    handlePaymentFailed(event);
                    break;

                case "invoice.payment_succeeded" :
                    handlePaymentSucceeded(event);
                    break;

                default :
                    log.info("Unhandled webhook event type: {}", event.getType());
            }

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing webhook event: {}", event.getType(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        com.stripe.model.checkout.Session session = (com.stripe.model.checkout.Session) event
                .getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new RuntimeException("Failed to deserialize session"));

        log.info("Processing checkout session completed: {}", session.getId());
        stripeService.handleCheckoutComplete(session.getId());
    }

    private void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new RuntimeException("Failed to deserialize subscription"));

        log.info("Processing subscription updated: {}", subscription.getId());
        stripeService.handleSubscriptionUpdated(subscription.getId());
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new RuntimeException("Failed to deserialize subscription"));

        log.info("Processing subscription deleted: {}", subscription.getId());
        stripeService.handleSubscriptionDeleted(subscription.getId());
    }

    private void handlePaymentFailed(Event event) {
        com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));

        log.warn("Payment failed for invoice: {} (customer: {})", invoice.getId(), invoice.getCustomer());
    }

    private void handlePaymentSucceeded(Event event) {
        com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));

        log.info("Payment succeeded for invoice: {} (customer: {})", invoice.getId(), invoice.getCustomer());
    }
}
