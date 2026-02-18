package com.codeops.vault.service;

/**
 * A single version of a transit key's material.
 *
 * <p>Stored as part of the encrypted key material JSON array within
 * a {@link com.codeops.vault.entity.TransitKey} entity.</p>
 *
 * @param version The version number (1-based).
 * @param key     Base64-encoded raw key material (32 bytes for AES-256).
 */
public record KeyVersion(
        int version,
        String key
) {}
