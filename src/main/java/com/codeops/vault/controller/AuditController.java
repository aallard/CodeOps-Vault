package com.codeops.vault.controller;

import com.codeops.vault.config.AppConstants;
import com.codeops.vault.dto.request.AuditQueryRequest;
import com.codeops.vault.dto.response.AuditEntryResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.AuditService;
import com.codeops.vault.service.SealService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for querying the Vault audit log.
 *
 * <p>Provides endpoints for searching audit entries, viewing resource-specific
 * audit trails, and retrieving audit statistics. All endpoints require ADMIN
 * role and an unsealed Vault.</p>
 *
 * @see AuditService
 */
@RestController
@RequestMapping(AppConstants.VAULT_API_PREFIX + "/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final SealService sealService;

    /**
     * Queries the audit log with optional filters.
     *
     * <p>Supports filtering by userId, operation, path, resourceType+resourceId,
     * time range, and success status. Results are paginated and sorted by
     * creation date descending.</p>
     *
     * @param userId       Filter by acting user (optional).
     * @param operation    Filter by operation type (optional).
     * @param path         Filter by secret path (optional).
     * @param resourceType Filter by resource type (optional, requires resourceId).
     * @param resourceId   Filter by resource ID (optional, requires resourceType).
     * @param successOnly  If false, return only failed operations (optional).
     * @param startTime    Start of time range (optional, requires endTime).
     * @param endTime      End of time range (optional, requires startTime).
     * @param page         Page number (default 0).
     * @param size         Page size (default 20).
     * @return Paginated audit entry responses.
     */
    @GetMapping("/query")
    public ResponseEntity<PageResponse<AuditEntryResponse>> queryAuditLog(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) Boolean successOnly,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();

        AuditQueryRequest query = new AuditQueryRequest(
                userId, operation, path, resourceType, resourceId,
                successOnly, startTime, endTime);

        Pageable pageable = PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(auditService.queryAuditLog(teamId, query, pageable));
    }

    /**
     * Gets audit entries for a specific resource.
     *
     * @param resourceType The resource type (SECRET, POLICY, TRANSIT_KEY, etc.).
     * @param resourceId   The resource ID.
     * @param page         Page number (default 0).
     * @param size         Page size (default 20).
     * @return Paginated audit entry responses for the resource.
     */
    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<PageResponse<AuditEntryResponse>> getAuditForResource(
            @PathVariable String resourceType,
            @PathVariable UUID resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();

        Pageable pageable = PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(auditService.getAuditForResource(teamId, resourceType, resourceId, pageable));
    }

    /**
     * Gets audit log statistics for the current team.
     *
     * @return Map with totalEntries, failedEntries, and operation counts.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getAuditStats() {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(auditService.getAuditStats(teamId));
    }
}
