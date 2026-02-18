package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.PolicyPermission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response representing an access policy.
 *
 * @param id              Policy unique identifier.
 * @param teamId          Owning team identifier.
 * @param name            Policy name.
 * @param description     Policy description.
 * @param pathPattern     Glob pattern for secret paths.
 * @param permissions     List of permissions granted or denied.
 * @param isDenyPolicy    Whether this policy denies the listed permissions.
 * @param isActive        Whether the policy is active.
 * @param createdByUserId User who created this policy.
 * @param bindingCount    Number of bindings attached to this policy.
 * @param createdAt       Creation timestamp.
 * @param updatedAt       Last update timestamp.
 */
public record AccessPolicyResponse(
        UUID id,
        UUID teamId,
        String name,
        String description,
        String pathPattern,
        List<PolicyPermission> permissions,
        boolean isDenyPolicy,
        boolean isActive,
        UUID createdByUserId,
        int bindingCount,
        Instant createdAt,
        Instant updatedAt
) {}
