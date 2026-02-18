package com.codeops.vault.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to decrypt ciphertext using a named transit key.
 *
 * @param keyName    Name of the transit key to use.
 * @param ciphertext The encrypted data (prefixed with key version).
 */
public record TransitDecryptRequest(
        @NotBlank @Size(max = 200) String keyName,
        @NotBlank String ciphertext
) {}
