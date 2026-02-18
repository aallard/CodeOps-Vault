package com.codeops.vault.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representing a single audit log entry.
 *
 * @param id            Audit entry identifier.
 * @param teamId        Team context (null for system-level operations).
 * @param userId        Acting user (null for system/scheduled operations).
 * @param operation     The operation performed.
 * @param path          Secret path or resource identifier.
 * @param resourceType  Type of resource affected.
 * @param resourceId    ID of the affected resource.
 * @param success       Whether the operation succeeded.
 * @param errorMessage  Error details on failure.
 * @param ipAddress     Client IP address.
 * @param correlationId Request correlation ID.
 * @param createdAt     Timestamp of the audit entry.
 */
public record AuditEntryResponse(
        Long id,
        UUID teamId,
        UUID userId,
        String operation,
        String path,
        String resourceType,
        UUID resourceId,
        boolean success,
        String errorMessage,
        String ipAddress,
        String correlationId,
        Instant createdAt
) {}
