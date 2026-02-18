package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.BindingType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to bind a policy to a user, team, or service.
 *
 * @param policyId        The policy to bind.
 * @param bindingType     Scope: USER, TEAM, or SERVICE.
 * @param bindingTargetId The userId, teamId, or serviceId to bind to.
 */
public record CreateBindingRequest(
        @NotNull UUID policyId,
        @NotNull BindingType bindingType,
        @NotNull UUID bindingTargetId
) {}
