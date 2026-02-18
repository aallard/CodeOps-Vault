package com.codeops.vault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Named access control policy with a path pattern and set of permissions.
 *
 * <p>Policies define what operations are allowed (or denied) on secrets at paths
 * matching the policy's {@code pathPattern}. Wildcard matching is supported
 * (e.g., {@code /services/talent-app/*}). When {@code isDenyPolicy} is true,
 * the listed permissions are denied rather than granted, and deny always
 * overrides allow.</p>
 */
@Entity
@Table(name = "access_policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_ap_team_name", columnNames = {"team_id", "name"}),
        indexes = @Index(name = "idx_ap_team_id", columnList = "team_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessPolicy extends BaseEntity {

    /** Owning team identifier. */
    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    /** Policy name (e.g., "talent-app-readonly"). */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Optional description of the policy's purpose. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Glob pattern for matching secret paths (e.g., "/services/talent-app/*"). */
    @Column(name = "path_pattern", nullable = false, length = 500)
    private String pathPattern;

    /** Comma-separated PolicyPermission values (e.g., "READ,LIST"). */
    @Column(name = "permissions", nullable = false, length = 200)
    private String permissions;

    /** If true, this policy denies the listed permissions. Default false (allow). */
    @Builder.Default
    @Column(name = "is_deny_policy", nullable = false)
    private Boolean isDenyPolicy = false;

    /** Soft delete flag — active policies are enforced, inactive are ignored. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** User who created this policy. */
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}
