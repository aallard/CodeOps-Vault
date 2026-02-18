package com.codeops.vault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Immutable versioned record of a secret's encrypted value.
 *
 * <p>Every write to a secret creates a new version — secret values are never
 * overwritten in place. Old versions can be retained for rollback or destroyed
 * permanently by setting {@code isDestroyed} to true.</p>
 */
@Entity
@Table(name = "secret_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sv_secret_version", columnNames = {"secret_id", "version_number"}),
        indexes = {
                @Index(name = "idx_sv_secret_id", columnList = "secret_id"),
                @Index(name = "idx_sv_version", columnList = "version_number")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecretVersion extends BaseEntity {

    /** The parent secret this version belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secret_id", nullable = false)
    private Secret secret;

    /** Sequential version number (1, 2, 3, ...). */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** AES-256-GCM encrypted secret value. */
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    /** Identifier of the encryption key used (for key rotation tracking). */
    @Column(name = "encryption_key_id", length = 100)
    private String encryptionKeyId;

    /** Description of what changed in this version. */
    @Column(name = "change_description", length = 500)
    private String changeDescription;

    /** User who created this version. */
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    /** If true, the encrypted value has been permanently zeroed and is unrecoverable. */
    @Builder.Default
    @Column(name = "is_destroyed", nullable = false)
    private Boolean isDestroyed = false;
}
