package com.codeops.vault.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a named transit encryption key.
 *
 * @param name         Unique key name within the team.
 * @param description  Optional description.
 * @param algorithm    Encryption algorithm (default: "AES-256-GCM").
 * @param isDeletable  Whether key can be deleted (default: false for safety).
 * @param isExportable Whether key material can be exported (default: false).
 */
public record CreateTransitKeyRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 30) String algorithm,
        boolean isDeletable,
        boolean isExportable
) {}
