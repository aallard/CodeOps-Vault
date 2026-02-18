package com.codeops.vault.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link AccessPolicy} entity.
 */
class AccessPolicyTest {

    private AccessPolicy buildPolicy() {
        return AccessPolicy.builder()
                .teamId(UUID.randomUUID())
                .name("test-policy")
                .pathPattern("/services/test/*")
                .permissions("READ,LIST")
                .createdByUserId(UUID.randomUUID())
                .build();
    }

    @Test
    void builder_createsWithAllFields() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AccessPolicy policy = AccessPolicy.builder()
                .teamId(teamId)
                .name("full-access")
                .description("Full access to everything")
                .pathPattern("/services/*")
                .permissions("READ,WRITE,DELETE,LIST,ROTATE")
                .isDenyPolicy(true)
                .isActive(false)
                .createdByUserId(userId)
                .build();

        assertThat(policy.getTeamId()).isEqualTo(teamId);
        assertThat(policy.getName()).isEqualTo("full-access");
        assertThat(policy.getDescription()).isEqualTo("Full access to everything");
        assertThat(policy.getPathPattern()).isEqualTo("/services/*");
        assertThat(policy.getPermissions()).isEqualTo("READ,WRITE,DELETE,LIST,ROTATE");
        assertThat(policy.getIsDenyPolicy()).isTrue();
        assertThat(policy.getIsActive()).isFalse();
        assertThat(policy.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    void builder_defaultIsDenyPolicy_isFalse() {
        AccessPolicy policy = buildPolicy();
        assertThat(policy.getIsDenyPolicy()).isFalse();
    }

    @Test
    void builder_defaultIsActive_isTrue() {
        AccessPolicy policy = buildPolicy();
        assertThat(policy.getIsActive()).isTrue();
    }

    @Test
    void permissionsString_canBeParsed() {
        AccessPolicy policy = buildPolicy();
        String[] perms = policy.getPermissions().split(",");
        assertThat(perms).containsExactly("READ", "LIST");
    }

    @Test
    void prePersist_setsTimestamps() {
        AccessPolicy policy = buildPolicy();
        policy.onCreate();

        assertThat(policy.getCreatedAt()).isNotNull();
        assertThat(policy.getUpdatedAt()).isNotNull();
    }

    @Test
    void setter_descriptionCanBeSet() {
        AccessPolicy policy = buildPolicy();
        policy.setDescription("Updated description");
        assertThat(policy.getDescription()).isEqualTo("Updated description");
    }
}
