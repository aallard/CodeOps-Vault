package com.codeops.vault.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to revoke an active dynamic secret lease.
 *
 * @param leaseId The lease identifier to revoke.
 */
public record RevokeDynamicLeaseRequest(
        @NotBlank String leaseId
) {}
