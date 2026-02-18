package com.codeops.vault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Named encryption key for the transit encryption engine.
 *
 * <p>Transit keys provide encrypt/decrypt-as-a-service functionality. Data is
 * encrypted using the named key and can be decrypted without the caller ever
 * having access to the raw key material. Keys support versioning for rotation
 * without requiring re-encryption of existing data.</p>
 */
@Entity
@Table(name = "transit_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_tk_team_name", columnNames = {"team_id", "name"}),
        indexes = @Index(name = "idx_tk_team_id", columnList = "team_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitKey extends BaseEntity {

    /** Owning team identifier. */
    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    /** Key name (e.g., "payment-data-key"). */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Optional description of the key's purpose. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Current key version number (starts at 1). */
    @Builder.Default
    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    /** Oldest key version allowed for decryption. Default 1. */
    @Builder.Default
    @Column(name = "min_decryption_version", nullable = false)
    private Integer minDecryptionVersion = 1;

    /** Encrypted key material (JSON array of versioned keys). */
    @Column(name = "key_material", nullable = false, columnDefinition = "TEXT")
    private String keyMaterial;

    /** Encryption algorithm (e.g., "AES-256-GCM"). */
    @Column(name = "algorithm", nullable = false, length = 30)
    private String algorithm;

    /** Safety flag — if false, key cannot be deleted. Default false. */
    @Builder.Default
    @Column(name = "is_deletable", nullable = false)
    private Boolean isDeletable = false;

    /** Whether key material can be exported. Default false. */
    @Builder.Default
    @Column(name = "is_exportable", nullable = false)
    private Boolean isExportable = false;

    /** Soft delete flag — active keys are available, inactive are hidden. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** User who created this transit key. */
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}
