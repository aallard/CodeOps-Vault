package com.codeops.vault.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to re-encrypt ciphertext with the current key version
 * without exposing the plaintext.
 *
 * @param keyName    Name of the transit key.
 * @param ciphertext Existing ciphertext to rewrap.
 */
public record TransitRewrapRequest(
        @NotBlank @Size(max = 200) String keyName,
        @NotBlank String ciphertext
) {}
