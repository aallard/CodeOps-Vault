package com.codeops.vault.repository;

import com.codeops.vault.entity.DynamicLease;
import com.codeops.vault.entity.enums.LeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link DynamicLease} entities.
 *
 * <p>Provides query methods for dynamic leases, including lease ID
 * lookups, status-based filtering, and expired lease detection.</p>
 */
@Repository
public interface DynamicLeaseRepository extends JpaRepository<DynamicLease, UUID> {

    /**
     * Finds a lease by its unique lease identifier string.
     *
     * @param leaseId the lease identifier
     * @return the lease if found
     */
    Optional<DynamicLease> findByLeaseId(String leaseId);

    /**
     * Returns all leases for a secret.
     *
     * @param secretId the source secret ID
     * @return list of leases
     */
    List<DynamicLease> findBySecretId(UUID secretId);

    /**
     * Returns a page of leases for a secret.
     *
     * @param secretId the source secret ID
     * @param pageable pagination parameters
     * @return page of leases
     */
    Page<DynamicLease> findBySecretId(UUID secretId, Pageable pageable);

    /**
     * Returns all leases with the given status.
     *
     * @param status the lease status
     * @return list of matching leases
     */
    List<DynamicLease> findByStatus(LeaseStatus status);

    /**
     * Finds leases with the given status that expire before the given timestamp.
     *
     * @param status the lease status
     * @param now    the current timestamp
     * @return list of expired leases with the given status
     */
    List<DynamicLease> findByStatusAndExpiresAtBefore(LeaseStatus status, Instant now);

    /**
     * Counts leases for a secret with a specific status.
     *
     * @param secretId the source secret ID
     * @param status   the lease status
     * @return count of matching leases
     */
    long countBySecretIdAndStatus(UUID secretId, LeaseStatus status);

    /**
     * Counts all leases with the given status.
     *
     * @param status the lease status
     * @return count of matching leases
     */
    long countByStatus(LeaseStatus status);
}
