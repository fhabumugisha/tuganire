package com.tuganire.shared.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @Value("${stripe.price-id-monthly}")
    private String priceIdMonthly;

    @Value("${stripe.price-id-yearly}")
    private String priceIdYearly;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
        log.info("Stripe SDK initialized with version: {}", Stripe.VERSION);
        log.info("Stripe API Key configured (length: {})", apiKey != null ? apiKey.length() : 0);
    }

    @Bean
    public String stripeMonthlyPriceId() {
        return priceIdMonthly;
    }

    @Bean
    public String stripeYearlyPriceId() {
        return priceIdYearly;
    }
}
