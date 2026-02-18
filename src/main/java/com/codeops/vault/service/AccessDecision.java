package com.codeops.vault.service;

import java.util.UUID;

/**
 * Result of a policy evaluation for a specific access request.
 *
 * <p>Contains the decision (ALLOWED or DENIED), the reason for the decision,
 * and the policy that determined the outcome (if any).</p>
 *
 * @param allowed            Whether access is granted.
 * @param reason             Human-readable explanation of the decision.
 * @param decidingPolicyId   The ID of the policy that determined the outcome (null if default deny).
 * @param decidingPolicyName The name of the deciding policy (null if default deny).
 */
public record AccessDecision(
        boolean allowed,
        String reason,
        UUID decidingPolicyId,
        String decidingPolicyName
) {

    /**
     * Creates an ALLOWED decision referencing the policy that granted access.
     *
     * @param policyId   The ID of the allowing policy.
     * @param policyName The name of the allowing policy.
     * @return An ALLOWED AccessDecision.
     */
    public static AccessDecision allowed(UUID policyId, String policyName) {
        return new AccessDecision(true, "Allowed by policy: " + policyName, policyId, policyName);
    }

    /**
     * Creates a DENIED decision due to an explicit deny policy.
     *
     * @param policyId   The ID of the denying policy.
     * @param policyName The name of the denying policy.
     * @return A DENIED AccessDecision.
     */
    public static AccessDecision denied(UUID policyId, String policyName) {
        return new AccessDecision(false, "Denied by policy: " + policyName, policyId, policyName);
    }

    /**
     * Creates a DENIED decision due to no matching allow policy (default deny).
     *
     * @return A default DENIED AccessDecision with no deciding policy.
     */
    public static AccessDecision defaultDenied() {
        return new AccessDecision(false, "No matching allow policy found", null, null);
    }
}
