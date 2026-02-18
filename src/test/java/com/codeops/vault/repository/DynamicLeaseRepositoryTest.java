package com.codeops.vault.repository;

import com.codeops.vault.entity.DynamicLease;
import com.codeops.vault.entity.enums.LeaseStatus;
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
 * Repository tests for {@link DynamicLeaseRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class DynamicLeaseRepositoryTest {

    @Autowired
    private DynamicLeaseRepository dynamicLeaseRepository;

    private static final UUID SECRET_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dynamicLeaseRepository.deleteAll();

        // Active lease that hasn't expired
        dynamicLeaseRepository.save(DynamicLease.builder()
                .leaseId("lease-active-1")
                .secretId(SECRET_ID)
                .secretPath("/services/app/db/creds")
                .backendType("postgresql")
                .credentials("SEED:Y3JlZHMx")
                .status(LeaseStatus.ACTIVE)
                .ttlSeconds(3600)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build());

        // Active lease that has expired (expiresAt in past)
        dynamicLeaseRepository.save(DynamicLease.builder()
                .leaseId("lease-expired-1")
                .secretId(SECRET_ID)
                .secretPath("/services/app/db/creds")
                .backendType("postgresql")
                .credentials("SEED:Y3JlZHMy")
                .status(LeaseStatus.ACTIVE)
                .ttlSeconds(3600)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        // Revoked lease
        dynamicLeaseRepository.save(DynamicLease.builder()
                .leaseId("lease-revoked-1")
                .secretId(SECRET_ID)
                .secretPath("/services/app/db/creds")
                .backendType("postgresql")
                .credentials("SEED:Y3JlZHMz")
                .status(LeaseStatus.REVOKED)
                .ttlSeconds(3600)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .revokedAt(Instant.now())
                .build());
    }

    @Test
    void findByLeaseId_existingLease_returnsLease() {
        Optional<DynamicLease> result = dynamicLeaseRepository.findByLeaseId("lease-active-1");
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(LeaseStatus.ACTIVE);
    }

    @Test
    void findByLeaseId_nonExistent_returnsEmpty() {
        Optional<DynamicLease> result = dynamicLeaseRepository.findByLeaseId("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void findBySecretId_returnsAllLeases() {
        List<DynamicLease> leases = dynamicLeaseRepository.findBySecretId(SECRET_ID);
        assertThat(leases).hasSize(3);
    }

    @Test
    void findByStatus_returnsMatchingLeases() {
        List<DynamicLease> active = dynamicLeaseRepository.findByStatus(LeaseStatus.ACTIVE);
        assertThat(active).hasSize(2);

        List<DynamicLease> revoked = dynamicLeaseRepository.findByStatus(LeaseStatus.REVOKED);
        assertThat(revoked).hasSize(1);
    }

    @Test
    void findByStatusAndExpiresAtBefore_findsExpiredActiveLeases() {
        List<DynamicLease> expired = dynamicLeaseRepository
                .findByStatusAndExpiresAtBefore(LeaseStatus.ACTIVE, Instant.now());
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getLeaseId()).isEqualTo("lease-expired-1");
    }

    @Test
    void countBySecretIdAndStatus_countsCorrectly() {
        assertThat(dynamicLeaseRepository.countBySecretIdAndStatus(SECRET_ID, LeaseStatus.ACTIVE)).isEqualTo(2);
        assertThat(dynamicLeaseRepository.countBySecretIdAndStatus(SECRET_ID, LeaseStatus.REVOKED)).isEqualTo(1);
    }

    @Test
    void countByStatus_countsCorrectly() {
        assertThat(dynamicLeaseRepository.countByStatus(LeaseStatus.ACTIVE)).isEqualTo(2);
        assertThat(dynamicLeaseRepository.countByStatus(LeaseStatus.REVOKED)).isEqualTo(1);
        assertThat(dynamicLeaseRepository.countByStatus(LeaseStatus.EXPIRED)).isEqualTo(0);
    }
}
