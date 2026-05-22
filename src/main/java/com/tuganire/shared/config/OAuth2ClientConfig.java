package com.tuganire.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Conditionally registers OAuth2 providers. The {@link ClientRegistrationRepository} bean only exists when at least one
 * provider has been configured, which keeps {@link SecurityConfig}'s {@code .oauth2Login(...)} block dormant in
 * un-configured forks. {@code @ConditionalOnProperty} would treat an empty string as "present" (env var default is
 * {@code ${GOOGLE_CLIENT_ID:}}), so we evaluate non-empty via SpEL.
 */
@Configuration
@ConditionalOnExpression("'${tuganire.oauth.google.client-id:}'.length() > 0")
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${tuganire.oauth.google.client-id}") String googleClientId,
            @Value("${tuganire.oauth.google.client-secret}") String googleClientSecret) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(googleClientId)
                .clientSecret(googleClientSecret).build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
