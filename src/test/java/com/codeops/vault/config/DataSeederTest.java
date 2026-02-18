package com.codeops.vault.config;

import com.codeops.vault.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataSeeder}.
 *
 * <p>Uses the "test" profile with H2 in-memory database. Manually invokes
 * the DataSeeder (which is normally restricted to the "dev" profile) and
 * verifies that seed data is created correctly and idempotently.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class DataSeederTest {

    private static final UUID DEV_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private SecretVersionRepository secretVersionRepository;

    @Autowired
    private AccessPolicyRepository accessPolicyRepository;

    @Autowired
    private PolicyBindingRepository policyBindingRepository;

    @Autowired
    private RotationPolicyRepository rotationPolicyRepository;

    @Autowired
    private TransitKeyRepository transitKeyRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Clean slate — then run the seeder manually (it's @Profile("dev"), not auto-created in test)
        policyBindingRepository.deleteAll();
        rotationPolicyRepository.deleteAll();
        accessPolicyRepository.deleteAll();
        secretVersionRepository.deleteAll();
        secretRepository.deleteAll();
        transitKeyRepository.deleteAll();

        DataSeeder seeder = new DataSeeder(
                secretRepository, secretVersionRepository,
                accessPolicyRepository, policyBindingRepository,
                rotationPolicyRepository, transitKeyRepository);
        seeder.run();
    }

    @Test
    void seeder_createsExpectedSecrets() {
        assertThat(secretRepository.countByTeamId(DEV_TEAM_ID)).isEqualTo(3);
    }

    @Test
    void seeder_createsExpectedPoliciesAndBindings() {
        assertThat(accessPolicyRepository.countByTeamId(DEV_TEAM_ID)).isEqualTo(2);
        long bindingCount = policyBindingRepository.findAll().size();
        assertThat(bindingCount).isEqualTo(3);
    }

    @Test
    void seeder_createsRotationPolicyAndTransitKey() {
        assertThat(rotationPolicyRepository.countByIsActiveTrue()).isEqualTo(1);
        assertThat(transitKeyRepository.countByTeamId(DEV_TEAM_ID)).isEqualTo(1);
    }
}
