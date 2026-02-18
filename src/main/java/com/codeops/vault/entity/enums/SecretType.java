package com.codeops.vault.entity.enums;

/**
 * The type of a secret stored in the Vault.
 *
 * <p>Determines how the secret value is stored and retrieved:</p>
 * <ul>
 *   <li>{@link #STATIC} — A standard key-value secret stored encrypted in Vault</li>
 *   <li>{@link #DYNAMIC} — Generated on demand with a TTL lease (e.g., database credentials)</li>
 *   <li>{@link #REFERENCE} — A pointer to an external secret store (e.g., AWS Secrets Manager ARN)</li>
 * </ul>
 */
public enum SecretType {
    STATIC,
    DYNAMIC,
    REFERENCE
}
