package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.SecretType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;

/**
 * Request to create a new secret in the Vault.
 *
 * @param path          Hierarchical path (e.g., "/services/talent-app/db/password"). Must start with "/".
 * @param name          Human-readable name for the secret.
 * @param value         The secret value to encrypt and store (plaintext — encrypted by service layer).
 * @param description   Optional description.
 * @param secretType    Type of secret: STATIC, DYNAMIC, or REFERENCE.
 * @param referenceArn  For REFERENCE type: the external store ARN/URL.
 * @param maxVersions   Maximum versions to retain (null = unlimited).
 * @param retentionDays Days to retain old versions (null = forever).
 * @param expiresAt     Optional expiration timestamp.
 * @param metadata      Optional key-value metadata pairs.
 */
public record CreateSecretRequest(
        @NotBlank @Size(max = 500) @Pattern(regexp = "^/.*") String path,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String value,
        @Size(max = 2000) String description,
        @NotNull SecretType secretType,
        @Size(max = 500) String referenceArn,
        @Min(1) @Max(1000) Integer maxVersions,
        @Min(1) Integer retentionDays,
        Instant expiresAt,
        Map<String, String> metadata
) {}
