package com.codeops.vault.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link AuditEntry} entity.
 *
 * <p>Verifies that AuditEntry does NOT extend BaseEntity and uses
 * a Long auto-increment primary key.</p>
 */
class AuditEntryTest {

    private AuditEntry buildEntry() {
        return AuditEntry.builder()
                .teamId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .operation("READ")
                .path("/services/app/db/password")
                .resourceType("SECRET")
                .resourceId(UUID.randomUUID())
                .success(true)
                .ipAddress("192.168.1.1")
                .correlationId("corr-123")
                .build();
    }

    @Test
    void builder_createsWithAllFields() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        AuditEntry entry = AuditEntry.builder()
                .teamId(teamId)
                .userId(userId)
                .operation("WRITE")
                .path("/services/app/key")
                .resourceType("SECRET")
                .resourceId(resourceId)
                .success(true)
                .ipAddress("10.0.0.1")
                .correlationId("req-456")
                .detailsJson("{\"version\":2}")
                .build();

        assertThat(entry.getTeamId()).isEqualTo(teamId);
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getOperation()).isEqualTo("WRITE");
        assertThat(entry.getPath()).isEqualTo("/services/app/key");
        assertThat(entry.getResourceType()).isEqualTo("SECRET");
        assertThat(entry.getResourceId()).isEqualTo(resourceId);
        assertThat(entry.getSuccess()).isTrue();
        assertThat(entry.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(entry.getCorrelationId()).isEqualTo("req-456");
        assertThat(entry.getDetailsJson()).isEqualTo("{\"version\":2}");
    }

    @Test
    void auditEntry_doesNotExtendBaseEntity() {
        AuditEntry entry = buildEntry();
        assertThat(entry).isNotInstanceOf(BaseEntity.class);
    }

    @Test
    void auditEntry_idIsLong() {
        AuditEntry entry = buildEntry();
        entry.setId(42L);
        assertThat(entry.getId()).isEqualTo(42L);
    }

    @Test
    void prePersist_setsCreatedAt() {
        AuditEntry entry = buildEntry();
        entry.onCreate();
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void builder_failureEntry() {
        AuditEntry entry = AuditEntry.builder()
                .operation("DELETE")
                .success(false)
                .errorMessage("Access denied")
                .build();

        assertThat(entry.getSuccess()).isFalse();
        assertThat(entry.getErrorMessage()).isEqualTo("Access denied");
    }
}
