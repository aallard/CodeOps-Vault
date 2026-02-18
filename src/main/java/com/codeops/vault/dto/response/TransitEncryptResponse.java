package com.codeops.vault.dto.response;

/**
 * Response containing encrypted ciphertext from a transit encrypt operation.
 *
 * @param keyName    Name of the transit key used.
 * @param keyVersion Version of the key used for encryption.
 * @param ciphertext The encrypted ciphertext.
 */
public record TransitEncryptResponse(
        String keyName,
        int keyVersion,
        String ciphertext
) {}
