package com.codeops.vault;

import com.codeops.vault.config.JwtProperties;
import com.codeops.vault.config.ServiceUrlProperties;
import com.codeops.vault.config.VaultProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the CodeOps Vault service.
 *
 * <p>Provides secrets management for the CodeOps ecosystem, including encrypted
 * storage and retrieval of sensitive configuration values, credentials, and API keys.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class, VaultProperties.class})
public class CodeOpsVaultApplication {

    /**
     * Launches the CodeOps Vault Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CodeOpsVaultApplication.class, args);
    }
}
