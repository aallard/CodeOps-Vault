package com.codeops.vault.dto.request;

import java.time.Instant;
import java.util.UUID;

/**
 * Query parameters for searching the Vault audit log.
 * All fields optional — used as filters.
 *
 * @param userId       Filter by acting user.
 * @param operation    Filter by operation type.
 * @param path         Filter by secret path.
 * @param resourceType Filter by resource type.
 * @param resourceId   Filter by resource ID.
 * @param successOnly  If true, only return successful operations.
 * @param startTime    Start of time range filter.
 * @param endTime      End of time range filter.
 */
public record AuditQueryRequest(
        UUID userId,
        String operation,
        String path,
        String resourceType,
        UUID resourceId,
        Boolean successOnly,
        Instant startTime,
        Instant endTime
) {}
