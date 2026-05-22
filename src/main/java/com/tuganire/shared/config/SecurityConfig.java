package com.tuganire.shared.config;

import com.tuganire.auth.service.CustomOAuth2UserService;
import com.tuganire.auth.service.CustomOidcUserService;
import com.tuganire.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final PasswordEncoder passwordEncoder;
    private final CustomOAuth2UserService oauth2UserService;
    private final CustomOidcUserService oidcUserService;
    // Present only when at least one OAuth provider is fully configured (client-id + secret).
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Content-Security-Policy directive string.
    // 'unsafe-inline' AND 'unsafe-eval' are tolerated for script-src because Alpine.js uses
    // new Function() / new AsyncFunction() to evaluate x-data, @click, x-show expressions.
    // 'unsafe-inline' is also kept for style-src (DaisyUI / Tailwind inline patterns).
    // Nonce-based hardening + dropping unsafe-eval (requires Alpine CSP build) is a P2 follow-up.
    private static final String CSP_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://js.stripe.com https://checkout.stripe.com; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com /webjars/; "
            + "font-src 'self' https://fonts.gstatic.com /webjars/; " + "img-src 'self' data: https:; "
            + "frame-src https://js.stripe.com https://checkout.stripe.com https://*.stripe.com; "
            + "connect-src 'self' https://api.stripe.com; " + "object-src 'none'; " + "base-uri 'self'; "
            + "form-action 'self' https://checkout.stripe.com";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password",
                                "/webjars/**", "/css/**", "/js/**", "/images/**", "/files/**", "/error",
                                "/payment/pricing", "/payment/success", "/payment/cancel", "/.well-known/**",
                                "/webhooks/**", "/blog/**", "/privacy", "/terms", "/legal", "/verify-email/**",
                                "/actuator/health/**", "/actuator/info", "/oauth2/**", "/login/oauth2/**")
                        .permitAll().requestMatchers("/dashboard/**", "/profile/**", "/payment/**").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().authenticated())
                .headers(headers -> headers
                        // HSTS: browsers enforce HTTPS for 1 year including sub-domains
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                        // CSP: see CSP_POLICY constant above
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_POLICY))
                        // No embedding in iframes
                        .frameOptions(f -> f.deny())
                        // Prevent MIME-type sniffing
                        .contentTypeOptions(c -> {
                        })
                        // Referrer-Policy
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Permissions-Policy: minimal surface; payment=(self) for Stripe Checkout return
                        .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=(self)")))
                .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler).failureUrl("/login?error=true").usernameParameter("email")
                        .passwordParameter("password").permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/?logout=true")
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID").permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().newSession().maximumSessions(1))
                .csrf(csrf -> csrf
                        // Disable CSRF only for webhook endpoints (external services can't send CSRF tokens)
                        .ignoringRequestMatchers("/webhooks/**"));

        // OAuth2 login: only wired when at least one provider is configured (GOOGLE_CLIENT_ID set).
        // Spring Boot's OAuth2ClientAutoConfiguration registers ClientRegistrationRepository only when at least one
        // registration has a non-empty client-id, so this stays a no-op in fresh forks.
        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            // Both services must be wired: Spring routes OIDC providers (Google) through oidcUserService and plain
            // OAuth2 providers (GitHub, etc.) through userService. Forgetting one silently bypasses our local-user
            // mapping for that flow.
            http.oauth2Login(oauth -> oauth.loginPage("/login")
                    .userInfoEndpoint(
                            userInfo -> userInfo.userService(oauth2UserService).oidcUserService(oidcUserService))
                    .successHandler(loginSuccessHandler).failureUrl("/login?error=oauth"));
        }

        return http.build();
    }
}
