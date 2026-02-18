package com.codeops.vault.service;

import com.codeops.vault.dto.mapper.AuditMapper;
import com.codeops.vault.dto.request.AuditQueryRequest;
import com.codeops.vault.dto.response.AuditEntryResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.entity.AuditEntry;
import com.codeops.vault.repository.AuditEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService}.
 *
 * <p>Covers audit logging (success/failure), query filters,
 * resource-specific queries, statistics, and error resilience.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEntryRepository auditEntryRepository;

    @Mock
    private AuditMapper auditMapper;

    @InjectMocks
    private AuditService auditService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();

    // ─── logSuccess Tests ────────────────────────────────────

    @Test
    void logSuccess_savesAuditEntryWithCorrectFields() {
        when(auditEntryRepository.save(any(AuditEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logSuccess(TEAM_ID, USER_ID, "WRITE", "/secrets/db/password",
                "SECRET", RESOURCE_ID, null);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getOperation()).isEqualTo("WRITE");
        assertThat(saved.getPath()).isEqualTo("/secrets/db/password");
        assertThat(saved.getResourceType()).isEqualTo("SECRET");
        assertThat(saved.getResourceId()).isEqualTo(RESOURCE_ID);
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getIpAddress()).isEqualTo("system");
        assertThat(saved.getCorrelationId()).isEqualTo("no-correlation-id");
    }

    @Test
    void logSuccess_neverThrowsOnRepositoryFailure() {
        when(auditEntryRepository.save(any(AuditEntry.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Should not throw — audit failures are silently logged
        auditService.logSuccess(TEAM_ID, USER_ID, "READ", "/secrets/test",
                "SECRET", RESOURCE_ID, null);

        verify(auditEntryRepository).save(any(AuditEntry.class));
    }

    @Test
    void logSuccess_withNullUserAndTeam_savesSystemEntry() {
        when(auditEntryRepository.save(any(AuditEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logSuccess(null, null, "SEAL", null, "VAULT", null, null);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.getTeamId()).isNull();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getOperation()).isEqualTo("SEAL");
        assertThat(saved.getSuccess()).isTrue();
    }

    // ─── logFailure Tests ────────────────────────────────────

    @Test
    void logFailure_savesEntryWithErrorMessage() {
        when(auditEntryRepository.save(any(AuditEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logFailure(TEAM_ID, USER_ID, "DELETE", "/secrets/locked",
                "SECRET", RESOURCE_ID, "Access denied");

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getErrorMessage()).isEqualTo("Access denied");
        assertThat(saved.getOperation()).isEqualTo("DELETE");
    }

    @Test
    void logFailure_neverThrowsOnRepositoryFailure() {
        when(auditEntryRepository.save(any(AuditEntry.class)))
                .thenThrow(new RuntimeException("DB timeout"));

        // Should not throw
        auditService.logFailure(TEAM_ID, USER_ID, "WRITE", "/secrets/test",
                "SECRET", RESOURCE_ID, "Some error");

        verify(auditEntryRepository).save(any(AuditEntry.class));
    }

    // ─── queryAuditLog Tests ─────────────────────────────────

    @Test
    void queryAuditLog_withResourceFilter_usesResourceQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditEntry entry = buildAuditEntry();
        Page<AuditEntry> page = new PageImpl<>(List.of(entry), pageable, 1);
        AuditEntryResponse response = buildResponse();

        when(auditEntryRepository.findByTeamIdAndResourceTypeAndResourceId(
                TEAM_ID, "SECRET", RESOURCE_ID, pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of(entry))).thenReturn(List.of(response));

        AuditQueryRequest query = new AuditQueryRequest(
                null, null, null, "SECRET", RESOURCE_ID, null, null, null);

        PageResponse<AuditEntryResponse> result = auditService.queryAuditLog(TEAM_ID, query, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(auditEntryRepository).findByTeamIdAndResourceTypeAndResourceId(TEAM_ID, "SECRET", RESOURCE_ID, pageable);
    }

    @Test
    void queryAuditLog_withUserIdFilter_usesUserIdQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditEntry> page = new PageImpl<>(List.of(), pageable, 0);

        when(auditEntryRepository.findByUserId(USER_ID, pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of())).thenReturn(List.of());

        AuditQueryRequest query = new AuditQueryRequest(
                USER_ID, null, null, null, null, null, null, null);

        auditService.queryAuditLog(TEAM_ID, query, pageable);

        verify(auditEntryRepository).findByUserId(USER_ID, pageable);
    }

    @Test
    void queryAuditLog_withOperationFilter_usesOperationQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditEntry> page = new PageImpl<>(List.of(), pageable, 0);

        when(auditEntryRepository.findByTeamIdAndOperation(TEAM_ID, "READ", pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of())).thenReturn(List.of());

        AuditQueryRequest query = new AuditQueryRequest(
                null, "READ", null, null, null, null, null, null);

        auditService.queryAuditLog(TEAM_ID, query, pageable);

        verify(auditEntryRepository).findByTeamIdAndOperation(TEAM_ID, "READ", pageable);
    }

    @Test
    void queryAuditLog_withNoFilters_returnsAllTeamEntries() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditEntry> page = new PageImpl<>(List.of(), pageable, 0);

        when(auditEntryRepository.findByTeamId(TEAM_ID, pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of())).thenReturn(List.of());

        AuditQueryRequest query = new AuditQueryRequest(
                null, null, null, null, null, null, null, null);

        auditService.queryAuditLog(TEAM_ID, query, pageable);

        verify(auditEntryRepository).findByTeamId(TEAM_ID, pageable);
    }

    @Test
    void queryAuditLog_withFailedOnly_usesFailedQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditEntry> page = new PageImpl<>(List.of(), pageable, 0);

        when(auditEntryRepository.findByTeamIdAndSuccessFalse(TEAM_ID, pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of())).thenReturn(List.of());

        AuditQueryRequest query = new AuditQueryRequest(
                null, null, null, null, null, false, null, null);

        auditService.queryAuditLog(TEAM_ID, query, pageable);

        verify(auditEntryRepository).findByTeamIdAndSuccessFalse(TEAM_ID, pageable);
    }

    @Test
    void queryAuditLog_withTimeRange_usesTimeRangeQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        Page<AuditEntry> page = new PageImpl<>(List.of(), pageable, 0);

        when(auditEntryRepository.findByTeamIdAndCreatedAtBetween(TEAM_ID, start, end, pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of())).thenReturn(List.of());

        AuditQueryRequest query = new AuditQueryRequest(
                null, null, null, null, null, null, start, end);

        auditService.queryAuditLog(TEAM_ID, query, pageable);

        verify(auditEntryRepository).findByTeamIdAndCreatedAtBetween(TEAM_ID, start, end, pageable);
    }

    // ─── getAuditForResource Tests ───────────────────────────

    @Test
    void getAuditForResource_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditEntry entry = buildAuditEntry();
        Page<AuditEntry> page = new PageImpl<>(List.of(entry), pageable, 1);
        AuditEntryResponse response = buildResponse();

        when(auditEntryRepository.findByTeamIdAndResourceTypeAndResourceId(
                TEAM_ID, "SECRET", RESOURCE_ID, pageable)).thenReturn(page);
        when(auditMapper.toResponses(List.of(entry))).thenReturn(List.of(response));

        PageResponse<AuditEntryResponse> result =
                auditService.getAuditForResource(TEAM_ID, "SECRET", RESOURCE_ID, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).operation()).isEqualTo("WRITE");
    }

    // ─── getAuditStats Tests ────────────────────────────────

    @Test
    void getAuditStats_returnsAllCounts() {
        when(auditEntryRepository.countByTeamId(TEAM_ID)).thenReturn(100L);
        when(auditEntryRepository.countByTeamIdAndSuccessFalse(TEAM_ID)).thenReturn(5L);
        when(auditEntryRepository.countByTeamIdAndOperation(TEAM_ID, "READ")).thenReturn(50L);
        when(auditEntryRepository.countByTeamIdAndOperation(TEAM_ID, "WRITE")).thenReturn(30L);
        when(auditEntryRepository.countByTeamIdAndOperation(TEAM_ID, "DELETE")).thenReturn(10L);

        Map<String, Long> stats = auditService.getAuditStats(TEAM_ID);

        assertThat(stats).containsEntry("totalEntries", 100L);
        assertThat(stats).containsEntry("failedEntries", 5L);
        assertThat(stats).containsEntry("readOperations", 50L);
        assertThat(stats).containsEntry("writeOperations", 30L);
        assertThat(stats).containsEntry("deleteOperations", 10L);
    }

    // ─── Helpers ─────────────────────────────────────────────

    private AuditEntry buildAuditEntry() {
        return AuditEntry.builder()
                .id(1L)
                .teamId(TEAM_ID)
                .userId(USER_ID)
                .operation("WRITE")
                .path("/secrets/db/password")
                .resourceType("SECRET")
                .resourceId(RESOURCE_ID)
                .success(true)
                .ipAddress("127.0.0.1")
                .correlationId("test-corr-id")
                .createdAt(Instant.now())
                .build();
    }

    private AuditEntryResponse buildResponse() {
        return new AuditEntryResponse(
                1L, TEAM_ID, USER_ID, "WRITE", "/secrets/db/password",
                "SECRET", RESOURCE_ID, true, null, "127.0.0.1",
                "test-corr-id", Instant.now());
    }
}
