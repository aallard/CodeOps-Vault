package com.codeops.vault.entity.enums;

/**
 * Strategy used when rotating a secret's value.
 *
 * <p>Determines how the new secret value is generated during rotation.</p>
 */
public enum RotationStrategy {
    /** Generate a cryptographically random value of configured length. */
    RANDOM_GENERATE,
    /** Call an external API endpoint to obtain a new value. */
    EXTERNAL_API,
    /** Execute a custom script or command to produce a new value. */
    CUSTOM_SCRIPT
}
