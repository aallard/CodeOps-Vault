package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the {@link Secret} entity.
 */
class SecretTest {

    private Secret buildSecret() {
        return Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/services/test-app/db/password")
                .name("Test Secret")
                .description("A test secret")
                .secretType(SecretType.STATIC)
                .ownerUserId(UUID.randomUUID())
                .build();
    }

    @Test
    void builder_createsSecretWithAllFields() {
        UUID teamId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Secret secret = Secret.builder()
                .teamId(teamId)
                .path("/services/app/key")
                .name("App Key")
                .description("An app key")
                .secretType(SecretType.DYNAMIC)
                .currentVersion(3)
                .maxVersions(10)
                .retentionDays(90)
                .ownerUserId(ownerId)
                .referenceArn("arn:aws:secretsmanager:us-east-1:123456:secret:my-secret")
                .metadataJson("{\"env\":\"prod\"}")
                .build();

        assertThat(secret.getTeamId()).isEqualTo(teamId);
        assertThat(secret.getPath()).isEqualTo("/services/app/key");
        assertThat(secret.getName()).isEqualTo("App Key");
        assertThat(secret.getDescription()).isEqualTo("An app key");
        assertThat(secret.getSecretType()).isEqualTo(SecretType.DYNAMIC);
        assertThat(secret.getCurrentVersion()).isEqualTo(3);
        assertThat(secret.getMaxVersions()).isEqualTo(10);
        assertThat(secret.getRetentionDays()).isEqualTo(90);
        assertThat(secret.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(secret.getReferenceArn()).isEqualTo("arn:aws:secretsmanager:us-east-1:123456:secret:my-secret");
        assertThat(secret.getMetadataJson()).isEqualTo("{\"env\":\"prod\"}");
    }

    @Test
    void builder_defaultCurrentVersion_isOne() {
        Secret secret = buildSecret();
        assertThat(secret.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void builder_defaultIsActive_isTrue() {
        Secret secret = buildSecret();
        assertThat(secret.getIsActive()).isTrue();
    }

    @Test
    void builder_defaultVersionsList_isEmpty() {
        Secret secret = buildSecret();
        assertThat(secret.getVersions()).isNotNull().isEmpty();
    }

    @Test
    void builder_defaultRotationPolicy_isNull() {
        Secret secret = buildSecret();
        assertThat(secret.getRotationPolicy()).isNull();
    }

    @Test
    void setters_modifyFields() {
        Secret secret = buildSecret();
        Instant now = Instant.now();
        secret.setExpiresAt(now);
        secret.setLastAccessedAt(now);
        secret.setLastRotatedAt(now);
        secret.setIsActive(false);

        assertThat(secret.getExpiresAt()).isEqualTo(now);
        assertThat(secret.getLastAccessedAt()).isEqualTo(now);
        assertThat(secret.getLastRotatedAt()).isEqualTo(now);
        assertThat(secret.getIsActive()).isFalse();
    }

    @Test
    void prePersist_setsTimestamps() {
        Secret secret = buildSecret();
        secret.onCreate();

        assertThat(secret.getCreatedAt()).isNotNull();
        assertThat(secret.getUpdatedAt()).isNotNull();
        assertThat(secret.getCreatedAt()).isCloseTo(secret.getUpdatedAt(), within(1, java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void preUpdate_updatesTimestamp() {
        Secret secret = buildSecret();
        secret.onCreate();
        Instant createdAt = secret.getCreatedAt();

        secret.onUpdate();

        assertThat(secret.getCreatedAt()).isEqualTo(createdAt);
        assertThat(secret.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
