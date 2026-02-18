package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.BindingType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Binds an {@link AccessPolicy} to a specific user, team, or service.
 *
 * <p>Policy bindings connect policies to their targets. The {@code bindingType}
 * determines whether the target is a user, team, or service, and the
 * {@code bindingTargetId} holds the corresponding identifier.</p>
 */
@Entity
@Table(name = "policy_bindings",
        uniqueConstraints = @UniqueConstraint(name = "uk_pb_policy_type_target",
                columnNames = {"policy_id", "binding_type", "binding_target_id"}),
        indexes = {
                @Index(name = "idx_pb_policy_id", columnList = "policy_id"),
                @Index(name = "idx_pb_target", columnList = "binding_target_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyBinding extends BaseEntity {

    /** The access policy being bound. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private AccessPolicy policy;

    /** The scope of this binding (USER, TEAM, or SERVICE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false, length = 20)
    private BindingType bindingType;

    /** The userId, teamId, or serviceId this policy is bound to. */
    @Column(name = "binding_target_id", nullable = false)
    private UUID bindingTargetId;

    /** User who created this binding. */
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}
