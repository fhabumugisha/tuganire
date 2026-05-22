package com.tuganire.shared.config;

import com.tuganire.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    public LoginSuccessHandler(@Lazy AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        // For OAuth2 logins, lastLoginAt is already set by OAuth2UserMapper during principal creation — skip the
        // duplicate UPDATE.
        if (!(authentication instanceof OAuth2AuthenticationToken)
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            authService.updateLastLogin(userDetails.getUsername());
        }

        response.sendRedirect("/dashboard");
    }
}
