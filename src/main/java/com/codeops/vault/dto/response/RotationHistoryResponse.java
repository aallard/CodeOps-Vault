package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.RotationStrategy;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representing a rotation history entry.
 *
 * @param id                Rotation history unique identifier.
 * @param secretId          ID of the rotated secret.
 * @param secretPath        Path of the rotated secret.
 * @param strategy          Rotation strategy that was used.
 * @param previousVersion   Version number before rotation.
 * @param newVersion        Version number after rotation (null on failure).
 * @param success           Whether the rotation succeeded.
 * @param errorMessage      Error details if rotation failed.
 * @param durationMs        Rotation execution time in milliseconds.
 * @param triggeredByUserId User who triggered the rotation (null if automatic).
 * @param createdAt         Creation timestamp.
 */
public record RotationHistoryResponse(
        UUID id,
        UUID secretId,
        String secretPath,
        RotationStrategy strategy,
        Integer previousVersion,
        Integer newVersion,
        boolean success,
        String errorMessage,
        Long durationMs,
        UUID triggeredByUserId,
        Instant createdAt
) {}
