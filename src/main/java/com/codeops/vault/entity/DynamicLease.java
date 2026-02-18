package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.LeaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a short-lived lease for dynamically generated credentials.
 *
 * <p>Dynamic secrets (e.g., temporary database credentials) are issued with
 * a time-to-live (TTL). The lease tracks the credential lifecycle from
 * creation through expiry or revocation. Credentials are stored encrypted
 * and are automatically invalidated when the lease expires.</p>
 */
@Entity
@Table(name = "dynamic_leases",
        indexes = {
                @Index(name = "idx_dl_lease_id", columnList = "lease_id"),
                @Index(name = "idx_dl_secret_id", columnList = "secret_id"),
                @Index(name = "idx_dl_status", columnList = "status"),
                @Index(name = "idx_dl_expires_at", columnList = "expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicLease extends BaseEntity {

    /** Unique lease identifier (UUID-based string). */
    @Column(name = "lease_id", nullable = false, unique = true, length = 100)
    private String leaseId;

    /** ID of the source secret that generated this lease. */
    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    /** Path of the source secret (denormalized for convenience). */
    @Column(name = "secret_path", nullable = false, length = 500)
    private String secretPath;

    /** Backend type (e.g., "postgresql", "mysql"). */
    @Column(name = "backend_type", nullable = false, length = 50)
    private String backendType;

    /** Encrypted JSON containing the generated credentials. */
    @Column(name = "credentials", nullable = false, columnDefinition = "TEXT")
    private String credentials;

    /** Current lifecycle status of this lease. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaseStatus status;

    /** Lease duration in seconds. */
    @Column(name = "ttl_seconds", nullable = false)
    private Integer ttlSeconds;

    /** When this lease expires. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** When this lease was revoked (if status is REVOKED). */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** User who revoked the lease (if manual revocation). */
    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    /** User who requested the lease. */
    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    /** JSON metadata (connection details, hostname, port, database). */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}
