package com.codeops.vault.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Utility class providing static helper methods for accessing the current Spring Security
 * authentication context.
 *
 * <p>Methods in this class read from {@link SecurityContextHolder} and expect the principal
 * to be a {@link UUID} (as set by {@link JwtAuthFilter}), with team ID stored in
 * authentication details, and authorities following the {@code ROLE_} prefix convention
 * for roles and plain strings for permissions.</p>
 *
 * @see JwtAuthFilter
 * @see SecurityConfig
 */
public final class SecurityUtils {
    private SecurityUtils() {}

    /**
     * Retrieves the UUID of the currently authenticated user from the Spring Security context.
     *
     * @return the authenticated user's UUID
     * @throws org.springframework.security.access.AccessDeniedException if no authentication
     *         is present or the principal is not a {@link UUID}
     */
    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new org.springframework.security.access.AccessDeniedException("No authenticated user");
        }
        if (!(auth.getPrincipal() instanceof UUID userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid authentication principal");
        }
        return userId;
    }

    /**
     * Retrieves the team UUID from the current authentication context.
     *
     * <p>The team ID is stored in the authentication details map by {@link JwtAuthFilter}.</p>
     *
     * @return the current team's UUID
     * @throws org.springframework.security.access.AccessDeniedException if no team context is available
     */
    @SuppressWarnings("unchecked")
    public static UUID getCurrentTeamId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object teamId = details.get("teamId");
            if (teamId instanceof UUID uuid) {
                return uuid;
            }
        }
        throw new org.springframework.security.access.AccessDeniedException("No team context available");
    }

    /**
     * Checks whether the currently authenticated user has the specified role.
     *
     * @param role the role name to check (without the {@code ROLE_} prefix)
     * @return {@code true} if the current user has the specified role, {@code false} otherwise
     */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Checks whether the currently authenticated user has the specified permission.
     *
     * @param permission the permission string to check (e.g., {@code "vault:read"})
     * @return {@code true} if the current user has the specified permission, {@code false} otherwise
     */
    public static boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }
}
