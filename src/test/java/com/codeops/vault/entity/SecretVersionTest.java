package com.codeops.vault.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link SecretVersion} entity.
 */
class SecretVersionTest {

    private SecretVersion buildVersion() {
        return SecretVersion.builder()
                .versionNumber(1)
                .encryptedValue("SEED:dGVzdA==")
                .createdByUserId(UUID.randomUUID())
                .build();
    }

    @Test
    void builder_createsVersionWithAllFields() {
        UUID userId = UUID.randomUUID();
        SecretVersion version = SecretVersion.builder()
                .versionNumber(3)
                .encryptedValue("SEED:c2VjcmV0")
                .encryptionKeyId("key-v2")
                .changeDescription("Rotated value")
                .createdByUserId(userId)
                .build();

        assertThat(version.getVersionNumber()).isEqualTo(3);
        assertThat(version.getEncryptedValue()).isEqualTo("SEED:c2VjcmV0");
        assertThat(version.getEncryptionKeyId()).isEqualTo("key-v2");
        assertThat(version.getChangeDescription()).isEqualTo("Rotated value");
        assertThat(version.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    void builder_defaultIsDestroyed_isFalse() {
        SecretVersion version = buildVersion();
        assertThat(version.getIsDestroyed()).isFalse();
    }

    @Test
    void setter_isDestroyedCanBeSetTrue() {
        SecretVersion version = buildVersion();
        version.setIsDestroyed(true);
        assertThat(version.getIsDestroyed()).isTrue();
    }

    @Test
    void prePersist_setsTimestamps() {
        SecretVersion version = buildVersion();
        version.onCreate();

        assertThat(version.getCreatedAt()).isNotNull();
        assertThat(version.getUpdatedAt()).isNotNull();
    }

    @Test
    void setter_secretCanBeSet() {
        SecretVersion version = buildVersion();
        Secret secret = Secret.builder().teamId(UUID.randomUUID()).path("/test").name("test").build();
        version.setSecret(secret);
        assertThat(version.getSecret()).isEqualTo(secret);
    }

    @Test
    void builder_optionalFieldsDefaultToNull() {
        SecretVersion version = buildVersion();
        assertThat(version.getEncryptionKeyId()).isNull();
        assertThat(version.getChangeDescription()).isNull();
    }
}
