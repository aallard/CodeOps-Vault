package com.codeops.vault.repository;

import com.codeops.vault.entity.RotationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RotationHistory} entities.
 *
 * <p>Provides query methods for rotation history records, including
 * ordered retrieval, latest success lookup, and failure counting.</p>
 */
@Repository
public interface RotationHistoryRepository extends JpaRepository<RotationHistory, UUID> {

    /**
     * Returns a page of rotation history for a secret.
     *
     * @param secretId the secret ID
     * @param pageable pagination parameters
     * @return page of history records
     */
    Page<RotationHistory> findBySecretId(UUID secretId, Pageable pageable);

    /**
     * Returns all rotation history for a secret, ordered by creation time descending.
     *
     * @param secretId the secret ID
     * @return list of history records, newest first
     */
    List<RotationHistory> findBySecretIdOrderByCreatedAtDesc(UUID secretId);

    /**
     * Returns the most recent successful rotation for a secret.
     *
     * @param secretId the secret ID
     * @return the latest successful rotation if any
     */
    Optional<RotationHistory> findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(UUID secretId);

    /**
     * Counts all rotation history records for a secret.
     *
     * @param secretId the secret ID
     * @return record count
     */
    long countBySecretId(UUID secretId);

    /**
     * Counts failed rotation attempts for a secret.
     *
     * @param secretId the secret ID
     * @return count of failures
     */
    long countBySecretIdAndSuccessFalse(UUID secretId);
}
