package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.RotationStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Audit record of a rotation attempt (success or failure).
 *
 * <p>Each time a secret rotation is attempted, a history record is created
 * regardless of outcome. The {@code secretId} and {@code secretPath} are
 * stored as plain values (not foreign keys) so history is preserved even
 * if the secret is later deleted.</p>
 */
@Entity
@Table(name = "rotation_history",
        indexes = {
                @Index(name = "idx_rh_secret_id", columnList = "secret_id"),
                @Index(name = "idx_rh_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotationHistory extends BaseEntity {

    /** ID of the rotated secret (not a FK — preserves history if secret is deleted). */
    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    /** Path of the secret at the time of rotation (denormalized for history). */
    @Column(name = "secret_path", nullable = false, length = 500)
    private String secretPath;

    /** The rotation strategy that was used. */
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 30)
    private RotationStrategy strategy;

    /** Secret version number before rotation. */
    @Column(name = "previous_version")
    private Integer previousVersion;

    /** Secret version number after rotation (null on failure). */
    @Column(name = "new_version")
    private Integer newVersion;

    /** Whether the rotation succeeded. */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /** Error details if the rotation failed. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Rotation execution time in milliseconds. */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** User who triggered the rotation (null = scheduled/automatic). */
    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;
}
