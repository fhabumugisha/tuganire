package com.tuganire.auth.service;

import com.tuganire.auth.model.User;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Hybrid principal: extends {@link User} (so Thymeleaf templates accessing {@code principal.firstName} /
 * {@code principal.email} keep working) and implements {@link OidcUser} (which itself extends {@code OAuth2User}), so
 * it satisfies both OIDC and plain-OAuth2 flows. This avoids forking templates by auth method.
 */
@Getter
public class OAuth2UserPrincipal extends User implements OidcUser {

    private final Map<String, Object> attributes;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    /** Constructor for OIDC providers (Google, etc.) — has id_token + UserInfo. */
    public OAuth2UserPrincipal(User user, Map<String, Object> attributes, OidcIdToken idToken, OidcUserInfo userInfo) {
        copyFrom(user);
        this.attributes = attributes;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    /** Constructor for non-OIDC OAuth2 providers (GitHub, etc.) — no id_token. */
    public OAuth2UserPrincipal(User user, Map<String, Object> attributes) {
        this(user, attributes, null, null);
    }

    @Override
    public String getName() {
        return getEmail();
    }

    @Override
    public Map<String, Object> getClaims() {
        return idToken != null ? idToken.getClaims() : Collections.emptyMap();
    }

    private void copyFrom(User user) {
        setId(user.getId());
        setEmail(user.getEmail());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setProvider(user.getProvider());
        setProviderId(user.getProviderId());
        setPlan(user.getPlan());
        setAdmin(user.isAdmin());
        setEmailVerified(user.isEmailVerified());
        setStripeCustomerId(user.getStripeCustomerId());
        setCreatedAt(user.getCreatedAt());
        setModifiedAt(user.getModifiedAt());
        setLastLoginAt(user.getLastLoginAt());
    }
}
