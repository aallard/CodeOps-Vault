package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.AuditEntryResponse;
import com.codeops.vault.entity.AuditEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AuditMapper} MapStruct implementation.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditMapperTest {

    @Autowired
    private AuditMapper auditMapper;

    @Test
    void toResponse_mapsAllFields() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEntry entry = AuditEntry.builder()
                .id(1L)
                .teamId(teamId)
                .userId(userId)
                .operation("READ")
                .path("/services/app/db")
                .resourceType("SECRET")
                .resourceId(resourceId)
                .success(true)
                .ipAddress("192.168.1.1")
                .correlationId("req-123")
                .createdAt(now)
                .build();

        AuditEntryResponse response = auditMapper.toResponse(entry);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.operation()).isEqualTo("READ");
        assertThat(response.path()).isEqualTo("/services/app/db");
        assertThat(response.resourceType()).isEqualTo("SECRET");
        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.success()).isTrue();
        assertThat(response.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(response.correlationId()).isEqualTo("req-123");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void toResponses_mapsList() {
        AuditEntry e1 = AuditEntry.builder()
                .id(1L).operation("READ").success(true).createdAt(Instant.now()).build();
        AuditEntry e2 = AuditEntry.builder()
                .id(2L).operation("WRITE").success(true).createdAt(Instant.now()).build();
        AuditEntry e3 = AuditEntry.builder()
                .id(3L).operation("DELETE").success(false)
                .errorMessage("Access denied").createdAt(Instant.now()).build();

        List<AuditEntryResponse> responses = auditMapper.toResponses(List.of(e1, e2, e3));

        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).operation()).isEqualTo("READ");
        assertThat(responses.get(1).operation()).isEqualTo("WRITE");
        assertThat(responses.get(2).operation()).isEqualTo("DELETE");
        assertThat(responses.get(2).success()).isFalse();
        assertThat(responses.get(2).errorMessage()).isEqualTo("Access denied");
    }

    @Test
    void toResponse_failedEntry_errorMessagePreserved() {
        AuditEntry entry = AuditEntry.builder()
                .id(1L)
                .operation("ROTATE")
                .success(false)
                .errorMessage("Rotation script timed out")
                .createdAt(Instant.now())
                .build();

        AuditEntryResponse response = auditMapper.toResponse(entry);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).isEqualTo("Rotation script timed out");
    }
}
