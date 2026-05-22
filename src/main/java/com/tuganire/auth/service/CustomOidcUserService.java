package com.tuganire.auth.service;

import com.tuganire.auth.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * OIDC user service (Google, etc.). Spring routes requests here when the provider includes the {@code openid} scope.
 * Without this bean wired into {@code .userInfoEndpoint().oidcUserService(...)}, Spring uses its default
 * {@link OidcUserService} and our local-user mapping is silently bypassed — Google users would log in but no
 * {@link User} row would ever be persisted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final OAuth2UserMapper userMapper;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        User user = userMapper.resolveUser(registrationId, oidcUser.getAttributes());
        return new OAuth2UserPrincipal(user, oidcUser.getAttributes(), oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
