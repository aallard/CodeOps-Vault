package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.PolicyPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request to create a new access policy.
 *
 * @param name         Unique policy name within the team.
 * @param description  Optional description.
 * @param pathPattern  Glob pattern for secret paths (e.g., "/services/talent-app/*").
 * @param permissions  List of permissions to grant/deny.
 * @param isDenyPolicy Whether this policy denies (true) or allows (false) the permissions.
 */
public record CreatePolicyRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 500) String pathPattern,
        @NotEmpty List<PolicyPermission> permissions,
        boolean isDenyPolicy
) {}
