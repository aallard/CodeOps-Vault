package com.codeops.vault.service;

import com.codeops.vault.config.RequestCorrelationFilter;
import com.codeops.vault.dto.mapper.AuditMapper;
import com.codeops.vault.dto.request.AuditQueryRequest;
import com.codeops.vault.dto.response.AuditEntryResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.entity.AuditEntry;
import com.codeops.vault.repository.AuditEntryRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for recording and querying Vault audit log entries.
 *
 * <p>Every Vault operation (reads, writes, deletes, policy changes,
 * seal/unseal, transit operations, lease management) is recorded as an
 * immutable audit entry. Audit logging is best-effort — failures in
 * audit recording never block the primary operation.</p>
 *
 * <p>Each audit entry captures:</p>
 * <ul>
 *   <li>Team and user context from the JWT</li>
 *   <li>Operation type (READ, WRITE, DELETE, ROTATE, etc.)</li>
 *   <li>Resource type and ID</li>
 *   <li>Client IP address from the HTTP request</li>
 *   <li>Correlation ID from MDC for request tracing</li>
 *   <li>Success/failure status and error details</li>
 * </ul>
 *
 * @see AuditEntry
 * @see AuditEntryRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final AuditMapper auditMapper;

    // ─── Logging Methods ────────────────────────────────────

    /**
     * Records a successful Vault operation in the audit log.
     *
     * <p>Extracts IP address from the current HTTP request and correlation
     * ID from MDC. Failures are silently logged — audit never blocks
     * primary operations.</p>
     *
     * @param teamId       Team context (nullable for system operations).
     * @param userId       Acting user (nullable for system/scheduled operations).
     * @param operation    The operation performed (e.g., READ, WRITE, DELETE).
     * @param path         Secret path or resource identifier.
     * @param resourceType Type of resource (SECRET, POLICY, TRANSIT_KEY, etc.).
     * @param resourceId   ID of the affected resource.
     * @param detailsJson  Additional JSON context (nullable).
     */
    public void logSuccess(UUID teamId, UUID userId, String operation,
                           String path, String resourceType, UUID resourceId,
                           String detailsJson) {
        try {
            AuditEntry entry = AuditEntry.builder()
                    .teamId(teamId)
                    .userId(userId)
                    .operation(operation)
                    .path(path)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .success(true)
                    .ipAddress(extractIpAddress())
                    .correlationId(extractCorrelationId())
                    .detailsJson(detailsJson)
                    .build();
            writeAuditEntry(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for operation {}: {}", operation, e.getMessage());
        }
    }

    /**
     * Records a failed Vault operation in the audit log.
     *
     * <p>Extracts IP address from the current HTTP request and correlation
     * ID from MDC. Failures are silently logged — audit never blocks
     * primary operations.</p>
     *
     * @param teamId       Team context (nullable for system operations).
     * @param userId       Acting user (nullable for system/scheduled operations).
     * @param operation    The operation performed.
     * @param path         Secret path or resource identifier.
     * @param resourceType Type of resource.
     * @param resourceId   ID of the affected resource.
     * @param errorMessage Error details describing the failure.
     */
    public void logFailure(UUID teamId, UUID userId, String operation,
                           String path, String resourceType, UUID resourceId,
                           String errorMessage) {
        try {
            AuditEntry entry = AuditEntry.builder()
                    .teamId(teamId)
                    .userId(userId)
                    .operation(operation)
                    .path(path)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .success(false)
                    .errorMessage(errorMessage)
                    .ipAddress(extractIpAddress())
                    .correlationId(extractCorrelationId())
                    .build();
            writeAuditEntry(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit failure log for operation {}: {}", operation, e.getMessage());
        }
    }

    // ─── Query Methods ──────────────────────────────────────

    /**
     * Queries the audit log with optional filters.
     *
     * <p>Applies the first matching filter in priority order:
     * resourceType+resourceId, userId, operation, path, time range,
     * successOnly, or returns all team entries if no filter is set.</p>
     *
     * @param teamId   Team ID for scoping.
     * @param query    Query parameters with optional filters.
     * @param pageable Pagination parameters.
     * @return Paginated list of audit entry responses.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> queryAuditLog(UUID teamId, AuditQueryRequest query,
                                                           Pageable pageable) {
        Page<AuditEntry> page;

        if (query.resourceType() != null && query.resourceId() != null) {
            page = auditEntryRepository.findByTeamIdAndResourceTypeAndResourceId(
                    teamId, query.resourceType(), query.resourceId(), pageable);
        } else if (query.userId() != null) {
            page = auditEntryRepository.findByUserId(query.userId(), pageable);
        } else if (query.operation() != null) {
            page = auditEntryRepository.findByTeamIdAndOperation(teamId, query.operation(), pageable);
        } else if (query.path() != null) {
            page = auditEntryRepository.findByTeamIdAndPath(teamId, query.path(), pageable);
        } else if (query.startTime() != null && query.endTime() != null) {
            page = auditEntryRepository.findByTeamIdAndCreatedAtBetween(
                    teamId, query.startTime(), query.endTime(), pageable);
        } else if (Boolean.FALSE.equals(query.successOnly())) {
            page = auditEntryRepository.findByTeamIdAndSuccessFalse(teamId, pageable);
        } else {
            page = auditEntryRepository.findByTeamId(teamId, pageable);
        }

        List<AuditEntryResponse> responses = auditMapper.toResponses(page.getContent());
        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Gets audit entries for a specific resource.
     *
     * @param teamId       Team ID for scoping.
     * @param resourceType The resource type (SECRET, POLICY, etc.).
     * @param resourceId   The resource ID.
     * @param pageable     Pagination parameters.
     * @return Paginated list of audit entry responses.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> getAuditForResource(UUID teamId, String resourceType,
                                                                  UUID resourceId, Pageable pageable) {
        Page<AuditEntry> page = auditEntryRepository.findByTeamIdAndResourceTypeAndResourceId(
                teamId, resourceType, resourceId, pageable);

        List<AuditEntryResponse> responses = auditMapper.toResponses(page.getContent());
        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Gets audit statistics for a team.
     *
     * @param teamId Team ID.
     * @return Map with "totalEntries", "failedEntries", and operation counts.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getAuditStats(UUID teamId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("totalEntries", auditEntryRepository.countByTeamId(teamId));
        stats.put("failedEntries", auditEntryRepository.countByTeamIdAndSuccessFalse(teamId));
        stats.put("readOperations", auditEntryRepository.countByTeamIdAndOperation(teamId, "READ"));
        stats.put("writeOperations", auditEntryRepository.countByTeamIdAndOperation(teamId, "WRITE"));
        stats.put("deleteOperations", auditEntryRepository.countByTeamIdAndOperation(teamId, "DELETE"));
        return stats;
    }

    // ─── Private Helpers ────────────────────────────────────

    /**
     * Persists an audit entry in a new transaction to avoid coupling
     * with the calling service's transaction.
     *
     * @param entry The audit entry to persist.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAuditEntry(AuditEntry entry) {
        auditEntryRepository.save(entry);
    }

    /**
     * Extracts the client IP address from the current HTTP request.
     *
     * @return The client IP, or "system" if no request context is available.
     */
    private String extractIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.trace("Could not extract IP address: {}", e.getMessage());
        }
        return "system";
    }

    /**
     * Extracts the correlation ID from the MDC context.
     *
     * @return The correlation ID, or "no-correlation-id" if not available.
     */
    private String extractCorrelationId() {
        String correlationId = MDC.get(RequestCorrelationFilter.MDC_CORRELATION_ID);
        return correlationId != null ? correlationId : "no-correlation-id";
    }
}
