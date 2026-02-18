package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.BindingType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response representing a policy binding.
 *
 * @param id              Binding unique identifier.
 * @param policyId        The bound policy's identifier.
 * @param policyName      The bound policy's name.
 * @param bindingType     Scope of the binding (USER, TEAM, SERVICE).
 * @param bindingTargetId The target user, team, or service identifier.
 * @param createdByUserId User who created this binding.
 * @param createdAt       Creation timestamp.
 */
public record PolicyBindingResponse(
        UUID id,
        UUID policyId,
        String policyName,
        BindingType bindingType,
        UUID bindingTargetId,
        UUID createdByUserId,
        Instant createdAt
) {}
