package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.SealStatus;

import java.time.Instant;

/**
 * Response representing the current seal status of the Vault.
 *
 * @param status            Current seal state (SEALED, UNSEALED, UNSEALING).
 * @param totalShares       Total number of Shamir key shares.
 * @param threshold         Number of shares required to unseal.
 * @param sharesProvided    Number of valid shares provided so far.
 * @param autoUnsealEnabled Whether auto-unseal is enabled.
 * @param sealedAt          When the Vault was last sealed.
 * @param unsealedAt        When the Vault was last unsealed.
 */
public record SealStatusResponse(
        SealStatus status,
        int totalShares,
        int threshold,
        int sharesProvided,
        boolean autoUnsealEnabled,
        Instant sealedAt,
        Instant unsealedAt
) {}
