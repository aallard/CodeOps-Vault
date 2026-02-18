package com.codeops.vault.dto.response;

import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecretResponse} record.
 */
class SecretResponseTest {

    @Test
    void recordCreation_allFieldsAccessible() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, String> metadata = Map.of("env", "prod");

        var response = new SecretResponse(id, teamId, "/services/app/db", "DB Password",
                "A database password", SecretType.STATIC, 3, 100, 30,
                now.plusSeconds(86400), now.minusSeconds(60), now.minusSeconds(3600),
                ownerUserId, null, true, metadata, now, now);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.path()).isEqualTo("/services/app/db");
        assertThat(response.name()).isEqualTo("DB Password");
        assertThat(response.description()).isEqualTo("A database password");
        assertThat(response.secretType()).isEqualTo(SecretType.STATIC);
        assertThat(response.currentVersion()).isEqualTo(3);
        assertThat(response.maxVersions()).isEqualTo(100);
        assertThat(response.retentionDays()).isEqualTo(30);
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.isActive()).isTrue();
        assertThat(response.metadata()).isEqualTo(metadata);
    }

    @Test
    void recordEquality_sameFields_areEqual() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        var response1 = new SecretResponse(id, teamId, "/path", "name", null,
                SecretType.STATIC, 1, null, null, null, null, null,
                null, null, true, null, now, now);
        var response2 = new SecretResponse(id, teamId, "/path", "name", null,
                SecretType.STATIC, 1, null, null, null, null, null,
                null, null, true, null, now, now);

        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    void recordWithNullOptionalFields_noExceptions() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        var response = new SecretResponse(id, teamId, "/path", "name", null,
                SecretType.DYNAMIC, 1, null, null, null, null, null,
                null, null, true, null, now, now);

        assertThat(response.description()).isNull();
        assertThat(response.maxVersions()).isNull();
        assertThat(response.retentionDays()).isNull();
        assertThat(response.expiresAt()).isNull();
        assertThat(response.metadata()).isNull();
        assertThat(response.referenceArn()).isNull();
    }
}
