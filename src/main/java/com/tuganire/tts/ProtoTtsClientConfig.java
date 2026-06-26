package com.tuganire.tts;

import com.tuganire.config.ProtoTtsConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers the {@link ProtoTtsClient} declarative HTTP service client and enables {@link ProtoTtsConfig}.
 *
 * <p>
 * Configures the underlying {@code RestClient} with the Proto base URL and a static
 * {@code Authorization: Bearer <token>} header read from {@code tuganire.proto.*}. The token is only attached as a
 * default header; when it is blank the {@link ProtoTtsProvider} short-circuits to the MMS fallback before any request
 * is made, so no unauthenticated call is ever sent.
 */
@Configuration
@EnableConfigurationProperties(ProtoTtsConfig.class)
@ImportHttpServices(group = "proto-tts", types = ProtoTtsClient.class)
public class ProtoTtsClientConfig {

    private static final String GROUP_NAME = "proto-tts";

    /**
     * Configures the RestClient for the {@code proto-tts} HTTP service group.
     *
     * @param protoConfig
     *            Proto API settings (base URL + bearer token)
     * @return configurer that sets the base URL and Authorization header on the RestClient builder
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer protoTtsGroupConfigurer(ProtoTtsConfig protoConfig) {
        return groups -> groups.filterByName(GROUP_NAME).forEachClient((group, builder) -> {
            builder.baseUrl(protoConfig.baseUrl());
            if (protoConfig.token() != null && !protoConfig.token().isBlank()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + protoConfig.token());
            }
        });
    }
}
