package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.RotationStrategy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request to update a rotation policy. All fields optional.
 *
 * @param strategy              Updated rotation strategy.
 * @param rotationIntervalHours Updated rotation interval.
 * @param randomLength          Updated random length for RANDOM_GENERATE.
 * @param randomCharset         Updated random charset for RANDOM_GENERATE.
 * @param externalApiUrl        Updated external API URL.
 * @param externalApiHeaders    Updated external API headers.
 * @param scriptCommand         Updated script command.
 * @param maxFailures           Updated max failures.
 * @param isActive              Updated active status.
 */
public record UpdateRotationPolicyRequest(
        RotationStrategy strategy,
        @Min(1) Integer rotationIntervalHours,
        @Min(8) @Max(1024) Integer randomLength,
        @Size(max = 100) String randomCharset,
        @Size(max = 500) String externalApiUrl,
        String externalApiHeaders,
        String scriptCommand,
        @Min(1) @Max(100) Integer maxFailures,
        Boolean isActive
) {}
