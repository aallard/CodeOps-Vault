package com.codeops.vault.repository;

import com.codeops.vault.entity.AccessPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link AccessPolicy} entities.
 *
 * <p>Provides query methods for access policies, including team-scoped
 * lookups, active policy filtering, and uniqueness checks.</p>
 */
@Repository
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, UUID> {

    /**
     * Finds a policy by team and name.
     *
     * @param teamId the owning team ID
     * @param name   the policy name
     * @return the policy if found
     */
    Optional<AccessPolicy> findByTeamIdAndName(UUID teamId, String name);

    /**
     * Returns all policies for a team.
     *
     * @param teamId the owning team ID
     * @return list of policies
     */
    List<AccessPolicy> findByTeamId(UUID teamId);

    /**
     * Returns a page of policies for a team.
     *
     * @param teamId   the owning team ID
     * @param pageable pagination parameters
     * @return page of policies
     */
    Page<AccessPolicy> findByTeamId(UUID teamId, Pageable pageable);

    /**
     * Returns a page of active policies for a team.
     *
     * @param teamId   the owning team ID
     * @param pageable pagination parameters
     * @return page of active policies
     */
    Page<AccessPolicy> findByTeamIdAndIsActiveTrue(UUID teamId, Pageable pageable);

    /**
     * Returns all active policies for a team.
     *
     * @param teamId the owning team ID
     * @return list of active policies
     */
    List<AccessPolicy> findByTeamIdAndIsActiveTrue(UUID teamId);

    /**
     * Checks whether a policy exists with the given team and name.
     *
     * @param teamId the owning team ID
     * @param name   the policy name
     * @return true if a policy with that name exists
     */
    boolean existsByTeamIdAndName(UUID teamId, String name);

    /**
     * Counts all policies for a team.
     *
     * @param teamId the owning team ID
     * @return policy count
     */
    long countByTeamId(UUID teamId);
}
