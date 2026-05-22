package com.tuganire.auth.service;

import com.tuganire.auth.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing authentication sessions. Handles automatic login after user registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationSessionService {

    /**
     * Automatically logs in a user by creating an authentication token and setting it in the security context.
     *
     * @param user
     *            the user to authenticate
     */
    public void autoLogin(User user) {
        log.debug("Auto-login user: {}", user.getEmail());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null,
                user.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.info("User {} successfully auto-logged in", user.getEmail());
    }

    /**
     * Clears the current authentication from the security context.
     */
    public void clearAuthentication() {
        SecurityContextHolder.clearContext();
        log.debug("Authentication context cleared");
    }
}
