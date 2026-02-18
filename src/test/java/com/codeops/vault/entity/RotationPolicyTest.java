package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.RotationStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link RotationPolicy} entity.
 */
class RotationPolicyTest {

    private RotationPolicy buildPolicy(RotationStrategy strategy) {
        return RotationPolicy.builder()
                .strategy(strategy)
                .rotationIntervalHours(24)
                .build();
    }

    @Test
    void builder_createsWithRandomGenerateFields() {
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .rotationIntervalHours(720)
                .randomLength(32)
                .randomCharset("alphanumeric")
                .build();

        assertThat(policy.getStrategy()).isEqualTo(RotationStrategy.RANDOM_GENERATE);
        assertThat(policy.getRotationIntervalHours()).isEqualTo(720);
        assertThat(policy.getRandomLength()).isEqualTo(32);
        assertThat(policy.getRandomCharset()).isEqualTo("alphanumeric");
    }

    @Test
    void builder_createsWithExternalApiFields() {
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.EXTERNAL_API)
                .rotationIntervalHours(168)
                .externalApiUrl("https://api.example.com/rotate")
                .externalApiHeaders("{\"Authorization\": \"Bearer token\"}")
                .build();

        assertThat(policy.getStrategy()).isEqualTo(RotationStrategy.EXTERNAL_API);
        assertThat(policy.getExternalApiUrl()).isEqualTo("https://api.example.com/rotate");
        assertThat(policy.getExternalApiHeaders()).contains("Bearer token");
    }

    @Test
    void builder_createsWithCustomScriptFields() {
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.CUSTOM_SCRIPT)
                .rotationIntervalHours(48)
                .scriptCommand("/opt/scripts/rotate.sh")
                .build();

        assertThat(policy.getStrategy()).isEqualTo(RotationStrategy.CUSTOM_SCRIPT);
        assertThat(policy.getScriptCommand()).isEqualTo("/opt/scripts/rotate.sh");
    }

    @Test
    void builder_defaultIsActive_isTrue() {
        RotationPolicy policy = buildPolicy(RotationStrategy.RANDOM_GENERATE);
        assertThat(policy.getIsActive()).isTrue();
    }

    @Test
    void builder_defaultFailureCount_isZero() {
        RotationPolicy policy = buildPolicy(RotationStrategy.RANDOM_GENERATE);
        assertThat(policy.getFailureCount()).isEqualTo(0);
    }

    @Test
    void setter_rotationTimestamps() {
        RotationPolicy policy = buildPolicy(RotationStrategy.RANDOM_GENERATE);
        Instant now = Instant.now();
        Instant next = now.plus(24, ChronoUnit.HOURS);

        policy.setLastRotatedAt(now);
        policy.setNextRotationAt(next);

        assertThat(policy.getLastRotatedAt()).isEqualTo(now);
        assertThat(policy.getNextRotationAt()).isEqualTo(next);
    }
}
