package com.codeops.vault.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representing a transit encryption key (never its key material).
 *
 * @param id                   Key unique identifier.
 * @param teamId               Owning team identifier.
 * @param name                 Key name.
 * @param description          Key description.
 * @param currentVersion       Current key version number.
 * @param minDecryptionVersion Oldest version allowed for decryption.
 * @param algorithm            Encryption algorithm.
 * @param isDeletable          Whether the key can be deleted.
 * @param isExportable         Whether key material can be exported.
 * @param isActive             Whether the key is active.
 * @param createdByUserId      User who created this key.
 * @param createdAt            Creation timestamp.
 * @param updatedAt            Last update timestamp.
 */
public record TransitKeyResponse(
        UUID id,
        UUID teamId,
        String name,
        String description,
        int currentVersion,
        int minDecryptionVersion,
        String algorithm,
        boolean isDeletable,
        boolean isExportable,
        boolean isActive,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {}
