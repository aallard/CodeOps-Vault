package com.codeops.vault.entity.enums;

/**
 * The scope at which a policy binding applies.
 *
 * <p>Determines whether a policy is bound to an individual user,
 * an entire team, or a specific service identity.</p>
 */
public enum BindingType {
    /** Policy applies to a specific user by userId. */
    USER,
    /** Policy applies to all members of a team by teamId. */
    TEAM,
    /** Policy applies to a specific service by serviceId (from Registry). */
    SERVICE
}
