package com.tuganire.integration;

import com.tuganire.cache.TranslationCache;
import com.tuganire.translation.TranslationResponse;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests.
 *
 * <p>
 * Starts a Testcontainers PostgreSQL instance and wires its JDBC URL into the Spring context via
 * {@link DynamicPropertySource}. The {@code it} profile activates {@code application-it.yml}. All subclasses share the
 * same static container, which Testcontainers starts once per JVM.
 *
 * <p>
 * A no-op {@link TranslationCache} is registered as {@code @Primary} so that the Redis-backed implementation is not
 * active in tests (no Redis container is started). This keeps ITs fast and focused on business logic, not
 * infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
@Import(AbstractIntegrationTest.NoOpCacheConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tuganire_it").withUsername("tuganire").withPassword("tuganire");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Registers a no-op {@link TranslationCache} as the primary bean, overriding the Redis-backed implementation.
     * Prevents any Redis connection attempt in the IT context.
     */
    @TestConfiguration
    static class NoOpCacheConfig {

        @Bean
        @Primary
        TranslationCache noOpTranslationCacheForIt() {
            return new TranslationCache() {

                @Override
                public Optional<TranslationResponse> find(String sourceText, Locale src, Locale tgt) {
                    return Optional.empty();
                }

                @Override
                public void put(String sourceText, Locale src, Locale tgt, TranslationResponse response) {
                    // intentionally empty — no caching in ITs
                }
            };
        }
    }
}
