package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.BindingType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link PolicyBinding} entity.
 */
class PolicyBindingTest {

    private PolicyBinding buildBinding(BindingType type) {
        return PolicyBinding.builder()
                .bindingType(type)
                .bindingTargetId(UUID.randomUUID())
                .createdByUserId(UUID.randomUUID())
                .build();
    }

    @Test
    void builder_createsWithAllFields() {
        UUID targetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PolicyBinding binding = PolicyBinding.builder()
                .bindingType(BindingType.USER)
                .bindingTargetId(targetId)
                .createdByUserId(userId)
                .build();

        assertThat(binding.getBindingType()).isEqualTo(BindingType.USER);
        assertThat(binding.getBindingTargetId()).isEqualTo(targetId);
        assertThat(binding.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    void bindingType_user() {
        PolicyBinding binding = buildBinding(BindingType.USER);
        assertThat(binding.getBindingType()).isEqualTo(BindingType.USER);
    }

    @Test
    void bindingType_team() {
        PolicyBinding binding = buildBinding(BindingType.TEAM);
        assertThat(binding.getBindingType()).isEqualTo(BindingType.TEAM);
    }

    @Test
    void bindingType_service() {
        PolicyBinding binding = buildBinding(BindingType.SERVICE);
        assertThat(binding.getBindingType()).isEqualTo(BindingType.SERVICE);
    }

    @Test
    void prePersist_setsTimestamps() {
        PolicyBinding binding = buildBinding(BindingType.TEAM);
        binding.onCreate();

        assertThat(binding.getCreatedAt()).isNotNull();
        assertThat(binding.getUpdatedAt()).isNotNull();
    }
}
