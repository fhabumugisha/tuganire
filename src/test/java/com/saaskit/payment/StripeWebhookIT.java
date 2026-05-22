package com.tuganire.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuganire.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class StripeWebhookIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Value("${stripe.webhook-secret}")
    String webhookSecret;

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String payload = unhandledEventPayload();

        mockMvc.perform(post("/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=1700000000,v1=deadbeef").content(payload))
                .andExpect(status().isBadRequest()).andExpect(content().string("Invalid signature"));
    }

    @Test
    void validSignatureWithUnhandledEventReturnsOk() throws Exception {
        String payload = unhandledEventPayload();
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignature(payload, webhookSecret, timestamp);

        mockMvc.perform(post("/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature).content(payload)).andExpect(status().isOk())
                .andExpect(content().string("Webhook processed"));
    }

    private static String unhandledEventPayload() {
        return "{\"id\":\"evt_test_unhandled\",\"object\":\"event\",\"api_version\":\"2024-04-10\","
                + "\"created\":1700000000,\"type\":\"customer.created\",\"livemode\":false,"
                + "\"pending_webhooks\":0,\"request\":null,"
                + "\"data\":{\"object\":{\"id\":\"cus_test_123\",\"object\":\"customer\"}}}";
    }

    private static String buildStripeSignature(String payload, String secret, long timestamp) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hmac.length * 2);
        for (byte b : hmac) {
            hex.append(String.format("%02x", b));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }
}
