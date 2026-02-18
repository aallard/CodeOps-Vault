package com.codeops.vault.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Database seeder for the Vault service (dev profile only).
 *
 * <p>Currently a stub — no entities are defined in CV-001 (skeleton task).
 * Entities and seed data will be added in CV-002.</p>
 */
@Component
@Slf4j
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    /**
     * Runs on application startup in the dev profile.
     *
     * @param args command-line arguments (unused)
     */
    @Override
    public void run(String... args) {
        log.info("DataSeeder: skipping — no entities defined yet");
    }
}
