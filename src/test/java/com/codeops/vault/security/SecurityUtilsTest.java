package com.codeops.vault.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(UUID userId, UUID teamId, List<String> authorities) {
        List<SimpleGrantedAuthority> auths = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, auths);
        Map<String, Object> details = new HashMap<>();
        if (teamId != null) {
            details.put("teamId", teamId);
        }
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getCurrentUserId_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        setAuthentication(userId, UUID.randomUUID(), List.of("ROLE_MEMBER"));
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    void getCurrentTeamId_returnsCorrectUUID() {
        UUID teamId = UUID.randomUUID();
        setAuthentication(UUID.randomUUID(), teamId, List.of("ROLE_MEMBER"));
        assertThat(SecurityUtils.getCurrentTeamId()).isEqualTo(teamId);
    }

    @Test
    void hasRole_returnsTrue_whenRoleExists() {
        setAuthentication(UUID.randomUUID(), UUID.randomUUID(), List.of("ROLE_ADMIN"));
        assertThat(SecurityUtils.hasRole("ADMIN")).isTrue();
    }

    @Test
    void hasPermission_returnsTrue_whenPermissionExists() {
        setAuthentication(UUID.randomUUID(), UUID.randomUUID(), List.of("vault:read", "ROLE_MEMBER"));
        assertThat(SecurityUtils.hasPermission("vault:read")).isTrue();
    }
}
