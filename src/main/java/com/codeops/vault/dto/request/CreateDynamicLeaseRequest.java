package com.codeops.vault.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to generate a dynamic secret lease (e.g., short-lived DB credentials).
 *
 * @param secretId   The dynamic secret to generate credentials from.
 * @param ttlSeconds Requested lease duration in seconds (60–86400).
 */
public record CreateDynamicLeaseRequest(
        @NotNull UUID secretId,
        @NotNull @Min(60) @Max(86400) Integer ttlSeconds
) {}
