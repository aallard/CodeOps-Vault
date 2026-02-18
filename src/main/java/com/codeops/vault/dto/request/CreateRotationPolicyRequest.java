package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.RotationStrategy;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Request to create or update a rotation policy for a secret.
 *
 * @param secretId              The secret to rotate.
 * @param strategy              Rotation strategy to use.
 * @param rotationIntervalHours How often to rotate (in hours).
 * @param randomLength          For RANDOM_GENERATE: character length.
 * @param randomCharset         For RANDOM_GENERATE: allowed characters.
 * @param externalApiUrl        For EXTERNAL_API: endpoint URL.
 * @param externalApiHeaders    For EXTERNAL_API: JSON headers string.
 * @param scriptCommand         For CUSTOM_SCRIPT: command to execute.
 * @param maxFailures           Max consecutive failures before pausing.
 */
public record CreateRotationPolicyRequest(
        @NotNull UUID secretId,
        @NotNull RotationStrategy strategy,
        @NotNull @Min(1) Integer rotationIntervalHours,
        @Min(8) @Max(1024) Integer randomLength,
        @Size(max = 100) String randomCharset,
        @Size(max = 500) String externalApiUrl,
        String externalApiHeaders,
        String scriptCommand,
        @Min(1) @Max(100) Integer maxFailures
) {}
