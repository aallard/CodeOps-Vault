package com.codeops.vault.repository;

import com.codeops.vault.entity.RotationPolicy;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.enums.RotationStrategy;
import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link RotationPolicyRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class RotationPolicyRepositoryTest {

    @Autowired
    private RotationPolicyRepository rotationPolicyRepository;

    @Autowired
    private SecretRepository secretRepository;

    private Secret secret1;
    private Secret secret2;

    @BeforeEach
    void setUp() {
        rotationPolicyRepository.deleteAll();
        secretRepository.deleteAll();

        secret1 = secretRepository.save(Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/test/secret-1")
                .name("Secret 1")
                .secretType(SecretType.STATIC)
                .build());

        secret2 = secretRepository.save(Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/test/secret-2")
                .name("Secret 2")
                .secretType(SecretType.STATIC)
                .build());

        // Active policy, due for rotation (nextRotationAt in the past)
        rotationPolicyRepository.save(RotationPolicy.builder()
                .secret(secret1)
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .rotationIntervalHours(24)
                .randomLength(32)
                .nextRotationAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        // Inactive policy
        rotationPolicyRepository.save(RotationPolicy.builder()
                .secret(secret2)
                .strategy(RotationStrategy.EXTERNAL_API)
                .rotationIntervalHours(168)
                .externalApiUrl("https://api.example.com/rotate")
                .isActive(false)
                .nextRotationAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());
    }

    @Test
    void findBySecretId_existingPolicy_returnsPolicy() {
        Optional<RotationPolicy> result = rotationPolicyRepository.findBySecretId(secret1.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getStrategy()).isEqualTo(RotationStrategy.RANDOM_GENERATE);
    }

    @Test
    void findBySecretId_nonExistent_returnsEmpty() {
        Optional<RotationPolicy> result = rotationPolicyRepository.findBySecretId(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void findByIsActiveTrueAndNextRotationAtBefore_returnsDuePolicies() {
        List<RotationPolicy> due = rotationPolicyRepository
                .findByIsActiveTrueAndNextRotationAtBefore(Instant.now());
        assertThat(due).hasSize(1);
        assertThat(due.get(0).getSecret().getId()).isEqualTo(secret1.getId());
    }

    @Test
    void findByIsActiveTrue_returnsActivePolicies() {
        List<RotationPolicy> active = rotationPolicyRepository.findByIsActiveTrue();
        assertThat(active).hasSize(1);
    }

    @Test
    void countByIsActiveTrue_returnsCorrectCount() {
        assertThat(rotationPolicyRepository.countByIsActiveTrue()).isEqualTo(1);
    }
}
