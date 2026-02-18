package com.codeops.vault.service;

/**
 * A generated data encryption key returned in both plaintext and encrypted form.
 *
 * <p>Used by the transit "generate data key" operation. The caller receives
 * both forms: the plaintext key for immediate use in their own encryption,
 * and the encrypted key for storage (to be decrypted later via transit decrypt).</p>
 *
 * @param plaintextKey Base64-encoded plaintext key material.
 * @param encryptedKey Base64-encoded encrypted key envelope.
 */
public record GeneratedDataKey(
        String plaintextKey,
        String encryptedKey
) {}
