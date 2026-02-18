package com.codeops.vault.dto.response;

/**
 * Response containing decrypted plaintext from a transit decrypt operation.
 *
 * @param keyName   Name of the transit key used.
 * @param plaintext The decrypted plaintext.
 */
public record TransitDecryptResponse(
        String keyName,
        String plaintext
) {}
