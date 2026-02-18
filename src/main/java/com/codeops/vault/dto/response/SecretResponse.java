package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.SecretType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response representing a secret's metadata (never its value).
 *
 * @param id              Secret unique identifier.
 * @param teamId          Owning team identifier.
 * @param path            Hierarchical path.
 * @param name            Human-readable name.
 * @param description     Description of the secret.
 * @param secretType      Type of secret (STATIC, DYNAMIC, REFERENCE).
 * @param currentVersion  Current version number.
 * @param maxVersions     Maximum versions retained.
 * @param retentionDays   Days to retain old versions.
 * @param expiresAt       Expiration timestamp.
 * @param lastAccessedAt  Last access timestamp.
 * @param lastRotatedAt   Last rotation timestamp.
 * @param ownerUserId     User who created the secret.
 * @param referenceArn    External store ARN for REFERENCE type.
 * @param isActive        Whether the secret is active.
 * @param metadata        Key-value metadata pairs.
 * @param createdAt       Creation timestamp.
 * @param updatedAt       Last update timestamp.
 */
public record SecretResponse(
        UUID id,
        UUID teamId,
        String path,
        String name,
        String description,
        SecretType secretType,
        int currentVersion,
        Integer maxVersions,
        Integer retentionDays,
        Instant expiresAt,
        Instant lastAccessedAt,
        Instant lastRotatedAt,
        UUID ownerUserId,
        String referenceArn,
        boolean isActive,
        Map<String, String> metadata,
        Instant createdAt,
        Instant updatedAt
) {}
