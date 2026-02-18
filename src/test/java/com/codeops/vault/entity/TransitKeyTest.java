package com.codeops.vault.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link TransitKey} entity.
 */
class TransitKeyTest {

    private TransitKey buildKey() {
        return TransitKey.builder()
                .teamId(UUID.randomUUID())
                .name("test-key")
                .keyMaterial("SEED:a2V5LW1hdGVyaWFs")
                .algorithm("AES-256-GCM")
                .createdByUserId(UUID.randomUUID())
                .build();
    }

    @Test
    void builder_createsWithAllFields() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TransitKey key = TransitKey.builder()
                .teamId(teamId)
                .name("payment-key")
                .description("Key for payment data")
                .currentVersion(3)
                .minDecryptionVersion(2)
                .keyMaterial("SEED:a2V5")
                .algorithm("AES-256-GCM")
                .isDeletable(true)
                .isExportable(true)
                .isActive(false)
                .createdByUserId(userId)
                .build();

        assertThat(key.getTeamId()).isEqualTo(teamId);
        assertThat(key.getName()).isEqualTo("payment-key");
        assertThat(key.getDescription()).isEqualTo("Key for payment data");
        assertThat(key.getCurrentVersion()).isEqualTo(3);
        assertThat(key.getMinDecryptionVersion()).isEqualTo(2);
        assertThat(key.getAlgorithm()).isEqualTo("AES-256-GCM");
        assertThat(key.getIsDeletable()).isTrue();
        assertThat(key.getIsExportable()).isTrue();
        assertThat(key.getIsActive()).isFalse();
        assertThat(key.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    void builder_defaultCurrentVersion_isOne() {
        TransitKey key = buildKey();
        assertThat(key.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void builder_defaultMinDecryptionVersion_isOne() {
        TransitKey key = buildKey();
        assertThat(key.getMinDecryptionVersion()).isEqualTo(1);
    }

    @Test
    void builder_defaultIsDeletable_isFalse() {
        TransitKey key = buildKey();
        assertThat(key.getIsDeletable()).isFalse();
    }

    @Test
    void builder_defaultIsExportable_isFalse() {
        TransitKey key = buildKey();
        assertThat(key.getIsExportable()).isFalse();
    }

    @Test
    void builder_defaultIsActive_isTrue() {
        TransitKey key = buildKey();
        assertThat(key.getIsActive()).isTrue();
    }
}
