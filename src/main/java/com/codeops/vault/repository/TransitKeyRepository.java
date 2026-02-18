package com.codeops.vault.repository;

import com.codeops.vault.entity.TransitKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TransitKey} entities.
 *
 * <p>Provides query methods for transit encryption keys, including
 * team-scoped lookups, active key filtering, and uniqueness checks.</p>
 */
@Repository
public interface TransitKeyRepository extends JpaRepository<TransitKey, UUID> {

    /**
     * Finds a transit key by team and name.
     *
     * @param teamId the owning team ID
     * @param name   the key name
     * @return the transit key if found
     */
    Optional<TransitKey> findByTeamIdAndName(UUID teamId, String name);

    /**
     * Returns all transit keys for a team.
     *
     * @param teamId the owning team ID
     * @return list of transit keys
     */
    List<TransitKey> findByTeamId(UUID teamId);

    /**
     * Returns a page of transit keys for a team.
     *
     * @param teamId   the owning team ID
     * @param pageable pagination parameters
     * @return page of transit keys
     */
    Page<TransitKey> findByTeamId(UUID teamId, Pageable pageable);

    /**
     * Returns a page of active transit keys for a team.
     *
     * @param teamId   the owning team ID
     * @param pageable pagination parameters
     * @return page of active transit keys
     */
    Page<TransitKey> findByTeamIdAndIsActiveTrue(UUID teamId, Pageable pageable);

    /**
     * Checks whether a transit key exists with the given team and name.
     *
     * @param teamId the owning team ID
     * @param name   the key name
     * @return true if a key with that name exists
     */
    boolean existsByTeamIdAndName(UUID teamId, String name);

    /**
     * Counts all transit keys for a team.
     *
     * @param teamId the owning team ID
     * @return key count
     */
    long countByTeamId(UUID teamId);
}
