package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.RotationHistoryResponse;
import com.codeops.vault.dto.response.RotationPolicyResponse;
import com.codeops.vault.entity.RotationHistory;
import com.codeops.vault.entity.RotationPolicy;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.enums.RotationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RotationMapper} MapStruct implementation.
 */
@SpringBootTest
@ActiveProfiles("test")
class RotationMapperTest {

    @Autowired
    private RotationMapper rotationMapper;

    @Test
    void toResponse_mapsAllFieldsWithSecretPath() {
        UUID policyId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        Instant now = Instant.now();

        Secret secret = Secret.builder().build();
        secret.setId(secretId);

        RotationPolicy policy = RotationPolicy.builder()
                .secret(secret)
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .rotationIntervalHours(24)
                .randomLength(32)
                .randomCharset("alphanumeric")
                .isActive(true)
                .failureCount(0)
                .maxFailures(5)
                .lastRotatedAt(now.minusSeconds(3600))
                .nextRotationAt(now.plusSeconds(82800))
                .build();
        policy.setId(policyId);
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);

        RotationPolicyResponse response = rotationMapper.toResponse(policy, "/services/app/db");

        assertThat(response.id()).isEqualTo(policyId);
        assertThat(response.secretId()).isEqualTo(secretId);
        assertThat(response.secretPath()).isEqualTo("/services/app/db");
        assertThat(response.strategy()).isEqualTo(RotationStrategy.RANDOM_GENERATE);
        assertThat(response.rotationIntervalHours()).isEqualTo(24);
        assertThat(response.randomLength()).isEqualTo(32);
        assertThat(response.randomCharset()).isEqualTo("alphanumeric");
        assertThat(response.isActive()).isTrue();
        assertThat(response.failureCount()).isEqualTo(0);
        assertThat(response.maxFailures()).isEqualTo(5);
    }

    @Test
    void toHistoryResponse_mapsAllFields() {
        UUID historyId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID triggeredByUserId = UUID.randomUUID();
        Instant now = Instant.now();

        RotationHistory history = RotationHistory.builder()
                .secretId(secretId)
                .secretPath("/services/app/db")
                .strategy(RotationStrategy.EXTERNAL_API)
                .previousVersion(2)
                .newVersion(3)
                .success(true)
                .durationMs(1500L)
                .triggeredByUserId(triggeredByUserId)
                .build();
        history.setId(historyId);
        history.setCreatedAt(now);

        RotationHistoryResponse response = rotationMapper.toHistoryResponse(history);

        assertThat(response.id()).isEqualTo(historyId);
        assertThat(response.secretId()).isEqualTo(secretId);
        assertThat(response.secretPath()).isEqualTo("/services/app/db");
        assertThat(response.strategy()).isEqualTo(RotationStrategy.EXTERNAL_API);
        assertThat(response.previousVersion()).isEqualTo(2);
        assertThat(response.newVersion()).isEqualTo(3);
        assertThat(response.success()).isTrue();
        assertThat(response.durationMs()).isEqualTo(1500L);
        assertThat(response.triggeredByUserId()).isEqualTo(triggeredByUserId);
    }

    @Test
    void toHistoryResponse_failedRotation_errorMessagePreserved() {
        RotationHistory history = RotationHistory.builder()
                .secretId(UUID.randomUUID())
                .secretPath("/path")
                .strategy(RotationStrategy.CUSTOM_SCRIPT)
                .previousVersion(1)
                .success(false)
                .errorMessage("Script exited with code 1")
                .durationMs(500L)
                .build();
        history.setId(UUID.randomUUID());
        history.setCreatedAt(Instant.now());

        RotationHistoryResponse response = rotationMapper.toHistoryResponse(history);

        assertThat(response.success()).isFalse();
        assertThat(response.newVersion()).isNull();
        assertThat(response.errorMessage()).isEqualTo("Script exited with code 1");
    }

    @Test
    void toHistoryResponses_mapsList() {
        RotationHistory h1 = RotationHistory.builder()
                .secretId(UUID.randomUUID()).secretPath("/p1")
                .strategy(RotationStrategy.RANDOM_GENERATE).success(true).build();
        h1.setId(UUID.randomUUID());
        h1.setCreatedAt(Instant.now());

        RotationHistory h2 = RotationHistory.builder()
                .secretId(UUID.randomUUID()).secretPath("/p2")
                .strategy(RotationStrategy.EXTERNAL_API).success(false).build();
        h2.setId(UUID.randomUUID());
        h2.setCreatedAt(Instant.now());

        List<RotationHistoryResponse> responses = rotationMapper.toHistoryResponses(List.of(h1, h2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).success()).isTrue();
        assertThat(responses.get(1).success()).isFalse();
    }
}
