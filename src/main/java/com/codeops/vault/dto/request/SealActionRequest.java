package com.codeops.vault.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to perform a seal or unseal action.
 *
 * @param action   "seal" or "unseal".
 * @param keyShare For unseal: one of the Shamir key shares.
 */
public record SealActionRequest(
        @NotBlank @Pattern(regexp = "^(seal|unseal)$") String action,
        String keyShare
) {}
