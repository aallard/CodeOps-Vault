package com.codeops.vault.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representing a secret version's metadata (never its encrypted value).
 *
 * @param id              Version unique identifier.
 * @param secretId        Parent secret identifier.
 * @param versionNumber   Sequential version number.
 * @param encryptionKeyId Identifier of the encryption key used.
 * @param changeDescription Description of what changed in this version.
 * @param createdByUserId User who created this version.
 * @param isDestroyed     Whether this version has been permanently destroyed.
 * @param createdAt       Creation timestamp.
 */
public record SecretVersionResponse(
        UUID id,
        UUID secretId,
        int versionNumber,
        String encryptionKeyId,
        String changeDescription,
        UUID createdByUserId,
        boolean isDestroyed,
        Instant createdAt
) {}
