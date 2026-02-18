package com.codeops.vault.service;

import com.codeops.vault.config.AppConstants;
import com.codeops.vault.dto.mapper.SecretMapper;
import com.codeops.vault.dto.request.CreateSecretRequest;
import com.codeops.vault.dto.request.UpdateSecretRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.SecretResponse;
import com.codeops.vault.dto.response.SecretValueResponse;
import com.codeops.vault.dto.response.SecretVersionResponse;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.SecretMetadata;
import com.codeops.vault.entity.SecretVersion;
import com.codeops.vault.entity.enums.SecretType;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import com.codeops.vault.repository.SecretMetadataRepository;
import com.codeops.vault.repository.SecretRepository;
import com.codeops.vault.repository.SecretVersionRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecretService}.
 *
 * <p>Uses Mockito to mock all repositories and EncryptionService.
 * Tests pure business logic: CRUD, versioning, retention, metadata,
 * path hierarchy, and statistics.</p>
 */
@ExtendWith(MockitoExtension.class)
class SecretServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private SecretMetadataRepository secretMetadataRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private SecretMapper secretMapper;

    @InjectMocks
    private SecretService secretService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SECRET_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    // ═══════════════════════════════════════════
    //  Create Tests
    // ═══════════════════════════════════════════

    @Test
    void createSecret_success_returnsResponse() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/test/path", "test-secret", "my-secret-value", "desc",
                SecretType.STATIC, null, null, null, null, null);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/test/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(invocation -> {
            Secret s = invocation.getArgument(0);
            s.setId(SECRET_ID);
            s.setCreatedAt(NOW);
            s.setUpdatedAt(NOW);
            return s;
        });
        when(encryptionService.encrypt("my-secret-value")).thenReturn("encrypted-value");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        SecretResponse expectedResponse = buildSecretResponse();
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(expectedResponse);

        SecretResponse result = secretService.createSecret(request, TEAM_ID, USER_ID);

        assertThat(result).isNotNull();
        verify(secretRepository).save(any(Secret.class));
        verify(secretVersionRepository).save(any(SecretVersion.class));
        verify(encryptionService).encrypt("my-secret-value");
    }

    @Test
    void createSecret_duplicatePath_throwsValidation() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/existing/path", "dup-secret", "value", null,
                SecretType.STATIC, null, null, null, null, null);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/existing/path")).thenReturn(true);

        assertThatThrownBy(() -> secretService.createSecret(request, TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createSecret_withMetadata_savesAllPairs() {
        Map<String, String> metadata = Map.of("env", "prod", "team", "backend");
        CreateSecretRequest request = new CreateSecretRequest(
                "/meta/path", "meta-secret", "value", null,
                SecretType.STATIC, null, null, null, null, metadata);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/meta/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(invocation -> {
            Secret s = invocation.getArgument(0);
            s.setId(SECRET_ID);
            return s;
        });
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        secretService.createSecret(request, TEAM_ID, USER_ID);

        verify(secretMetadataRepository, times(2)).save(any(SecretMetadata.class));
    }

    @Test
    void createSecret_referenceType_noEncryption() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/ref/path", "ref-secret", "arn:aws:secretsmanager:us-east-1:123:secret:prod",
                null, SecretType.REFERENCE, "arn:aws:secretsmanager:us-east-1:123:secret:prod",
                null, null, null, null);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/ref/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(invocation -> {
            Secret s = invocation.getArgument(0);
            s.setId(SECRET_ID);
            return s;
        });
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        secretService.createSecret(request, TEAM_ID, USER_ID);

        verify(encryptionService, never()).encrypt(anyString());
        verify(secretVersionRepository, never()).save(any(SecretVersion.class));

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getReferenceArn())
                .isEqualTo("arn:aws:secretsmanager:us-east-1:123:secret:prod");
    }

    @Test
    void createSecret_setsCurrentVersionTo1() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/ver/path", "ver-secret", "value", null,
                SecretType.STATIC, null, null, null, null, null);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/ver/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> {
            Secret s = i.getArgument(0);
            s.setId(SECRET_ID);
            return s;
        });
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        secretService.createSecret(request, TEAM_ID, USER_ID);

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void createSecret_callsEncryptionService() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/enc/path", "enc-secret", "super-secret-value", null,
                SecretType.STATIC, null, null, null, null, null);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/enc/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> {
            Secret s = i.getArgument(0);
            s.setId(SECRET_ID);
            return s;
        });
        when(encryptionService.encrypt("super-secret-value")).thenReturn("encrypted-payload");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        secretService.createSecret(request, TEAM_ID, USER_ID);

        verify(encryptionService).encrypt("super-secret-value");

        ArgumentCaptor<SecretVersion> captor = ArgumentCaptor.forClass(SecretVersion.class);
        verify(secretVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedValue()).isEqualTo("encrypted-payload");
    }

    @Test
    void createSecret_setsOwnerUserId() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/own/path", "own-secret", "value", null,
                SecretType.STATIC, null, null, null, null, null);

        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/own/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> {
            Secret s = i.getArgument(0);
            s.setId(SECRET_ID);
            return s;
        });
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        secretService.createSecret(request, TEAM_ID, USER_ID);

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(USER_ID);
    }

    @Test
    void createSecret_setsTimestamps() {
        CreateSecretRequest request = new CreateSecretRequest(
                "/ts/path", "ts-secret", "value", null,
                SecretType.STATIC, null, null, null, null, null);

        Instant fixedTime = Instant.parse("2026-01-15T10:00:00Z");
        when(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/ts/path")).thenReturn(false);
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> {
            Secret s = i.getArgument(0);
            s.setId(SECRET_ID);
            s.setCreatedAt(fixedTime);
            s.setUpdatedAt(fixedTime);
            return s;
        });
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));

        SecretResponse response = new SecretResponse(SECRET_ID, TEAM_ID, "/ts/path", "ts-secret",
                null, SecretType.STATIC, 1, null, null, null, null, null,
                USER_ID, null, true, Map.of(), fixedTime, fixedTime);
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(response);

        SecretResponse result = secretService.createSecret(request, TEAM_ID, USER_ID);

        assertThat(result.createdAt()).isEqualTo(fixedTime);
        assertThat(result.updatedAt()).isEqualTo(fixedTime);
    }

    // ═══════════════════════════════════════════
    //  Read Tests
    // ═══════════════════════════════════════════

    @Test
    void getSecretById_exists_returnsResponse() {
        Secret secret = buildSecret();
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(eq(secret), anyMap())).thenReturn(buildSecretResponse());

        SecretResponse result = secretService.getSecretById(SECRET_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(SECRET_ID);
    }

    @Test
    void getSecretById_notFound_throwsNotFound() {
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.getSecretById(SECRET_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Secret");
    }

    @Test
    void getSecretByPath_exists_returnsResponse() {
        Secret secret = buildSecret();
        when(secretRepository.findByTeamIdAndPath(TEAM_ID, "/test/path")).thenReturn(Optional.of(secret));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(eq(secret), anyMap())).thenReturn(buildSecretResponse());

        SecretResponse result = secretService.getSecretByPath(TEAM_ID, "/test/path");

        assertThat(result).isNotNull();
    }

    @Test
    void getSecretByPath_notFound_throwsNotFound() {
        when(secretRepository.findByTeamIdAndPath(TEAM_ID, "/missing/path")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.getSecretByPath(TEAM_ID, "/missing/path"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("path");
    }

    @Test
    void readSecretValue_decryptsCurrentVersion() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(2);
        SecretVersion version = buildVersion(secret, 2);
        version.setEncryptedValue("encrypted-data");

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(SECRET_ID, 2))
                .thenReturn(Optional.of(version));
        when(encryptionService.decrypt("encrypted-data")).thenReturn("decrypted-value");
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));

        SecretValueResponse result = secretService.readSecretValue(SECRET_ID);

        assertThat(result.value()).isEqualTo("decrypted-value");
        assertThat(result.versionNumber()).isEqualTo(2);
        verify(encryptionService).decrypt("encrypted-data");

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getLastAccessedAt()).isNotNull();
    }

    @Test
    void readSecretValue_notFound_throwsNotFound() {
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.readSecretValue(SECRET_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void readSecretVersionValue_specificVersion_decrypts() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(3);
        SecretVersion version = buildVersion(secret, 2);
        version.setEncryptedValue("encrypted-v2");

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(SECRET_ID, 2))
                .thenReturn(Optional.of(version));
        when(encryptionService.decrypt("encrypted-v2")).thenReturn("plaintext-v2");
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));

        SecretValueResponse result = secretService.readSecretVersionValue(SECRET_ID, 2);

        assertThat(result.value()).isEqualTo("plaintext-v2");
        assertThat(result.versionNumber()).isEqualTo(2);
    }

    @Test
    void readSecretVersionValue_destroyedVersion_throwsValidation() {
        Secret secret = buildSecret();
        SecretVersion version = buildVersion(secret, 1);
        version.setIsDestroyed(true);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(SECRET_ID, 1))
                .thenReturn(Optional.of(version));

        assertThatThrownBy(() -> secretService.readSecretVersionValue(SECRET_ID, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("destroyed");
    }

    // ═══════════════════════════════════════════
    //  List Tests
    // ═══════════════════════════════════════════

    @Test
    void listSecrets_noFilters_returnsPaginated() {
        Secret secret = buildSecret();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Secret> page = new PageImpl<>(List.of(secret), pageable, 1);

        when(secretRepository.findByTeamId(TEAM_ID, pageable)).thenReturn(page);
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        PageResponse<SecretResponse> result = secretService.listSecrets(
                TEAM_ID, null, null, false, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void listSecrets_byType_filtersCorrectly() {
        Secret secret = buildSecret();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Secret> page = new PageImpl<>(List.of(secret), pageable, 1);

        when(secretRepository.findByTeamIdAndSecretType(TEAM_ID, SecretType.STATIC, pageable))
                .thenReturn(page);
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        PageResponse<SecretResponse> result = secretService.listSecrets(
                TEAM_ID, SecretType.STATIC, null, false, pageable);

        assertThat(result.content()).hasSize(1);
        verify(secretRepository).findByTeamIdAndSecretType(TEAM_ID, SecretType.STATIC, pageable);
    }

    @Test
    void listSecrets_byPathPrefix_filtersCorrectly() {
        Secret secret = buildSecret();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Secret> page = new PageImpl<>(List.of(secret), pageable, 1);

        when(secretRepository.findByTeamIdAndPathStartingWith(TEAM_ID, "/services/", pageable))
                .thenReturn(page);
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        PageResponse<SecretResponse> result = secretService.listSecrets(
                TEAM_ID, null, "/services/", false, pageable);

        assertThat(result.content()).hasSize(1);
        verify(secretRepository).findByTeamIdAndPathStartingWith(TEAM_ID, "/services/", pageable);
    }

    @Test
    void listSecrets_activeOnly_excludesInactive() {
        Secret secret = buildSecret();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Secret> page = new PageImpl<>(List.of(secret), pageable, 1);

        when(secretRepository.findByTeamIdAndIsActiveTrue(TEAM_ID, pageable)).thenReturn(page);
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        PageResponse<SecretResponse> result = secretService.listSecrets(
                TEAM_ID, null, null, true, pageable);

        assertThat(result.content()).hasSize(1);
        verify(secretRepository).findByTeamIdAndIsActiveTrue(TEAM_ID, pageable);
    }

    @Test
    void searchSecrets_matchesName_caseInsensitive() {
        Secret secret = buildSecret();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Secret> page = new PageImpl<>(List.of(secret), pageable, 1);

        when(secretRepository.findByTeamIdAndNameContainingIgnoreCase(TEAM_ID, "test", pageable))
                .thenReturn(page);
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        PageResponse<SecretResponse> result = secretService.searchSecrets(TEAM_ID, "test", pageable);

        assertThat(result.content()).hasSize(1);
        verify(secretRepository).findByTeamIdAndNameContainingIgnoreCase(TEAM_ID, "test", pageable);
    }

    @Test
    void listPaths_returnsDistinctPaths() {
        Secret s1 = buildSecret();
        s1.setPath("/services/app1/db");
        Secret s2 = buildSecret();
        s2.setPath("/services/app1/cache");
        Secret s3 = buildSecret();
        s3.setPath("/services/app2/db");

        when(secretRepository.findByTeamIdAndPathStartingWithAndIsActiveTrue(TEAM_ID, "/services/"))
                .thenReturn(List.of(s1, s2, s3));

        List<String> paths = secretService.listPaths(TEAM_ID, "/services/");

        assertThat(paths).containsExactly(
                "/services/app1/cache",
                "/services/app1/db",
                "/services/app2/db");
    }

    // ═══════════════════════════════════════════
    //  Update Tests
    // ═══════════════════════════════════════════

    @Test
    void updateSecret_withNewValue_createsNewVersion() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(1);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(encryptionService.encrypt("new-value")).thenReturn("encrypted-new");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        UpdateSecretRequest request = new UpdateSecretRequest(
                "new-value", "updated password", null, null, null, null, null);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        ArgumentCaptor<SecretVersion> captor = ArgumentCaptor.forClass(SecretVersion.class);
        verify(secretVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(2);
        assertThat(captor.getValue().getEncryptedValue()).isEqualTo("encrypted-new");

        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(secretCaptor.capture());
        assertThat(secretCaptor.getValue().getCurrentVersion()).isEqualTo(2);
    }

    @Test
    void updateSecret_withNewValue_callsRetentionPolicy() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(2);
        secret.setMaxVersions(2);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        SecretVersion v3 = buildVersion(secret, 3);
        SecretVersion v2 = buildVersion(secret, 2);
        SecretVersion v1 = buildVersion(secret, 1);
        when(secretVersionRepository.findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(SECRET_ID))
                .thenReturn(List.of(v3, v2, v1));

        UpdateSecretRequest request = new UpdateSecretRequest(
                "updated-value", null, null, null, null, null, null);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        verify(secretVersionRepository)
                .findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(SECRET_ID);
    }

    @Test
    void updateSecret_withoutValue_onlyUpdatesMetadataFields() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(1);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        UpdateSecretRequest request = new UpdateSecretRequest(
                null, null, "new description", null, null, null, null);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        verify(encryptionService, never()).encrypt(anyString());
        verify(secretVersionRepository, never()).save(any(SecretVersion.class));

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void updateSecret_updateDescription_changesDescription() {
        Secret secret = buildSecret();

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        UpdateSecretRequest request = new UpdateSecretRequest(
                null, null, "updated description", null, null, null, null);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("updated description");
    }

    @Test
    void updateSecret_updateMaxVersions_changesRetention() {
        Secret secret = buildSecret();

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        UpdateSecretRequest request = new UpdateSecretRequest(
                null, null, null, 5, null, null, null);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getMaxVersions()).isEqualTo(5);
    }

    @Test
    void updateSecret_replaceMetadata_updatesMetadata() {
        Secret secret = buildSecret();

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        Map<String, String> newMetadata = Map.of("env", "staging", "version", "2.0");
        UpdateSecretRequest request = new UpdateSecretRequest(
                null, null, null, null, null, null, newMetadata);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        verify(secretMetadataRepository).deleteBySecretId(SECRET_ID);
        verify(secretMetadataRepository, times(2)).save(any(SecretMetadata.class));
    }

    @Test
    void updateSecret_notFound_throwsNotFound() {
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.empty());

        UpdateSecretRequest request = new UpdateSecretRequest(
                "new-value", null, null, null, null, null, null);

        assertThatThrownBy(() -> secretService.updateSecret(SECRET_ID, request, USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateSecret_setsChangeDescription() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(1);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        UpdateSecretRequest request = new UpdateSecretRequest(
                "new-val", "rotated credentials", null, null, null, null, null);

        secretService.updateSecret(SECRET_ID, request, USER_ID);

        ArgumentCaptor<SecretVersion> captor = ArgumentCaptor.forClass(SecretVersion.class);
        verify(secretVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getChangeDescription()).isEqualTo("rotated credentials");
    }

    // ═══════════════════════════════════════════
    //  Delete Tests
    // ═══════════════════════════════════════════

    @Test
    void softDeleteSecret_setsInactiveFalse() {
        Secret secret = buildSecret();
        secret.setIsActive(true);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));

        secretService.softDeleteSecret(SECRET_ID);

        ArgumentCaptor<Secret> captor = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    @Test
    void softDeleteSecret_notFound_throwsNotFound() {
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.softDeleteSecret(SECRET_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hardDeleteSecret_removesEverything() {
        Secret secret = buildSecret();

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));

        secretService.hardDeleteSecret(SECRET_ID);

        verify(secretMetadataRepository).deleteBySecretId(SECRET_ID);
        verify(secretRepository).delete(secret);
    }

    @Test
    void hardDeleteSecret_notFound_throwsNotFound() {
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.hardDeleteSecret(SECRET_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ═══════════════════════════════════════════
    //  Version Management Tests
    // ═══════════════════════════════════════════

    @Test
    void listVersions_returnsDescendingOrder() {
        Secret secret = buildSecret();
        SecretVersion v2 = buildVersion(secret, 2);
        SecretVersion v1 = buildVersion(secret, 1);
        Pageable pageable = PageRequest.of(0, 20);
        Page<SecretVersion> page = new PageImpl<>(List.of(v2, v1), pageable, 2);

        when(secretRepository.existsById(SECRET_ID)).thenReturn(true);
        when(secretVersionRepository.findBySecretId(SECRET_ID, pageable)).thenReturn(page);

        SecretVersionResponse r2 = buildVersionResponse(2);
        SecretVersionResponse r1 = buildVersionResponse(1);
        when(secretMapper.toVersionResponses(List.of(v2, v1))).thenReturn(List.of(r2, r1));

        PageResponse<SecretVersionResponse> result = secretService.listVersions(SECRET_ID, pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).versionNumber()).isEqualTo(2);
        assertThat(result.content().get(1).versionNumber()).isEqualTo(1);
    }

    @Test
    void getVersion_exists_returnsResponse() {
        Secret secret = buildSecret();
        SecretVersion version = buildVersion(secret, 3);

        when(secretVersionRepository.findBySecretIdAndVersionNumber(SECRET_ID, 3))
                .thenReturn(Optional.of(version));

        SecretVersionResponse expectedResponse = buildVersionResponse(3);
        when(secretMapper.toVersionResponse(version)).thenReturn(expectedResponse);

        SecretVersionResponse result = secretService.getVersion(SECRET_ID, 3);

        assertThat(result.versionNumber()).isEqualTo(3);
    }

    @Test
    void getVersion_notFound_throwsNotFound() {
        when(secretVersionRepository.findBySecretIdAndVersionNumber(SECRET_ID, 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.getVersion(SECRET_ID, 99))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void destroyVersion_zerosEncryptedValue() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(2);
        SecretVersion version = buildVersion(secret, 1);
        version.setEncryptedValue("encrypted-data");

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(SECRET_ID, 1))
                .thenReturn(Optional.of(version));
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));

        secretService.destroyVersion(SECRET_ID, 1);

        ArgumentCaptor<SecretVersion> captor = ArgumentCaptor.forClass(SecretVersion.class);
        verify(secretVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDestroyed()).isTrue();
        assertThat(captor.getValue().getEncryptedValue()).isEqualTo("DESTROYED");
    }

    @Test
    void destroyVersion_currentVersion_throwsValidation() {
        Secret secret = buildSecret();
        secret.setCurrentVersion(3);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));

        assertThatThrownBy(() -> secretService.destroyVersion(SECRET_ID, 3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("current version");
    }

    @Test
    void applyRetentionPolicy_maxVersions_destroysOldest() {
        Secret secret = buildSecret();
        secret.setMaxVersions(2);
        secret.setCurrentVersion(3);

        SecretVersion v3 = buildVersion(secret, 3);
        SecretVersion v2 = buildVersion(secret, 2);
        SecretVersion v1 = buildVersion(secret, 1);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(SECRET_ID))
                .thenReturn(List.of(v3, v2, v1));
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));

        secretService.applyRetentionPolicy(SECRET_ID);

        assertThat(v1.getIsDestroyed()).isTrue();
        assertThat(v1.getEncryptedValue()).isEqualTo("DESTROYED");
        assertThat(v2.getIsDestroyed()).isFalse();
        assertThat(v3.getIsDestroyed()).isFalse();
    }

    // ═══════════════════════════════════════════
    //  Retention Policy Tests
    // ═══════════════════════════════════════════

    @Test
    void applyRetentionPolicy_retentionDays_destroysExpired() {
        Secret secret = buildSecret();
        secret.setRetentionDays(30);
        secret.setCurrentVersion(3);

        SecretVersion v3 = buildVersion(secret, 3);
        v3.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));

        SecretVersion v2 = buildVersion(secret, 2);
        v2.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));

        SecretVersion v1 = buildVersion(secret, 1);
        v1.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(SECRET_ID))
                .thenReturn(List.of(v3, v2, v1));
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));

        secretService.applyRetentionPolicy(SECRET_ID);

        assertThat(v1.getIsDestroyed()).isTrue();
        assertThat(v2.getIsDestroyed()).isTrue();
        assertThat(v3.getIsDestroyed()).isFalse();
    }

    @Test
    void applyRetentionPolicy_currentVersionNeverDestroyed() {
        Secret secret = buildSecret();
        secret.setMaxVersions(1);
        secret.setCurrentVersion(3);

        SecretVersion v3 = buildVersion(secret, 3);
        SecretVersion v2 = buildVersion(secret, 2);
        SecretVersion v1 = buildVersion(secret, 1);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(SECRET_ID))
                .thenReturn(List.of(v3, v2, v1));
        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));

        secretService.applyRetentionPolicy(SECRET_ID);

        assertThat(v3.getIsDestroyed()).isFalse();
        assertThat(v2.getIsDestroyed()).isTrue();
        assertThat(v1.getIsDestroyed()).isTrue();
    }

    @Test
    void applyRetentionPolicy_noPolicy_doesNothing() {
        Secret secret = buildSecret();
        secret.setMaxVersions(null);
        secret.setRetentionDays(null);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));

        secretService.applyRetentionPolicy(SECRET_ID);

        verify(secretVersionRepository, never())
                .findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(any());
        verify(secretVersionRepository, never()).save(any(SecretVersion.class));
    }

    @Test
    void applyRetentionPolicy_alreadyDestroyed_skipped() {
        Secret secret = buildSecret();
        secret.setMaxVersions(3);
        secret.setCurrentVersion(5);

        SecretVersion v5 = buildVersion(secret, 5);
        SecretVersion v4 = buildVersion(secret, 4);
        SecretVersion v3 = buildVersion(secret, 3);

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(SECRET_ID))
                .thenReturn(List.of(v5, v4, v3));

        secretService.applyRetentionPolicy(SECRET_ID);

        verify(secretVersionRepository, never()).save(any(SecretVersion.class));
    }

    // ═══════════════════════════════════════════
    //  Metadata Tests
    // ═══════════════════════════════════════════

    @Test
    void getMetadata_returnsAllPairs() {
        SecretMetadata m1 = SecretMetadata.builder().metadataKey("env").metadataValue("prod").build();
        SecretMetadata m2 = SecretMetadata.builder().metadataKey("team").metadataValue("backend").build();

        when(secretRepository.existsById(SECRET_ID)).thenReturn(true);
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of(m1, m2));

        Map<String, String> result = secretService.getMetadata(SECRET_ID);

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("env", "prod");
        assertThat(result).containsEntry("team", "backend");
    }

    @Test
    void setMetadata_newKey_creates() {
        Secret secret = buildSecret();
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretMetadataRepository.findBySecretIdAndMetadataKey(SECRET_ID, "newKey"))
                .thenReturn(Optional.empty());
        when(secretMetadataRepository.save(any(SecretMetadata.class))).thenAnswer(i -> i.getArgument(0));

        secretService.setMetadata(SECRET_ID, "newKey", "newValue");

        ArgumentCaptor<SecretMetadata> captor = ArgumentCaptor.forClass(SecretMetadata.class);
        verify(secretMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadataKey()).isEqualTo("newKey");
        assertThat(captor.getValue().getMetadataValue()).isEqualTo("newValue");
    }

    @Test
    void setMetadata_existingKey_updates() {
        Secret secret = buildSecret();
        SecretMetadata existing = SecretMetadata.builder()
                .metadataKey("existingKey")
                .metadataValue("oldValue")
                .build();

        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretMetadataRepository.findBySecretIdAndMetadataKey(SECRET_ID, "existingKey"))
                .thenReturn(Optional.of(existing));
        when(secretMetadataRepository.save(any(SecretMetadata.class))).thenAnswer(i -> i.getArgument(0));

        secretService.setMetadata(SECRET_ID, "existingKey", "updatedValue");

        assertThat(existing.getMetadataValue()).isEqualTo("updatedValue");
        verify(secretMetadataRepository).save(existing);
    }

    @Test
    void removeMetadata_existingKey_deletes() {
        when(secretRepository.existsById(SECRET_ID)).thenReturn(true);

        secretService.removeMetadata(SECRET_ID, "keyToRemove");

        verify(secretMetadataRepository).deleteBySecretIdAndMetadataKey(SECRET_ID, "keyToRemove");
    }

    @Test
    void replaceMetadata_replacesAll() {
        Secret secret = buildSecret();
        when(secretRepository.findById(SECRET_ID)).thenReturn(Optional.of(secret));
        when(secretMetadataRepository.save(any(SecretMetadata.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, String> newMetadata = Map.of("k1", "v1", "k2", "v2", "k3", "v3");

        secretService.replaceMetadata(SECRET_ID, newMetadata);

        verify(secretMetadataRepository).deleteBySecretId(SECRET_ID);
        verify(secretMetadataRepository, times(3)).save(any(SecretMetadata.class));
    }

    // ═══════════════════════════════════════════
    //  Statistics Tests
    // ═══════════════════════════════════════════

    @Test
    void getSecretCounts_returnsByType() {
        when(secretRepository.countByTeamId(TEAM_ID)).thenReturn(10L);
        when(secretRepository.countByTeamIdAndSecretType(TEAM_ID, SecretType.STATIC)).thenReturn(6L);
        when(secretRepository.countByTeamIdAndSecretType(TEAM_ID, SecretType.DYNAMIC)).thenReturn(3L);
        when(secretRepository.countByTeamIdAndSecretType(TEAM_ID, SecretType.REFERENCE)).thenReturn(1L);

        Map<String, Long> counts = secretService.getSecretCounts(TEAM_ID);

        assertThat(counts).containsEntry("total", 10L);
        assertThat(counts).containsEntry("static", 6L);
        assertThat(counts).containsEntry("dynamic", 3L);
        assertThat(counts).containsEntry("reference", 1L);
    }

    @Test
    void getExpiringSecrets_withinWindow_returnsMatching() {
        Secret expiringSecret = buildSecret();
        expiringSecret.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        expiringSecret.setIsActive(true);

        when(secretRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(expiringSecret));
        when(secretMetadataRepository.findBySecretId(SECRET_ID)).thenReturn(List.of());
        when(secretMapper.toResponse(any(Secret.class), anyMap())).thenReturn(buildSecretResponse());

        List<SecretResponse> result = secretService.getExpiringSecrets(TEAM_ID, 3);

        assertThat(result).hasSize(1);
    }

    @Test
    void getExpiringSecrets_outsideWindow_returnsEmpty() {
        Secret farFutureSecret = buildSecret();
        farFutureSecret.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
        farFutureSecret.setIsActive(true);

        when(secretRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(farFutureSecret));

        List<SecretResponse> result = secretService.getExpiringSecrets(TEAM_ID, 3);

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════
    //  Test Helpers
    // ═══════════════════════════════════════════

    private Secret buildSecret() {
        Secret secret = Secret.builder()
                .teamId(TEAM_ID)
                .path("/test/path")
                .name("test-secret")
                .description("test description")
                .secretType(SecretType.STATIC)
                .currentVersion(1)
                .isActive(true)
                .ownerUserId(USER_ID)
                .build();
        secret.setId(SECRET_ID);
        secret.setCreatedAt(NOW);
        secret.setUpdatedAt(NOW);
        return secret;
    }

    private SecretVersion buildVersion(Secret secret, int versionNumber) {
        SecretVersion version = SecretVersion.builder()
                .secret(secret)
                .versionNumber(versionNumber)
                .encryptedValue("encrypted-v" + versionNumber)
                .encryptionKeyId(AppConstants.DEFAULT_ENCRYPTION_KEY_ID)
                .createdByUserId(USER_ID)
                .isDestroyed(false)
                .build();
        version.setId(UUID.randomUUID());
        version.setCreatedAt(NOW);
        return version;
    }

    private SecretResponse buildSecretResponse() {
        return new SecretResponse(SECRET_ID, TEAM_ID, "/test/path", "test-secret",
                "test description", SecretType.STATIC, 1, null, null, null,
                null, null, USER_ID, null, true, Map.of(), NOW, NOW);
    }

    private SecretVersionResponse buildVersionResponse(int versionNumber) {
        return new SecretVersionResponse(UUID.randomUUID(), SECRET_ID, versionNumber,
                AppConstants.DEFAULT_ENCRYPTION_KEY_ID, null, USER_ID, false, NOW);
    }
}
