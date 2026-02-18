package com.codeops.vault.entity.enums;

/**
 * Lifecycle status of a dynamic secret lease.
 *
 * <p>Dynamic secrets (e.g., short-lived database credentials) have a
 * lease-based lifecycle with automatic expiry.</p>
 */
public enum LeaseStatus {
    /** Lease is active and credentials are valid. */
    ACTIVE,
    /** Lease has expired naturally (TTL elapsed). */
    EXPIRED,
    /** Lease was explicitly revoked before TTL. */
    REVOKED
}
