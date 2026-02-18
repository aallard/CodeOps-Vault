package com.codeops.vault.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to encrypt plaintext using a named transit key.
 *
 * @param keyName   Name of the transit key to use.
 * @param plaintext Base64-encoded plaintext data.
 */
public record TransitEncryptRequest(
        @NotBlank @Size(max = 200) String keyName,
        @NotBlank String plaintext
) {}
