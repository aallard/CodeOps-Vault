package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.RotationStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Scheduled rotation configuration for a specific secret.
 *
 * <p>Defines how and when a secret's value should be automatically rotated.
 * The {@code strategy} determines the rotation method, and strategy-specific
 * fields provide the required configuration (e.g., random length for
 * {@link RotationStrategy#RANDOM_GENERATE}, API URL for
 * {@link RotationStrategy#EXTERNAL_API}).</p>
 */
@Entity
@Table(name = "rotation_policies",
        indexes = {
                @Index(name = "idx_rp_next_rotation", columnList = "next_rotation_at"),
                @Index(name = "idx_rp_active", columnList = "is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotationPolicy extends BaseEntity {

    /** The secret this rotation policy applies to. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secret_id", nullable = false, unique = true)
    private Secret secret;

    /** The rotation strategy to use when generating a new value. */
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 30)
    private RotationStrategy strategy;

    /** How often to rotate the secret, in hours. */
    @Column(name = "rotation_interval_hours", nullable = false)
    private Integer rotationIntervalHours;

    /** For RANDOM_GENERATE: the character length of the generated value. */
    @Column(name = "random_length")
    private Integer randomLength;

    /** For RANDOM_GENERATE: allowed character set (e.g., "alphanumeric", "ascii-printable"). */
    @Column(name = "random_charset", length = 100)
    private String randomCharset;

    /** For EXTERNAL_API: the endpoint URL to call for a new value. */
    @Column(name = "external_api_url", length = 500)
    private String externalApiUrl;

    /** For EXTERNAL_API: JSON-encoded HTTP headers to include in the request. */
    @Column(name = "external_api_headers", columnDefinition = "TEXT")
    private String externalApiHeaders;

    /** For CUSTOM_SCRIPT: the command to execute to produce a new value. */
    @Column(name = "script_command", columnDefinition = "TEXT")
    private String scriptCommand;

    /** Timestamp of the last successful rotation. */
    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    /** Calculated next rotation time based on interval and last rotation. */
    @Column(name = "next_rotation_at")
    private Instant nextRotationAt;

    /** Whether this rotation policy is enabled. Default true. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Count of consecutive rotation failures. Default 0. */
    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    /** Pause rotation after this many consecutive failures (null = never pause). */
    @Column(name = "max_failures")
    private Integer maxFailures;
}
