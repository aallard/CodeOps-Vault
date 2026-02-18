package com.codeops.vault.entity.enums;

/**
 * The seal state of the Vault.
 *
 * <p>Vault starts sealed and must be unsealed (by providing enough
 * Shamir key shares) before it can process secret operations. In
 * development mode, auto-unseal can be enabled.</p>
 */
public enum SealStatus {
    /** Vault is sealed — no secret operations allowed. */
    SEALED,
    /** Vault is unsealed — all operations available. */
    UNSEALED,
    /** Vault is in the process of being unsealed (shares being collected). */
    UNSEALING
}
