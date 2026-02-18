package com.codeops.vault.entity;

import com.codeops.vault.entity.enums.LeaseStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link DynamicLease} entity.
 */
class DynamicLeaseTest {

    private DynamicLease buildLease() {
        Instant now = Instant.now();
        return DynamicLease.builder()
                .leaseId(UUID.randomUUID().toString())
                .secretId(UUID.randomUUID())
                .secretPath("/services/app/db/creds")
                .backendType("postgresql")
                .credentials("SEED:ZW5jcnlwdGVk")
                .status(LeaseStatus.ACTIVE)
                .ttlSeconds(3600)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .requestedByUserId(UUID.randomUUID())
                .build();
    }

    @Test
    void builder_createsWithAllFields() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(2, ChronoUnit.HOURS);

        DynamicLease lease = DynamicLease.builder()
                .leaseId("lease-123")
                .secretId(secretId)
                .secretPath("/services/app/db/creds")
                .backendType("mysql")
                .credentials("SEED:Y3JlZHM=")
                .status(LeaseStatus.ACTIVE)
                .ttlSeconds(7200)
                .expiresAt(expiresAt)
                .requestedByUserId(userId)
                .metadataJson("{\"host\":\"db.example.com\"}")
                .build();

        assertThat(lease.getLeaseId()).isEqualTo("lease-123");
        assertThat(lease.getSecretId()).isEqualTo(secretId);
        assertThat(lease.getBackendType()).isEqualTo("mysql");
        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.ACTIVE);
        assertThat(lease.getTtlSeconds()).isEqualTo(7200);
        assertThat(lease.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(lease.getRequestedByUserId()).isEqualTo(userId);
    }

    @Test
    void setter_revokedFields() {
        DynamicLease lease = buildLease();
        Instant revokedAt = Instant.now();
        UUID revokerId = UUID.randomUUID();

        lease.setStatus(LeaseStatus.REVOKED);
        lease.setRevokedAt(revokedAt);
        lease.setRevokedByUserId(revokerId);

        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.REVOKED);
        assertThat(lease.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(lease.getRevokedByUserId()).isEqualTo(revokerId);
    }

    @Test
    void setter_statusExpired() {
        DynamicLease lease = buildLease();
        lease.setStatus(LeaseStatus.EXPIRED);
        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.EXPIRED);
    }

    @Test
    void builder_optionalFieldsDefaultToNull() {
        DynamicLease lease = buildLease();
        assertThat(lease.getRevokedAt()).isNull();
        assertThat(lease.getRevokedByUserId()).isNull();
        assertThat(lease.getMetadataJson()).isNull();
    }

    @Test
    void prePersist_setsTimestamps() {
        DynamicLease lease = buildLease();
        lease.onCreate();

        assertThat(lease.getCreatedAt()).isNotNull();
        assertThat(lease.getUpdatedAt()).isNotNull();
    }

    @Test
    void leaseId_isUniqueIdentifier() {
        DynamicLease lease = buildLease();
        assertThat(lease.getLeaseId()).isNotNull().isNotBlank();
    }
}
