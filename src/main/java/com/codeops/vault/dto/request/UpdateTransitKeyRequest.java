package com.codeops.vault.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request to update a transit key's metadata. Key material is not directly updatable.
 *
 * @param description         Updated description.
 * @param minDecryptionVersion Updated minimum decryption version.
 * @param isDeletable         Updated deletable flag.
 * @param isExportable        Updated exportable flag.
 * @param isActive            Updated active status.
 */
public record UpdateTransitKeyRequest(
        @Size(max = 2000) String description,
        @Min(1) Integer minDecryptionVersion,
        Boolean isDeletable,
        Boolean isExportable,
        Boolean isActive
) {}
