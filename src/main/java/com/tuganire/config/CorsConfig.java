package com.tuganire.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration for the Tuganire API. Allowed origins are driven by the {@code tuganire.security.allowed-origins}
 * property; the default value covers the production domain and local development.
 *
 * <p>
 * The {@code X-Session-Id} header is explicitly allowed so that the anonymous rate-limit key can be forwarded from
 * browser clients.
 */
@Configuration
public class CorsConfig {

    /**
     * Origins that are permitted to make cross-origin requests. Configured via
     * {@code tuganire.security.allowed-origins}; defaults to the production domain and localhost.
     */
    @Value("${tuganire.security.allowed-origins:https://tuganire.app,http://localhost:8080}")
    private List<String> allowedOrigins;

    /**
     * Produces a {@link CorsConfigurationSource} that permits GET, POST and PUT from the configured origin whitelist,
     * allows credentials, and exposes the {@code X-Session-Id} header.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT"));
        config.setAllowedHeaders(
                List.of(CorsConfiguration.ALL, "X-Session-Id", "X-CSRF-TOKEN", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("X-Session-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
