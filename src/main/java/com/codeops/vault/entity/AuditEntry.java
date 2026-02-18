package com.codeops.vault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry for every Vault operation.
 *
 * <p>Every operation performed against the Vault (reads, writes, deletes,
 * policy changes, seal/unseal, transit operations, etc.) is recorded as an
 * audit entry. This entity does NOT extend {@link BaseEntity} — it uses a
 * {@code Long} auto-increment primary key for write performance on high-volume
 * audit logging.</p>
 */
@Entity
@Table(name = "vault_audit_log",
        indexes = {
                @Index(name = "idx_val_team_id", columnList = "team_id"),
                @Index(name = "idx_val_user_id", columnList = "user_id"),
                @Index(name = "idx_val_operation", columnList = "operation"),
                @Index(name = "idx_val_path", columnList = "path"),
                @Index(name = "idx_val_created_at", columnList = "created_at"),
                @Index(name = "idx_val_resource", columnList = "resource_type, resource_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntry {

    /** Auto-increment primary key for write performance. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Team context (nullable for system-level operations). */
    @Column(name = "team_id")
    private UUID teamId;

    /** Acting user (nullable for system/scheduled operations). */
    @Column(name = "user_id")
    private UUID userId;

    /** The operation performed (e.g., READ, WRITE, DELETE, ROTATE, SEAL, UNSEAL). */
    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    /** Secret path or resource identifier. */
    @Column(name = "path", length = 500)
    private String path;

    /** Type of resource affected (SECRET, POLICY, BINDING, ROTATION, LEASE, TRANSIT_KEY, SEAL). */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /** ID of the affected resource. */
    @Column(name = "resource_id")
    private UUID resourceId;

    /** Whether the operation succeeded. */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /** Error details on failure. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Client IP address. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Request correlation ID from MDC. */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** Additional JSON context (version numbers, policy changes, etc.). */
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    /** Timestamp of the audit entry. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Sets the creation timestamp before persisting.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
