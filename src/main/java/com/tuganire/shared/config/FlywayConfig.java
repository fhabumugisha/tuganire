package com.tuganire.shared.config;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway configuration that handles migration checksum mismatches gracefully. Automatically repairs checksum mismatches
 * without failing on new databases.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                // Attempt repair first to fix any checksum mismatches from modified migrations
                flyway.repair();
                log.debug("Flyway repair completed");
            } catch (FlywayException e) {
                log.warn("Flyway repair failed (non-fatal): {}", e.getMessage());
            }

            // Always run migrations
            flyway.migrate();
        };
    }
}
