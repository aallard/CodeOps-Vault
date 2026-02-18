package com.codeops.vault.repository;

import com.codeops.vault.entity.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link AuditEntryRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class AuditEntryRepositoryTest {

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditEntryRepository.deleteAll();

        auditEntryRepository.save(AuditEntry.builder()
                .teamId(TEAM_ID)
                .userId(USER_ID)
                .operation("READ")
                .path("/services/app/db/password")
                .resourceType("SECRET")
                .resourceId(RESOURCE_ID)
                .success(true)
                .build());

        auditEntryRepository.save(AuditEntry.builder()
                .teamId(TEAM_ID)
                .userId(USER_ID)
                .operation("WRITE")
                .path("/services/app/db/password")
                .resourceType("SECRET")
                .resourceId(RESOURCE_ID)
                .success(true)
                .build());

        auditEntryRepository.save(AuditEntry.builder()
                .teamId(TEAM_ID)
                .userId(USER_ID)
                .operation("DELETE")
                .path("/services/old/secret")
                .resourceType("SECRET")
                .success(false)
                .errorMessage("Access denied")
                .build());

        auditEntryRepository.save(AuditEntry.builder()
                .teamId(TEAM_ID)
                .userId(UUID.randomUUID())
                .operation("POLICY_CREATE")
                .resourceType("POLICY")
                .success(true)
                .build());
    }

    @Test
    void findByTeamId_returnsAllEntries() {
        Page<AuditEntry> page = auditEntryRepository.findByTeamId(TEAM_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(4);
    }

    @Test
    void findByUserId_returnsUserEntries() {
        Page<AuditEntry> page = auditEntryRepository.findByUserId(USER_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(3);
    }

    @Test
    void findByTeamIdAndOperation_filtersCorrectly() {
        Page<AuditEntry> page = auditEntryRepository.findByTeamIdAndOperation(TEAM_ID, "READ", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getOperation()).isEqualTo("READ");
    }

    @Test
    void findByTeamIdAndPath_filtersCorrectly() {
        Page<AuditEntry> page = auditEntryRepository.findByTeamIdAndPath(TEAM_ID, "/services/app/db/password", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByTeamIdAndCreatedAtBetween_filtersTimeRange() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        Page<AuditEntry> page = auditEntryRepository.findByTeamIdAndCreatedAtBetween(TEAM_ID, start, end, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(4);
    }

    @Test
    void findByTeamIdAndResourceTypeAndResourceId_filtersCorrectly() {
        Page<AuditEntry> page = auditEntryRepository.findByTeamIdAndResourceTypeAndResourceId(
                TEAM_ID, "SECRET", RESOURCE_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByTeamIdAndSuccessFalse_returnsFailures() {
        Page<AuditEntry> page = auditEntryRepository.findByTeamIdAndSuccessFalse(TEAM_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getErrorMessage()).isEqualTo("Access denied");
    }

    @Test
    void countByTeamId_returnsCorrectCount() {
        assertThat(auditEntryRepository.countByTeamId(TEAM_ID)).isEqualTo(4);
    }

    @Test
    void countByTeamIdAndOperation_countsCorrectly() {
        assertThat(auditEntryRepository.countByTeamIdAndOperation(TEAM_ID, "READ")).isEqualTo(1);
        assertThat(auditEntryRepository.countByTeamIdAndOperation(TEAM_ID, "WRITE")).isEqualTo(1);
    }
}
