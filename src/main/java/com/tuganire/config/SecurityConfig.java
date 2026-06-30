package com.tuganire.config;

import com.tuganire.shared.security.RateLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 7 configuration for the Tuganire MVP.
 *
 * <p>
 * There is no user authentication in the MVP (anonymous UUID sessions only). The filter chain:
 *
 * <ul>
 * <li>Permits all requests to public routes (home, API, WebSocket, static assets, Actuator health).
 * <li>Disables form login and HTTP Basic — no login friction per the PRD UX requirement.
 * <li>Keeps CSRF protection enabled for browser POSTs (Thymeleaf / HTMX web POC), but ignores CSRF on
 * {@code /api/v1/**} and {@code /ws/**} which are programmatic/WebSocket clients.
 * <li>Adds a strict Content-Security-Policy header compatible with HTMX and Alpine.js.
 * <li>Registers the {@link RateLimitFilter} before the username-password authentication filter so that over-quota
 * requests are rejected early.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

    /**
     * Content-Security-Policy that allows 'self' plus the CDN origins used for HTMX, Alpine.js webjars, inline styles
     * required by Thymeleaf/Tailwind, and Google Fonts. Adjust the script/style-src directives if you load additional
     * CDN assets.
     *
     * <p>
     * {@code 'unsafe-eval'} is required by Alpine.js: it compiles {@code x-*} expressions to functions via
     * {@code new AsyncFunction(...)}. The CSP-friendly alternative is the {@code @alpinejs/csp} build, which forbids
     * inline expressions and requires registering every component with {@code Alpine.data(...)} — a larger change than
     * this MVP needs.
     */
    private static final String CSP_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net https://fonts.googleapis.com; "
            + "img-src 'self' data:; " + "connect-src 'self'; " + "font-src 'self' https://fonts.gstatic.com; "
            // media-src must allow data:/blob: — the TTS audio is same-origin ('self'), but the silent
            // audio clip used to unlock playback on iOS/Safari is a data: URI and would otherwise be blocked.
            + "media-src 'self' data: blob:; " + "frame-ancestors 'none'; " + "object-src 'none'";

    /**
     * Builds and exposes the application's {@link SecurityFilterChain}.
     *
     * @param http
     *            the {@link HttpSecurity} builder provided by Spring Security
     * @param corsConfigurationSource
     *            the CORS configuration from {@link CorsConfig}
     * @param rateLimitProperties
     *            rate-limit settings bound from application.yml
     * @param redisTemplate
     *            the shared {@link StringRedisTemplate} used by the rate-limit filter
     * @return the configured {@link SecurityFilterChain} bean
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
            RateLimitProperties rateLimitProperties, StringRedisTemplate redisTemplate) throws Exception {

        // CSRF: enabled for browser (Thymeleaf/HTMX), ignored on API and WebSocket paths
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                // CORS — delegate to CorsConfigurationSource bean
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // CSRF — keep enabled but ignore programmatic endpoints
                .csrf(csrf -> csrf.csrfTokenRequestHandler(csrfHandler).ignoringRequestMatchers("/api/v1/**", "/ws/**"))

                // Authorization — fully open in the MVP (no login)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index", "/actuator/health", "/actuator/health/**", "/api/v1/**",
                                "/ws/**", "/webjars/**", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error")
                        .permitAll().anyRequest().permitAll())

                // Disable form-based and HTTP Basic login — zero friction startup
                .formLogin(login -> login.disable()).httpBasic(basic -> basic.disable())

                // Security headers
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP_POLICY))
                        .frameOptions(frame -> frame.deny()).xssProtection(xss -> xss.disable()) // CSP supersedes the
                                                                                                 // legacy
                                                                                                 // X-XSS-Protection
                                                                                                 // header
                        .contentTypeOptions(ct -> {
                        }))

                // Rate-limit filter applied before the auth filter (early rejection of over-quota requests)
                .addFilterBefore(new RateLimitFilter(redisTemplate, rateLimitProperties),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
