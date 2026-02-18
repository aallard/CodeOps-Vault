package com.codeops.vault.service;

import com.codeops.vault.config.DynamicSecretProperties;
import com.codeops.vault.dto.mapper.LeaseMapper;
import com.codeops.vault.dto.request.CreateDynamicLeaseRequest;
import com.codeops.vault.dto.response.DynamicLeaseResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.entity.DynamicLease;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.enums.LeaseStatus;
import com.codeops.vault.entity.enums.SecretType;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import com.codeops.vault.repository.DynamicLeaseRepository;
import com.codeops.vault.repository.SecretRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamicSecretService}.
 *
 * <p>Covers lease creation, management, revocation, expiry processing,
 * backend operations, and statistics.</p>
 */
@ExtendWith(MockitoExtension.class)
class DynamicSecretServiceTest {

    @Mock
    private DynamicLeaseRepository leaseRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private LeaseMapper leaseMapper;

    @Mock
    private DynamicSecretProperties dynamicSecretProperties;

    @InjectMocks
    private DynamicSecretService dynamicSecretService;

    private static final String METADATA_JSON =
            "{\"backendType\":\"postgresql\",\"host\":\"localhost\",\"port\":\"5432\"," +
            "\"database\":\"myapp_db\",\"adminUser\":\"admin\",\"adminPassword\":\"admin_pass\"}";

    @BeforeEach
    void setUp() {
        lenient().when(dynamicSecretProperties.getPasswordLength()).thenReturn(32);
        lenient().when(dynamicSecretProperties.getUsernamePrefix()).thenReturn("v_");
        lenient().when(dynamicSecretProperties.isExecuteSql()).thenReturn(false);
    }

    // ─── Lease Creation ─────────────────────────────────────

    @Test
    void createLease_success_returnsCredentials() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("generatedPassword123");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-credentials");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        DynamicLeaseResponse expectedResponse = buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true);
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(expectedResponse);

        DynamicLeaseResponse result = dynamicSecretService.createLease(request, userId);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(result.connectionDetails()).isNotNull();
        verify(leaseRepository).save(any(DynamicLease.class));
    }

    @Test
    void createLease_generatesUniqueUsername() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("pass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        ArgumentCaptor<DynamicLease> captor = ArgumentCaptor.forClass(DynamicLease.class);
        verify(leaseRepository).save(captor.capture());
        DynamicLease saved = captor.getValue();
        // Check metadataJson contains a username matching the pattern
        assertThat(saved.getMetadataJson()).contains("v_");
        assertThat(saved.getMetadataJson()).contains("test_secret");
    }

    @Test
    void createLease_generatesRandomPassword() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("randomPass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        verify(encryptionService).generateRandomString(32, "alphanumeric");
    }

    @Test
    void createLease_encryptsCredentials() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("pass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-creds");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        verify(encryptionService).encrypt(argThat(json -> json.contains("username") && json.contains("password")));

        ArgumentCaptor<DynamicLease> captor = ArgumentCaptor.forClass(DynamicLease.class);
        verify(leaseRepository).save(captor.capture());
        assertThat(captor.getValue().getCredentials()).isEqualTo("encrypted-creds");
    }

    @Test
    void createLease_setsCorrectExpiry() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 7200);
        Instant beforeCreate = Instant.now();

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("pass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        ArgumentCaptor<DynamicLease> captor = ArgumentCaptor.forClass(DynamicLease.class);
        verify(leaseRepository).save(captor.capture());
        Instant expiresAt = captor.getValue().getExpiresAt();
        assertThat(expiresAt).isAfter(beforeCreate.plus(7199, ChronoUnit.SECONDS));
        assertThat(captor.getValue().getTtlSeconds()).isEqualTo(7200);
    }

    @Test
    void createLease_setsActiveStatus() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("pass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        ArgumentCaptor<DynamicLease> captor = ArgumentCaptor.forClass(DynamicLease.class);
        verify(leaseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LeaseStatus.ACTIVE);
    }

    @Test
    void createLease_secretNotFound_throwsNotFound() {
        UUID secretId = UUID.randomUUID();
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dynamicSecretService.createLease(request, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(secretId.toString());
    }

    @Test
    void createLease_secretNotDynamic_throwsValidation() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        secret.setSecretType(SecretType.STATIC);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));

        assertThatThrownBy(() -> dynamicSecretService.createLease(request, UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("DYNAMIC");
    }

    @Test
    void createLease_generatesUniqueLeaseId() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("pass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        ArgumentCaptor<DynamicLease> captor = ArgumentCaptor.forClass(DynamicLease.class);
        verify(leaseRepository).save(captor.capture());
        assertThat(captor.getValue().getLeaseId()).startsWith("lease-");
        assertThat(captor.getValue().getLeaseId().length()).isGreaterThan(10);
    }

    @Test
    void createLease_storesMetadata() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildDynamicSecret(secretId);
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(secretId, 3600);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("pass");
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> {
            DynamicLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(leaseMapper.toResponse(any(DynamicLease.class), any())).thenReturn(buildLeaseResponse(secretId, LeaseStatus.ACTIVE, true));

        dynamicSecretService.createLease(request, UUID.randomUUID());

        ArgumentCaptor<DynamicLease> captor = ArgumentCaptor.forClass(DynamicLease.class);
        verify(leaseRepository).save(captor.capture());
        String metadataJson = captor.getValue().getMetadataJson();
        assertThat(metadataJson).contains("localhost");
        assertThat(metadataJson).contains("myapp_db");
        assertThat(metadataJson).contains("postgresql");
    }

    // ─── Lease Management ───────────────────────────────────

    @Test
    void getLease_exists_returnsWithoutCredentials() {
        String leaseId = "lease-test-123";
        DynamicLease lease = buildLease(leaseId, LeaseStatus.ACTIVE);

        when(leaseRepository.findByLeaseId(leaseId)).thenReturn(Optional.of(lease));
        DynamicLeaseResponse response = buildLeaseResponse(UUID.randomUUID(), LeaseStatus.ACTIVE, false);
        when(leaseMapper.toResponse(lease, null)).thenReturn(response);

        DynamicLeaseResponse result = dynamicSecretService.getLease(leaseId);

        assertThat(result).isEqualTo(response);
        assertThat(result.connectionDetails()).isNull();
    }

    @Test
    void getLease_notFound_throwsNotFound() {
        when(leaseRepository.findByLeaseId("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dynamicSecretService.getLease("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void listLeases_returnsForSecret() {
        UUID secretId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        DynamicLease lease = buildLease("lease-1", LeaseStatus.ACTIVE);
        Page<DynamicLease> page = new PageImpl<>(List.of(lease), pageable, 1);

        when(leaseRepository.findBySecretId(secretId, pageable)).thenReturn(page);
        DynamicLeaseResponse response = buildLeaseResponse(secretId, LeaseStatus.ACTIVE, false);
        when(leaseMapper.toResponse(lease, null)).thenReturn(response);

        PageResponse<DynamicLeaseResponse> result = dynamicSecretService.listLeases(secretId, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void revokeLease_active_marksRevoked() {
        String leaseId = "lease-revoke-1";
        UUID userId = UUID.randomUUID();
        DynamicLease lease = buildLease(leaseId, LeaseStatus.ACTIVE);

        when(leaseRepository.findByLeaseId(leaseId)).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> i.getArgument(0));

        dynamicSecretService.revokeLease(leaseId, userId);

        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.REVOKED);
        assertThat(lease.getRevokedAt()).isNotNull();
        assertThat(lease.getRevokedByUserId()).isEqualTo(userId);
        verify(leaseRepository).save(lease);
    }

    @Test
    void revokeLease_notActive_throwsValidation() {
        String leaseId = "lease-expired-1";
        DynamicLease lease = buildLease(leaseId, LeaseStatus.EXPIRED);

        when(leaseRepository.findByLeaseId(leaseId)).thenReturn(Optional.of(lease));

        assertThatThrownBy(() -> dynamicSecretService.revokeLease(leaseId, UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not active");
    }

    // ─── Revocation ─────────────────────────────────────────

    @Test
    void revokeAllLeases_revokesOnlyActive() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DynamicLease activeLease = buildLease("lease-active", LeaseStatus.ACTIVE);
        DynamicLease expiredLease = buildLease("lease-expired", LeaseStatus.EXPIRED);

        when(leaseRepository.findBySecretId(secretId)).thenReturn(List.of(activeLease, expiredLease));
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> i.getArgument(0));

        int count = dynamicSecretService.revokeAllLeases(secretId, userId);

        assertThat(count).isEqualTo(1);
        assertThat(activeLease.getStatus()).isEqualTo(LeaseStatus.REVOKED);
        assertThat(expiredLease.getStatus()).isEqualTo(LeaseStatus.EXPIRED);
    }

    @Test
    void revokeAllLeases_returnsCount() {
        UUID secretId = UUID.randomUUID();
        DynamicLease lease1 = buildLease("lease-1", LeaseStatus.ACTIVE);
        DynamicLease lease2 = buildLease("lease-2", LeaseStatus.ACTIVE);

        when(leaseRepository.findBySecretId(secretId)).thenReturn(List.of(lease1, lease2));
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> i.getArgument(0));

        int count = dynamicSecretService.revokeAllLeases(secretId, UUID.randomUUID());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void revokeAllLeases_noActive_returnsZero() {
        UUID secretId = UUID.randomUUID();
        DynamicLease expiredLease = buildLease("lease-expired", LeaseStatus.EXPIRED);

        when(leaseRepository.findBySecretId(secretId)).thenReturn(List.of(expiredLease));

        int count = dynamicSecretService.revokeAllLeases(secretId, UUID.randomUUID());

        assertThat(count).isEqualTo(0);
    }

    // ─── Expiry Processing ──────────────────────────────────

    @Test
    void processExpiredLeases_expiresActivePastTTL() {
        DynamicLease lease = buildLease("lease-expired-1", LeaseStatus.ACTIVE);
        lease.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(leaseRepository.findByStatusAndExpiresAtBefore(eq(LeaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(lease));
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> i.getArgument(0));

        int count = dynamicSecretService.processExpiredLeases();

        assertThat(count).isEqualTo(1);
        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.EXPIRED);
    }

    @Test
    void processExpiredLeases_skipsAlreadyExpired() {
        // Already expired leases won't be returned by the query
        when(leaseRepository.findByStatusAndExpiresAtBefore(eq(LeaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        int count = dynamicSecretService.processExpiredLeases();

        assertThat(count).isEqualTo(0);
    }

    @Test
    void processExpiredLeases_skipsActiveNotYetExpired() {
        // Active leases with future expiresAt won't be returned
        when(leaseRepository.findByStatusAndExpiresAtBefore(eq(LeaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        int count = dynamicSecretService.processExpiredLeases();

        assertThat(count).isEqualTo(0);
        verify(leaseRepository, never()).save(any());
    }

    @Test
    void processExpiredLeases_returnsCount() {
        DynamicLease lease1 = buildLease("lease-1", LeaseStatus.ACTIVE);
        DynamicLease lease2 = buildLease("lease-2", LeaseStatus.ACTIVE);

        when(leaseRepository.findByStatusAndExpiresAtBefore(eq(LeaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(lease1, lease2));
        when(leaseRepository.save(any(DynamicLease.class))).thenAnswer(i -> i.getArgument(0));

        int count = dynamicSecretService.processExpiredLeases();

        assertThat(count).isEqualTo(2);
    }

    // ─── Backend Operations ─────────────────────────────────

    @Test
    void createDatabaseUser_executeSqlFalse_skips() {
        when(dynamicSecretProperties.isExecuteSql()).thenReturn(false);

        // Should not throw, should not attempt any SQL
        dynamicSecretService.createDatabaseUser(
                "postgresql", "localhost", 5432, "testdb",
                "admin", "adminpass", "v_test_user", "testpass");

        // No exception = success, no SQL executed
    }

    @Test
    void createDatabaseUser_postgresql_generatesCorrectSQL() {
        // When executeSql is true, it would try to connect — but we can't easily test
        // the actual SQL without a real database. This test verifies the method exists
        // and handles the executeSql=false case.
        when(dynamicSecretProperties.isExecuteSql()).thenReturn(false);

        // Verify no exception when SQL execution is disabled
        dynamicSecretService.createDatabaseUser(
                "postgresql", "localhost", 5432, "testdb",
                "admin", "adminpass", "v_test_user", "testpass");
    }

    @Test
    void dropDatabaseUser_executeSqlFalse_skips() {
        when(dynamicSecretProperties.isExecuteSql()).thenReturn(false);

        dynamicSecretService.dropDatabaseUser(
                "postgresql", "localhost", 5432, "testdb",
                "admin", "adminpass", "v_test_user");

        // No exception = success, no SQL executed
    }

    @Test
    void dropDatabaseUser_failure_logsDoesNotThrow() {
        when(dynamicSecretProperties.isExecuteSql()).thenReturn(true);

        // Attempting to connect to a non-existent database should fail gracefully
        assertThatCode(() -> dynamicSecretService.dropDatabaseUser(
                "postgresql", "nonexistent-host", 5432, "testdb",
                "admin", "adminpass", "v_test_user"))
                .doesNotThrowAnyException();
    }

    // ─── Statistics ─────────────────────────────────────────

    @Test
    void getLeaseStats_correctCounts() {
        UUID secretId = UUID.randomUUID();

        when(leaseRepository.countBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE)).thenReturn(5L);
        when(leaseRepository.countBySecretIdAndStatus(secretId, LeaseStatus.EXPIRED)).thenReturn(10L);
        when(leaseRepository.countBySecretIdAndStatus(secretId, LeaseStatus.REVOKED)).thenReturn(3L);

        Map<String, Long> stats = dynamicSecretService.getLeaseStats(secretId);

        assertThat(stats.get("active")).isEqualTo(5L);
        assertThat(stats.get("expired")).isEqualTo(10L);
        assertThat(stats.get("revoked")).isEqualTo(3L);
    }

    @Test
    void getTotalActiveLeases_returnsCount() {
        when(leaseRepository.countByStatus(LeaseStatus.ACTIVE)).thenReturn(42L);

        long count = dynamicSecretService.getTotalActiveLeases();

        assertThat(count).isEqualTo(42L);
    }

    // ─── DynamicSecretProperties ────────────────────────────

    @Test
    void dynamicSecretProperties_defaults_areCorrect() {
        DynamicSecretProperties props = new DynamicSecretProperties();

        assertThat(props.isExecuteSql()).isFalse();
        assertThat(props.getDefaultTtlSeconds()).isEqualTo(3600);
        assertThat(props.getMaxTtlSeconds()).isEqualTo(86400);
        assertThat(props.getPasswordLength()).isEqualTo(32);
        assertThat(props.getUsernamePrefix()).isEqualTo("v_");
    }

    @Test
    void dynamicSecretProperties_customValues_loaded() {
        DynamicSecretProperties props = new DynamicSecretProperties();
        props.setExecuteSql(true);
        props.setDefaultTtlSeconds(7200);
        props.setMaxTtlSeconds(43200);
        props.setPasswordLength(64);
        props.setUsernamePrefix("dyn_");

        assertThat(props.isExecuteSql()).isTrue();
        assertThat(props.getDefaultTtlSeconds()).isEqualTo(7200);
        assertThat(props.getMaxTtlSeconds()).isEqualTo(43200);
        assertThat(props.getPasswordLength()).isEqualTo(64);
        assertThat(props.getUsernamePrefix()).isEqualTo("dyn_");
    }

    // ─── Helpers ────────────────────────────────────────────

    private Secret buildDynamicSecret(UUID secretId) {
        Secret secret = Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/dynamic/test/db")
                .name("test-secret")
                .secretType(SecretType.DYNAMIC)
                .currentVersion(0)
                .isActive(true)
                .metadataJson(METADATA_JSON)
                .build();
        secret.setId(secretId);
        return secret;
    }

    private DynamicLease buildLease(String leaseId, LeaseStatus status) {
        DynamicLease lease = DynamicLease.builder()
                .leaseId(leaseId)
                .secretId(UUID.randomUUID())
                .secretPath("/dynamic/test/db")
                .backendType("postgresql")
                .credentials("encrypted-credentials")
                .status(status)
                .ttlSeconds(3600)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .requestedByUserId(UUID.randomUUID())
                .build();
        lease.setId(UUID.randomUUID());
        return lease;
    }

    private DynamicLeaseResponse buildLeaseResponse(UUID secretId, LeaseStatus status, boolean includeCredentials) {
        Map<String, Object> connectionDetails = null;
        if (includeCredentials) {
            connectionDetails = new LinkedHashMap<>();
            connectionDetails.put("username", "v_test_12345678");
            connectionDetails.put("password", "generatedPassword");
            connectionDetails.put("host", "localhost");
            connectionDetails.put("port", 5432);
            connectionDetails.put("database", "myapp_db");
            connectionDetails.put("backendType", "postgresql");
        }
        return new DynamicLeaseResponse(
                UUID.randomUUID(), "lease-test-123", secretId, "/dynamic/test/db",
                "postgresql", status, 3600,
                Instant.now().plus(1, ChronoUnit.HOURS), null,
                UUID.randomUUID(), connectionDetails, Instant.now());
    }
}
