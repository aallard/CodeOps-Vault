package com.codeops.vault.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing a decrypted secret value.
 * Only returned from explicit "read value" endpoints.
 *
 * @param secretId      The secret's unique identifier.
 * @param path          The secret's hierarchical path.
 * @param name          The secret's human-readable name.
 * @param versionNumber The version number of the returned value.
 * @param value         The decrypted secret value.
 * @param createdAt     When this version was created.
 */
public record SecretValueResponse(
        UUID secretId,
        String path,
        String name,
        int versionNumber,
        String value,
        Instant createdAt
) {}
