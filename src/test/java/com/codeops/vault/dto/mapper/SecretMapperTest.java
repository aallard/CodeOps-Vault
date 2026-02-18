package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.SecretResponse;
import com.codeops.vault.dto.response.SecretVersionResponse;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.SecretVersion;
import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecretMapper} MapStruct implementation.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecretMapperTest {

    @Autowired
    private SecretMapper secretMapper;

    @Test
    void toResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        Instant now = Instant.now();

        Secret secret = Secret.builder()
                .teamId(teamId)
                .path("/services/app/db")
                .name("DB Password")
                .description("Database password for app")
                .secretType(SecretType.STATIC)
                .currentVersion(3)
                .maxVersions(100)
                .retentionDays(30)
                .expiresAt(now.plusSeconds(86400))
                .lastAccessedAt(now.minusSeconds(60))
                .lastRotatedAt(now.minusSeconds(3600))
                .ownerUserId(ownerUserId)
                .isActive(true)
                .build();
        secret.setId(id);
        secret.setCreatedAt(now);
        secret.setUpdatedAt(now);

        Map<String, String> metadata = Map.of("env", "prod", "region", "us-east-1");

        SecretResponse response = secretMapper.toResponse(secret, metadata);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.path()).isEqualTo("/services/app/db");
        assertThat(response.name()).isEqualTo("DB Password");
        assertThat(response.description()).isEqualTo("Database password for app");
        assertThat(response.secretType()).isEqualTo(SecretType.STATIC);
        assertThat(response.currentVersion()).isEqualTo(3);
        assertThat(response.maxVersions()).isEqualTo(100);
        assertThat(response.retentionDays()).isEqualTo(30);
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.isActive()).isTrue();
        assertThat(response.metadata()).isEqualTo(metadata);
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);
    }

    @Test
    void toResponse_nullMetadata_mapsCorrectly() {
        Secret secret = Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/path")
                .name("name")
                .secretType(SecretType.STATIC)
                .currentVersion(1)
                .isActive(true)
                .build();
        secret.setId(UUID.randomUUID());
        secret.setCreatedAt(Instant.now());
        secret.setUpdatedAt(Instant.now());

        SecretResponse response = secretMapper.toResponse(secret, null);

        assertThat(response.metadata()).isNull();
    }

    @Test
    void toVersionResponse_mapsAllFields() {
        UUID versionId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID createdByUserId = UUID.randomUUID();
        Instant now = Instant.now();

        Secret secret = Secret.builder().build();
        secret.setId(secretId);

        SecretVersion version = SecretVersion.builder()
                .secret(secret)
                .versionNumber(2)
                .encryptedValue("encrypted-data")
                .encryptionKeyId("key-v1")
                .changeDescription("Updated password")
                .createdByUserId(createdByUserId)
                .isDestroyed(false)
                .build();
        version.setId(versionId);
        version.setCreatedAt(now);

        SecretVersionResponse response = secretMapper.toVersionResponse(version);

        assertThat(response.id()).isEqualTo(versionId);
        assertThat(response.secretId()).isEqualTo(secretId);
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.encryptionKeyId()).isEqualTo("key-v1");
        assertThat(response.changeDescription()).isEqualTo("Updated password");
        assertThat(response.createdByUserId()).isEqualTo(createdByUserId);
        assertThat(response.isDestroyed()).isFalse();
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void toVersionResponses_mapsList() {
        Secret secret = Secret.builder().build();
        secret.setId(UUID.randomUUID());

        SecretVersion v1 = SecretVersion.builder()
                .secret(secret).versionNumber(1).encryptedValue("enc1").isDestroyed(false).build();
        v1.setId(UUID.randomUUID());
        v1.setCreatedAt(Instant.now());

        SecretVersion v2 = SecretVersion.builder()
                .secret(secret).versionNumber(2).encryptedValue("enc2").isDestroyed(false).build();
        v2.setId(UUID.randomUUID());
        v2.setCreatedAt(Instant.now());

        List<SecretVersionResponse> responses = secretMapper.toVersionResponses(List.of(v1, v2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).versionNumber()).isEqualTo(1);
        assertThat(responses.get(1).versionNumber()).isEqualTo(2);
    }

    @Test
    void toVersionResponse_destroyedVersion_flagPreserved() {
        Secret secret = Secret.builder().build();
        secret.setId(UUID.randomUUID());

        SecretVersion version = SecretVersion.builder()
                .secret(secret).versionNumber(1).encryptedValue("zeroed").isDestroyed(true).build();
        version.setId(UUID.randomUUID());
        version.setCreatedAt(Instant.now());

        SecretVersionResponse response = secretMapper.toVersionResponse(version);

        assertThat(response.isDestroyed()).isTrue();
    }

    @Test
    void toVersionResponses_emptyList_returnsEmpty() {
        List<SecretVersionResponse> responses = secretMapper.toVersionResponses(List.of());
        assertThat(responses).isEmpty();
    }
}
