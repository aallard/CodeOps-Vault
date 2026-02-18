package com.codeops.vault.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Request to update a secret's value (creates a new version) and/or metadata.
 *
 * @param value             New secret value (if provided, creates a new version).
 * @param changeDescription Description of what changed in this version.
 * @param description       Updated description (null = no change).
 * @param maxVersions       Updated max versions (null = no change).
 * @param retentionDays     Updated retention days (null = no change).
 * @param expiresAt         Updated expiration (null = no change).
 * @param metadata          Replacement metadata (null = no change, empty map = clear all).
 */
public record UpdateSecretRequest(
        String value,
        @Size(max = 500) String changeDescription,
        @Size(max = 2000) String description,
        @Min(1) @Max(1000) Integer maxVersions,
        @Min(1) Integer retentionDays,
        Instant expiresAt,
        Map<String, String> metadata
) {}
