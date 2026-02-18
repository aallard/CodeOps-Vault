package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.RotationStrategy;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representing a rotation policy.
 *
 * @param id                    Rotation policy unique identifier.
 * @param secretId              The secret this policy applies to.
 * @param secretPath            Path of the secret.
 * @param strategy              Rotation strategy in use.
 * @param rotationIntervalHours How often the secret rotates (hours).
 * @param randomLength          For RANDOM_GENERATE: character length.
 * @param randomCharset         For RANDOM_GENERATE: allowed characters.
 * @param externalApiUrl        For EXTERNAL_API: endpoint URL.
 * @param isActive              Whether the rotation policy is active.
 * @param failureCount          Consecutive rotation failures.
 * @param maxFailures           Max failures before pausing.
 * @param lastRotatedAt         Last successful rotation timestamp.
 * @param nextRotationAt        Next scheduled rotation timestamp.
 * @param createdAt             Creation timestamp.
 * @param updatedAt             Last update timestamp.
 */
public record RotationPolicyResponse(
        UUID id,
        UUID secretId,
        String secretPath,
        RotationStrategy strategy,
        int rotationIntervalHours,
        Integer randomLength,
        String randomCharset,
        String externalApiUrl,
        boolean isActive,
        int failureCount,
        Integer maxFailures,
        Instant lastRotatedAt,
        Instant nextRotationAt,
        Instant createdAt,
        Instant updatedAt
) {}
