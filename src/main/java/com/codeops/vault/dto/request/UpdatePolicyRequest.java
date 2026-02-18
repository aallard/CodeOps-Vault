package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.PolicyPermission;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request to update an existing access policy.
 * All fields are optional — only non-null fields are applied.
 *
 * @param name         Updated policy name.
 * @param description  Updated description.
 * @param pathPattern  Updated path pattern.
 * @param permissions  Updated permissions list.
 * @param isDenyPolicy Updated deny/allow flag.
 * @param isActive     Updated active status.
 */
public record UpdatePolicyRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) String pathPattern,
        List<PolicyPermission> permissions,
        Boolean isDenyPolicy,
        Boolean isActive
) {}
