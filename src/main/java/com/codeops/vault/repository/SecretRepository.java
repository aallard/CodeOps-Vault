package com.codeops.vault.repository;

import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.enums.SecretType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Secret} entities.
 *
 * <p>Provides CRUD operations and custom query methods for secrets,
 * including team-scoped lookups, path-based searches, type filtering,
 * and expiration checks.</p>
 */
@Repository
public interface SecretRepository extends JpaRepository<Secret, UUID> {

    /**
     * Finds a secret by team and exact path.
     *
     * @param teamId the owning team ID
     * @param path   the exact secret path
     * @return the secret if found
     */
    Optional<Secret> findByTeamIdAndPath(UUID teamId, String path);

    /**
     * Returns all secrets for a team.
     *
     * @param teamId the owning team ID
     * @return list of secrets
     */
    List<Secret> findByTeamId(UUID teamId);

    /**
     * Returns a page of secrets for a team.
     *
     * @param teamId   the owning team ID
     * @param pageable pagination parameters
     * @return page of secrets
     */
    Page<Secret> findByTeamId(UUID teamId, Pageable pageable);

    /**
     * Returns a page of secrets for a team filtered by secret type.
     *
     * @param teamId     the owning team ID
     * @param secretType the secret type filter
     * @param pageable   pagination parameters
     * @return page of matching secrets
     */
    Page<Secret> findByTeamIdAndSecretType(UUID teamId, SecretType secretType, Pageable pageable);

    /**
     * Returns a page of secrets whose path starts with the given prefix.
     *
     * @param teamId     the owning team ID
     * @param pathPrefix the path prefix to match
     * @param pageable   pagination parameters
     * @return page of matching secrets
     */
    Page<Secret> findByTeamIdAndPathStartingWith(UUID teamId, String pathPrefix, Pageable pageable);

    /**
     * Returns a page of active secrets for a team.
     *
     * @param teamId   the owning team ID
     * @param pageable pagination parameters
     * @return page of active secrets
     */
    Page<Secret> findByTeamIdAndIsActiveTrue(UUID teamId, Pageable pageable);

    /**
     * Returns a page of secrets whose name contains the search term (case-insensitive).
     *
     * @param teamId   the owning team ID
     * @param name     the search term
     * @param pageable pagination parameters
     * @return page of matching secrets
     */
    Page<Secret> findByTeamIdAndNameContainingIgnoreCase(UUID teamId, String name, Pageable pageable);

    /**
     * Returns active secrets whose path starts with the given prefix.
     *
     * @param teamId     the owning team ID
     * @param pathPrefix the path prefix to match
     * @return list of active matching secrets
     */
    List<Secret> findByTeamIdAndPathStartingWithAndIsActiveTrue(UUID teamId, String pathPrefix);

    /**
     * Checks whether a secret exists at the given team and path.
     *
     * @param teamId the owning team ID
     * @param path   the secret path
     * @return true if a secret exists at the path
     */
    boolean existsByTeamIdAndPath(UUID teamId, String path);

    /**
     * Counts all secrets for a team.
     *
     * @param teamId the owning team ID
     * @return secret count
     */
    long countByTeamId(UUID teamId);

    /**
     * Counts secrets of a specific type for a team.
     *
     * @param teamId     the owning team ID
     * @param secretType the type to count
     * @return count of matching secrets
     */
    long countByTeamIdAndSecretType(UUID teamId, SecretType secretType);

    /**
     * Finds active secrets that have expired (expiresAt before the given timestamp).
     *
     * @param now the current timestamp
     * @return list of expired active secrets
     */
    List<Secret> findByExpiresAtBeforeAndIsActiveTrue(Instant now);
}
