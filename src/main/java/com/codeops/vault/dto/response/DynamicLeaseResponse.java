package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.LeaseStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response representing a dynamic secret lease.
 * Contains the generated credentials only on creation.
 *
 * @param id                 Lease unique identifier.
 * @param leaseId            Unique lease string identifier.
 * @param secretId           Source secret identifier.
 * @param secretPath         Path of the source secret.
 * @param backendType        Backend type (e.g., "postgresql").
 * @param status             Current lease status.
 * @param ttlSeconds         Lease duration in seconds.
 * @param expiresAt          When the lease expires.
 * @param revokedAt          When the lease was revoked (if applicable).
 * @param requestedByUserId  User who requested the lease.
 * @param connectionDetails  Connection details (only on creation).
 * @param createdAt          Creation timestamp.
 */
public record DynamicLeaseResponse(
        UUID id,
        String leaseId,
        UUID secretId,
        String secretPath,
        String backendType,
        LeaseStatus status,
        int ttlSeconds,
        Instant expiresAt,
        Instant revokedAt,
        UUID requestedByUserId,
        Map<String, Object> connectionDetails,
        Instant createdAt
) {}
