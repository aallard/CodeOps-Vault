package com.codeops.vault.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Vault encryption master key, bound to the
 * {@code codeops.vault} prefix in application properties.
 *
 * <p>The master key is used to derive AES-256-GCM encryption keys for secret storage.
 * It must be at least 32 characters long. This key is separate from the JWT secret.</p>
 */
@ConfigurationProperties(prefix = "codeops.vault")
@Getter
@Setter
public class VaultProperties {
    private String masterKey;

    /**
     * Validates that the master key is configured and meets the minimum length requirement
     * of 32 characters. Invoked automatically after dependency injection.
     *
     * @throws IllegalStateException if the master key is null, blank, or shorter than 32 characters
     */
    @PostConstruct
    public void validateMasterKey() {
        if (masterKey == null || masterKey.isBlank() || masterKey.length() < 32) {
            throw new IllegalStateException(
                    "Vault master key must be at least 32 characters. Set the VAULT_MASTER_KEY environment variable.");
        }
    }
}
