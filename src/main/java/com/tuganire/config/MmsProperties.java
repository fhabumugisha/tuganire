package com.tuganire.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * Configuration properties for the Python MMS server (Kinyarwanda ASR + MMS-TTS fallback).
 *
 * <p>
 * Binds from {@code tuganire.mms.*} in {@code application.yml}. The MMS server runs as a separate, scale-to-zero
 * Railway service: when idle it sleeps and the next request triggers a cold start that reloads ~4&nbsp;GB of models
 * (tens of seconds). The timeouts here let the backend ride out that wake; retries are handled declaratively on the
 * provider methods via Spring Framework's {@code @Retryable} (only gateway 5xx are retried — see the providers).
 *
 * @param baseUrl
 *            base URL of the MMS server ({@code MMS_TTS_URL}); defaults to localhost for dev
 * @param connectTimeout
 *            TCP connect timeout — short, since the Railway edge accepts connections quickly even while waking
 * @param readTimeout
 *            socket read timeout — long enough to cover a full cold start (model reload) plus inference. A read timeout
 *            is treated as terminal (not retried) so we never pile a second heavy inference on an already-busy server
 * @param sharedSecret
 *            optional shared secret sent as {@code X-MMS-Secret}; when blank the header is omitted (no auth). Set it on
 *            both this backend and the MMS server to require authentication between the two services
 */
@ConfigurationProperties(prefix = "tuganire.mms")
public record MmsProperties(@DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("10s") Duration connectTimeout, @DefaultValue("90s") Duration readTimeout,
        @DefaultValue("") String sharedSecret) {

    /** Header carrying {@link #sharedSecret} when configured. */
    private static final String SECRET_HEADER = "X-MMS-Secret";

    /**
     * Builds a {@link ClientHttpRequestFactory} carrying the MMS connect/read timeouts, shared by the MMS STT and TTS
     * RestClient groups so a cold-start wake is not cut short by the JDK default read timeout.
     *
     * @return a request factory configured with {@link #connectTimeout} and {@link #readTimeout}
     */
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /**
     * Builds the {@link RestClientHttpServiceGroupConfigurer} for an MMS HTTP service group: base URL, timeout-aware
     * request factory, and the optional shared-secret header. Shared by the STT and TTS client configs so the wiring
     * lives in one place.
     *
     * @param groupName
     *            the HTTP service group name (e.g. {@code "mms-stt"})
     * @return the group configurer
     */
    public RestClientHttpServiceGroupConfigurer groupConfigurer(String groupName) {
        return groups -> groups.filterByName(groupName).forEachClient((group, builder) -> {
            builder.baseUrl(baseUrl).requestFactory(clientHttpRequestFactory());
            if (sharedSecret != null && !sharedSecret.isBlank()) {
                builder.defaultHeader(SECRET_HEADER, sharedSecret);
            }
        });
    }
}
