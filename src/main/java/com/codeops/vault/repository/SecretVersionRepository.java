package com.codeops.vault.repository;

import com.codeops.vault.entity.SecretVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SecretVersion} entities.
 *
 * <p>Provides query methods for versioned secret values, including
 * ordered retrieval, latest version lookup, pruning old versions,
 * and filtering out destroyed versions.</p>
 */
@Repository
public interface SecretVersionRepository extends JpaRepository<SecretVersion, UUID> {

    /**
     * Returns all versions of a secret, ordered by version number descending.
     *
     * @param secretId the parent secret ID
     * @return list of versions, newest first
     */
    List<SecretVersion> findBySecretIdOrderByVersionNumberDesc(UUID secretId);

    /**
     * Returns a page of versions for a secret.
     *
     * @param secretId the parent secret ID
     * @param pageable pagination parameters
     * @return page of versions
     */
    Page<SecretVersion> findBySecretId(UUID secretId, Pageable pageable);

    /**
     * Finds a specific version by secret ID and version number.
     *
     * @param secretId      the parent secret ID
     * @param versionNumber the version number
     * @return the version if found
     */
    Optional<SecretVersion> findBySecretIdAndVersionNumber(UUID secretId, Integer versionNumber);

    /**
     * Returns the latest version of a secret (highest version number).
     *
     * @param secretId the parent secret ID
     * @return the latest version if any exist
     */
    Optional<SecretVersion> findTopBySecretIdOrderByVersionNumberDesc(UUID secretId);

    /**
     * Counts all versions of a secret.
     *
     * @param secretId the parent secret ID
     * @return version count
     */
    long countBySecretId(UUID secretId);

    /**
     * Finds versions older than the given version number.
     *
     * @param secretId the parent secret ID
     * @param version  the threshold version number (exclusive)
     * @return list of older versions
     */
    List<SecretVersion> findBySecretIdAndVersionNumberLessThan(UUID secretId, Integer version);

    /**
     * Deletes versions older than the given version number.
     *
     * @param secretId the parent secret ID
     * @param version  the threshold version number (exclusive)
     */
    void deleteBySecretIdAndVersionNumberLessThan(UUID secretId, Integer version);

    /**
     * Returns non-destroyed versions of a secret, ordered newest first.
     *
     * @param secretId the parent secret ID
     * @return list of non-destroyed versions, newest first
     */
    List<SecretVersion> findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(UUID secretId);
}
