package com.codeops.vault.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Key-value metadata pair attached to a secret.
 *
 * <p>Provides an extensible labeling and tagging system for secrets.
 * Each metadata entry is a single key-value pair, and the combination
 * of secret and key must be unique.</p>
 */
@Entity
@Table(name = "secret_metadata",
        uniqueConstraints = @UniqueConstraint(name = "uk_sm_secret_key", columnNames = {"secret_id", "metadata_key"}),
        indexes = @Index(name = "idx_sm_secret_id", columnList = "secret_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecretMetadata extends BaseEntity {

    /** The parent secret this metadata belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secret_id", nullable = false)
    private Secret secret;

    /** The metadata key name. */
    @Column(name = "metadata_key", nullable = false, length = 200)
    private String metadataKey;

    /** The metadata value. */
    @Column(name = "metadata_value", nullable = false, columnDefinition = "TEXT")
    private String metadataValue;
}
