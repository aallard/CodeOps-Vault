package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.SecretType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core secret entity representing a single secret at a hierarchical path.
 *
 * <p>Secrets are organized in a tree structure via their {@code path} field
 * (e.g., {@code /services/talent-app/db/password}). Each secret is owned by
 * a team and tracks versioning, expiration, access, and rotation metadata.</p>
 *
 * <p>The actual encrypted values are stored in {@link SecretVersion} records —
 * every write creates a new version rather than overwriting.</p>
 */
@Entity
@Table(name = "secrets",
        uniqueConstraints = @UniqueConstraint(name = "uk_secret_team_path", columnNames = {"team_id", "path"}),
        indexes = {
                @Index(name = "idx_secret_team_id", columnList = "team_id"),
                @Index(name = "idx_secret_path", columnList = "path"),
                @Index(name = "idx_secret_type", columnList = "secret_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Secret extends BaseEntity {

    /** Owning team identifier. */
    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    /** Hierarchical path (e.g., "/services/talent-app/db/password"). */
    @Column(name = "path", nullable = false, length = 500)
    private String path;

    /** Human-readable name for the secret. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Optional description of what this secret is used for. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** The type of secret (STATIC, DYNAMIC, or REFERENCE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "secret_type", nullable = false, length = 20)
    private SecretType secretType;

    /** Current version number (starts at 1, incremented on each write). */
    @Builder.Default
    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    /** Maximum number of versions to retain (null = unlimited). */
    @Column(name = "max_versions")
    private Integer maxVersions;

    /** Days to retain old versions (null = forever). */
    @Column(name = "retention_days")
    private Integer retentionDays;

    /** Optional expiration timestamp for this secret. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Timestamp of the last time this secret's value was read. */
    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    /** Timestamp of the last successful rotation. */
    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    /** User who created or owns this secret. */
    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    /** For REFERENCE type: external store ARN or URL. */
    @Column(name = "reference_arn", length = 500)
    private String referenceArn;

    /** Extensible JSON metadata for the secret. */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    /** Soft delete flag — active secrets are visible, inactive are hidden. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** All versions of this secret's encrypted value. */
    @OneToMany(mappedBy = "secret", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SecretVersion> versions = new ArrayList<>();

    /** The rotation policy attached to this secret (if any). */
    @OneToOne(mappedBy = "secret", cascade = CascadeType.ALL, orphanRemoval = true)
    private RotationPolicy rotationPolicy;
}
