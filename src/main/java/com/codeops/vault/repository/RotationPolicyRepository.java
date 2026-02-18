package com.codeops.vault.repository;

import com.codeops.vault.entity.RotationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RotationPolicy} entities.
 *
 * <p>Provides query methods for rotation policies, including
 * active policy lookups and due-for-rotation queries.</p>
 */
@Repository
public interface RotationPolicyRepository extends JpaRepository<RotationPolicy, UUID> {

    /**
     * Finds the rotation policy for a specific secret.
     *
     * @param secretId the secret ID
     * @return the rotation policy if one exists
     */
    Optional<RotationPolicy> findBySecretId(UUID secretId);

    /**
     * Finds active rotation policies whose next rotation time is before the given timestamp.
     *
     * @param now the current timestamp
     * @return list of policies due for rotation
     */
    List<RotationPolicy> findByIsActiveTrueAndNextRotationAtBefore(Instant now);

    /**
     * Returns all active rotation policies.
     *
     * @return list of active policies
     */
    List<RotationPolicy> findByIsActiveTrue();

    /**
     * Counts all active rotation policies.
     *
     * @return count of active policies
     */
    long countByIsActiveTrue();
}
