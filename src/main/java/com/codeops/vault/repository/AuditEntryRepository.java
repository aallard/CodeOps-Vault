package com.codeops.vault.repository;

import com.codeops.vault.entity.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for {@link AuditEntry} entities.
 *
 * <p>Provides query methods for the vault audit log, including
 * team-scoped lookups, operation filtering, time range queries,
 * resource-specific queries, and failure filtering.</p>
 */
@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /**
     * Returns a page of audit entries for a team.
     *
     * @param teamId   the team ID
     * @param pageable pagination parameters
     * @return page of audit entries
     */
    Page<AuditEntry> findByTeamId(UUID teamId, Pageable pageable);

    /**
     * Returns a page of audit entries for a user.
     *
     * @param userId   the user ID
     * @param pageable pagination parameters
     * @return page of audit entries
     */
    Page<AuditEntry> findByUserId(UUID userId, Pageable pageable);

    /**
     * Returns a page of audit entries for a team filtered by operation type.
     *
     * @param teamId    the team ID
     * @param operation the operation type
     * @param pageable  pagination parameters
     * @return page of matching audit entries
     */
    Page<AuditEntry> findByTeamIdAndOperation(UUID teamId, String operation, Pageable pageable);

    /**
     * Returns a page of audit entries for a team at a specific path.
     *
     * @param teamId   the team ID
     * @param path     the secret path
     * @param pageable pagination parameters
     * @return page of matching audit entries
     */
    Page<AuditEntry> findByTeamIdAndPath(UUID teamId, String path, Pageable pageable);

    /**
     * Returns a page of audit entries for a team within a time range.
     *
     * @param teamId   the team ID
     * @param start    the start of the time range (inclusive)
     * @param end      the end of the time range (inclusive)
     * @param pageable pagination parameters
     * @return page of matching audit entries
     */
    Page<AuditEntry> findByTeamIdAndCreatedAtBetween(UUID teamId, Instant start, Instant end, Pageable pageable);

    /**
     * Returns a page of audit entries for a specific resource.
     *
     * @param teamId       the team ID
     * @param resourceType the resource type
     * @param resourceId   the resource ID
     * @param pageable     pagination parameters
     * @return page of matching audit entries
     */
    Page<AuditEntry> findByTeamIdAndResourceTypeAndResourceId(UUID teamId, String resourceType, UUID resourceId, Pageable pageable);

    /**
     * Returns a page of failed audit entries for a team.
     *
     * @param teamId   the team ID
     * @param pageable pagination parameters
     * @return page of failed audit entries
     */
    Page<AuditEntry> findByTeamIdAndSuccessFalse(UUID teamId, Pageable pageable);

    /**
     * Counts all audit entries for a team.
     *
     * @param teamId the team ID
     * @return entry count
     */
    long countByTeamId(UUID teamId);

    /**
     * Counts audit entries for a team by operation type.
     *
     * @param teamId    the team ID
     * @param operation the operation type
     * @return count of matching entries
     */
    long countByTeamIdAndOperation(UUID teamId, String operation);

    /**
     * Counts failed audit entries for a team.
     *
     * @param teamId the team ID
     * @return count of failed entries
     */
    long countByTeamIdAndSuccessFalse(UUID teamId);
}
