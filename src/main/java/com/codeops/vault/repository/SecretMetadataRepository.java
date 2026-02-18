package com.codeops.vault.repository;

import com.codeops.vault.entity.SecretMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SecretMetadata} entities.
 *
 * <p>Provides query methods for key-value metadata pairs attached to secrets.</p>
 */
@Repository
public interface SecretMetadataRepository extends JpaRepository<SecretMetadata, UUID> {

    /**
     * Returns all metadata entries for a secret.
     *
     * @param secretId the parent secret ID
     * @return list of metadata entries
     */
    List<SecretMetadata> findBySecretId(UUID secretId);

    /**
     * Finds a specific metadata entry by secret and key.
     *
     * @param secretId    the parent secret ID
     * @param metadataKey the metadata key
     * @return the metadata entry if found
     */
    Optional<SecretMetadata> findBySecretIdAndMetadataKey(UUID secretId, String metadataKey);

    /**
     * Deletes a specific metadata entry by secret and key.
     *
     * @param secretId    the parent secret ID
     * @param metadataKey the metadata key
     */
    void deleteBySecretIdAndMetadataKey(UUID secretId, String metadataKey);

    /**
     * Deletes all metadata entries for a secret.
     *
     * @param secretId the parent secret ID
     */
    void deleteBySecretId(UUID secretId);

    /**
     * Checks whether a metadata entry exists for a secret and key.
     *
     * @param secretId    the parent secret ID
     * @param metadataKey the metadata key
     * @return true if the entry exists
     */
    boolean existsBySecretIdAndMetadataKey(UUID secretId, String metadataKey);
}
