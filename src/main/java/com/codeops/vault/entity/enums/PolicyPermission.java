package com.codeops.vault.entity.enums;

/**
 * Permissions that can be granted or denied in a Vault access policy.
 *
 * <p>These permissions control what operations are allowed on secrets
 * at paths matching the policy's path pattern.</p>
 */
public enum PolicyPermission {
    /** Read the current or historical value of a secret. */
    READ,
    /** Create or update a secret (creates new version). */
    WRITE,
    /** Permanently delete a secret and all its versions. */
    DELETE,
    /** List secrets and paths under a given prefix. */
    LIST,
    /** Trigger rotation of a secret's value. */
    ROTATE
}
