package com.codeops.vault.repository;

import com.codeops.vault.entity.PolicyBinding;
import com.codeops.vault.entity.enums.BindingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PolicyBinding} entities.
 *
 * <p>Provides query methods for policy bindings, including target-based
 * lookups, active binding resolution, and uniqueness checks.</p>
 */
@Repository
public interface PolicyBindingRepository extends JpaRepository<PolicyBinding, UUID> {

    /**
     * Returns all bindings for a specific policy.
     *
     * @param policyId the policy ID
     * @return list of bindings
     */
    List<PolicyBinding> findByPolicyId(UUID policyId);

    /**
     * Returns all bindings of a given type for a target.
     *
     * @param bindingType the binding scope (USER, TEAM, SERVICE)
     * @param targetId    the target identifier
     * @return list of matching bindings
     */
    List<PolicyBinding> findByBindingTypeAndBindingTargetId(BindingType bindingType, UUID targetId);

    /**
     * Finds a specific binding by policy, type, and target.
     *
     * @param policyId    the policy ID
     * @param bindingType the binding scope
     * @param targetId    the target identifier
     * @return the binding if found
     */
    Optional<PolicyBinding> findByPolicyIdAndBindingTypeAndBindingTargetId(UUID policyId, BindingType bindingType, UUID targetId);

    /**
     * Checks whether a binding exists for the given policy, type, and target.
     *
     * @param policyId    the policy ID
     * @param bindingType the binding scope
     * @param targetId    the target identifier
     * @return true if the binding exists
     */
    boolean existsByPolicyIdAndBindingTypeAndBindingTargetId(UUID policyId, BindingType bindingType, UUID targetId);

    /**
     * Deletes all bindings for a policy.
     *
     * @param policyId the policy ID
     */
    void deleteByPolicyId(UUID policyId);

    /**
     * Counts all bindings for a policy.
     *
     * @param policyId the policy ID
     * @return binding count
     */
    long countByPolicyId(UUID policyId);

    /**
     * Finds all active policy bindings for a target within a team.
     *
     * <p>Only returns bindings where the associated policy is active.</p>
     *
     * @param teamId      the team ID
     * @param bindingType the binding scope
     * @param targetId    the target identifier
     * @return list of active bindings
     */
    @Query("SELECT pb FROM PolicyBinding pb JOIN pb.policy p WHERE p.teamId = :teamId AND p.isActive = true AND pb.bindingType = :bindingType AND pb.bindingTargetId = :targetId")
    List<PolicyBinding> findActiveBindingsForTarget(@Param("teamId") UUID teamId, @Param("bindingType") BindingType bindingType, @Param("targetId") UUID targetId);
}
