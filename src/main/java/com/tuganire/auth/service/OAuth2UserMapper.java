package com.tuganire.auth.service;

import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.shared.constant.AuthProvider;
import com.tuganire.shared.constant.PlanType;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared user-resolution logic for OAuth2 / OIDC providers. Given the attributes Google (or any other provider)
 * returns, find an existing local {@link User} by email or create one. Links the provider id to existing accounts so
 * password-based users can later sign in via Google without losing data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserMapper {

    private static final String REGISTRATION_GOOGLE = "google";

    private static final String ERROR_MISSING_EMAIL = "missing_email";
    private static final String ERROR_EMAIL_NOT_VERIFIED = "email_not_verified";
    private static final String ERROR_UNSUPPORTED_PROVIDER = "unsupported_provider";

    private final UserRepository userRepository;

    @Transactional
    public User resolveUser(String registrationId, Map<String, Object> attrs) {
        AuthProvider provider = providerFor(registrationId);
        String email = (String) attrs.get(StandardClaimNames.EMAIL);
        String sub = (String) attrs.get(StandardClaimNames.SUB);
        String firstName = (String) attrs.getOrDefault(StandardClaimNames.GIVEN_NAME, "");
        String lastName = (String) attrs.getOrDefault(StandardClaimNames.FAMILY_NAME, "");
        Boolean emailVerified = (Boolean) attrs.getOrDefault(StandardClaimNames.EMAIL_VERIFIED, Boolean.FALSE);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_MISSING_EMAIL),
                    "OAuth provider did not return an email");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_EMAIL_NOT_VERIFIED),
                    "OAuth provider email is not verified");
        }

        return userRepository.findByEmail(email).map(existing -> linkProvider(existing, provider, sub))
                .orElseGet(() -> createFromProvider(email, provider, sub, firstName, lastName));
    }

    private AuthProvider providerFor(String registrationId) {
        String key = registrationId.toLowerCase(Locale.ROOT);
        if (REGISTRATION_GOOGLE.equals(key)) {
            return AuthProvider.GOOGLE;
        }
        throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_UNSUPPORTED_PROVIDER),
                "Unsupported OAuth provider: " + registrationId);
    }

    private User linkProvider(User existing, AuthProvider provider, String sub) {
        if (existing.getProvider() == null) {
            existing.setProvider(provider);
            existing.setProviderId(sub);
        }
        if (!existing.isEmailVerified()) {
            existing.setEmailVerified(true);
        }
        existing.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(existing);
    }

    private User createFromProvider(String email, AuthProvider provider, String sub, String firstName,
            String lastName) {
        log.info("Creating new user from {} OAuth: {}", provider, email);
        User user = User.builder().email(email).provider(provider).providerId(sub).firstName(firstName)
                .lastName(lastName).emailVerified(true).plan(PlanType.FREE).lastLoginAt(LocalDateTime.now()).build();
        return userRepository.save(user);
    }
}
