package com.codeops.vault.service;

import com.codeops.vault.dto.mapper.RotationMapper;
import com.codeops.vault.dto.request.CreateRotationPolicyRequest;
import com.codeops.vault.dto.request.UpdateRotationPolicyRequest;
import com.codeops.vault.dto.request.UpdateSecretRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.RotationHistoryResponse;
import com.codeops.vault.dto.response.RotationPolicyResponse;
import com.codeops.vault.entity.RotationHistory;
import com.codeops.vault.entity.RotationPolicy;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.enums.RotationStrategy;
import com.codeops.vault.entity.enums.SecretType;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.repository.RotationHistoryRepository;
import com.codeops.vault.repository.RotationPolicyRepository;
import com.codeops.vault.repository.SecretRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RotationService}.
 *
 * <p>Covers policy CRUD, rotation execution with all strategies,
 * failure handling, due rotation processing, history retrieval,
 * and statistics.</p>
 */
@ExtendWith(MockitoExtension.class)
class RotationServiceTest {

    @Mock
    private RotationPolicyRepository rotationPolicyRepository;

    @Mock
    private RotationHistoryRepository rotationHistoryRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretService secretService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private RotationMapper rotationMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RotationService rotationService;

    // ─── Policy CRUD ────────────────────────────────────────

    @Test
    void createOrUpdatePolicy_newPolicy_createsSuccessfully() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        CreateRotationPolicyRequest request = new CreateRotationPolicyRequest(
                secretId, RotationStrategy.RANDOM_GENERATE, 24, 32, "alphanumeric",
                null, null, null, 5);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> {
            RotationPolicy p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        RotationPolicyResponse expectedResponse = buildPolicyResponse(secretId);
        when(rotationMapper.toResponse(any(RotationPolicy.class), eq(secret.getPath()))).thenReturn(expectedResponse);

        RotationPolicyResponse result = rotationService.createOrUpdatePolicy(request, userId);

        assertThat(result).isEqualTo(expectedResponse);
        ArgumentCaptor<RotationPolicy> captor = ArgumentCaptor.forClass(RotationPolicy.class);
        verify(rotationPolicyRepository).save(captor.capture());
        RotationPolicy saved = captor.getValue();
        assertThat(saved.getStrategy()).isEqualTo(RotationStrategy.RANDOM_GENERATE);
        assertThat(saved.getRotationIntervalHours()).isEqualTo(24);
        assertThat(saved.getNextRotationAt()).isNotNull();
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getFailureCount()).isEqualTo(0);
    }

    @Test
    void createOrUpdatePolicy_existingPolicy_updates() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy existing = buildPolicy(secretId, secret);

        CreateRotationPolicyRequest request = new CreateRotationPolicyRequest(
                secretId, RotationStrategy.EXTERNAL_API, 12, null, null,
                "https://api.example.com/rotate", null, null, 3);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(existing));
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        RotationPolicyResponse expectedResponse = buildPolicyResponse(secretId);
        when(rotationMapper.toResponse(any(RotationPolicy.class), eq(secret.getPath()))).thenReturn(expectedResponse);

        RotationPolicyResponse result = rotationService.createOrUpdatePolicy(request, userId);

        assertThat(result).isEqualTo(expectedResponse);
        verify(rotationPolicyRepository).save(existing);
        assertThat(existing.getStrategy()).isEqualTo(RotationStrategy.EXTERNAL_API);
        assertThat(existing.getRotationIntervalHours()).isEqualTo(12);
    }

    @Test
    void createOrUpdatePolicy_secretNotFound_throwsNotFound() {
        UUID secretId = UUID.randomUUID();
        CreateRotationPolicyRequest request = new CreateRotationPolicyRequest(
                secretId, RotationStrategy.RANDOM_GENERATE, 24, 32, "alphanumeric",
                null, null, null, 5);

        when(secretRepository.findById(secretId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotationService.createOrUpdatePolicy(request, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(secretId.toString());
    }

    @Test
    void getPolicy_exists_returnsResponse() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);

        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        RotationPolicyResponse expectedResponse = buildPolicyResponse(secretId);
        when(rotationMapper.toResponse(policy, secret.getPath())).thenReturn(expectedResponse);

        RotationPolicyResponse result = rotationService.getPolicy(secretId);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void updatePolicy_partialFields_onlyUpdatesProvided() {
        UUID policyId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setId(policyId);

        UpdateRotationPolicyRequest request = new UpdateRotationPolicyRequest(
                null, 48, null, null, null, null, null, 10, null);

        when(rotationPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        RotationPolicyResponse expectedResponse = buildPolicyResponse(secretId);
        when(rotationMapper.toResponse(any(RotationPolicy.class), eq(secret.getPath()))).thenReturn(expectedResponse);

        RotationPolicyResponse result = rotationService.updatePolicy(policyId, request);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(policy.getRotationIntervalHours()).isEqualTo(48);
        assertThat(policy.getMaxFailures()).isEqualTo(10);
        // Strategy should remain unchanged
        assertThat(policy.getStrategy()).isEqualTo(RotationStrategy.RANDOM_GENERATE);
    }

    @Test
    void deletePolicy_doesNotDeleteHistory() {
        UUID policyId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setId(policyId);

        when(rotationPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        rotationService.deletePolicy(policyId);

        verify(rotationPolicyRepository).delete(policy);
        verify(rotationHistoryRepository, never()).deleteAll(anyList());
    }

    // ─── Rotation Execution ─────────────────────────────────

    @Test
    void rotateSecret_randomGenerate_success() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        secret.setCurrentVersion(3);
        RotationPolicy policy = buildPolicy(secretId, secret);

        Secret updatedSecret = buildSecret(secretId);
        updatedSecret.setCurrentVersion(4);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret))
                .thenReturn(Optional.of(updatedSecret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("newRandomValue123");
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        RotationHistoryResponse expectedResponse = buildHistoryResponse(secretId, true);
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(expectedResponse);

        RotationHistoryResponse result = rotationService.rotateSecret(secretId, userId);

        assertThat(result).isEqualTo(expectedResponse);
        verify(secretService).updateSecret(eq(secretId), any(UpdateSecretRequest.class), eq(userId));

        ArgumentCaptor<RotationHistory> historyCaptor = ArgumentCaptor.forClass(RotationHistory.class);
        verify(rotationHistoryRepository).save(historyCaptor.capture());
        RotationHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getSuccess()).isTrue();
        assertThat(savedHistory.getPreviousVersion()).isEqualTo(3);
        assertThat(savedHistory.getNewVersion()).isEqualTo(4);
        assertThat(savedHistory.getTriggeredByUserId()).isEqualTo(userId);
    }

    @Test
    void rotateSecret_randomGenerate_correctLength() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setRandomLength(64);

        Secret updatedSecret = buildSecret(secretId);
        updatedSecret.setCurrentVersion(2);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret))
                .thenReturn(Optional.of(updatedSecret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(encryptionService.generateRandomString(64, "alphanumeric")).thenReturn("x".repeat(64));
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, true));

        rotationService.rotateSecret(secretId, null);

        verify(encryptionService).generateRandomString(64, "alphanumeric");
    }

    @Test
    void rotateSecret_randomGenerate_resetsFailureCount() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setFailureCount(3);

        Secret updatedSecret = buildSecret(secretId);
        updatedSecret.setCurrentVersion(2);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret))
                .thenReturn(Optional.of(updatedSecret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(encryptionService.generateRandomString(anyInt(), anyString())).thenReturn("newVal");
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, true));

        rotationService.rotateSecret(secretId, null);

        ArgumentCaptor<RotationPolicy> captor = ArgumentCaptor.forClass(RotationPolicy.class);
        verify(rotationPolicyRepository).save(captor.capture());
        assertThat(captor.getValue().getFailureCount()).isEqualTo(0);
    }

    @Test
    void rotateSecret_randomGenerate_advancesNextRotation() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setRotationIntervalHours(12);
        Instant beforeRotation = Instant.now();

        Secret updatedSecret = buildSecret(secretId);
        updatedSecret.setCurrentVersion(2);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret))
                .thenReturn(Optional.of(updatedSecret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(encryptionService.generateRandomString(anyInt(), anyString())).thenReturn("newVal");
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, true));

        rotationService.rotateSecret(secretId, null);

        assertThat(policy.getNextRotationAt()).isAfter(beforeRotation.plus(11, ChronoUnit.HOURS));
    }

    @Test
    void rotateSecret_externalApi_success() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setStrategy(RotationStrategy.EXTERNAL_API);
        policy.setExternalApiUrl("https://api.example.com/rotate");
        policy.setExternalApiHeaders("{\"Authorization\": \"Bearer token123\"}");

        Secret updatedSecret = buildSecret(secretId);
        updatedSecret.setCurrentVersion(2);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret))
                .thenReturn(Optional.of(updatedSecret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(restTemplate.exchange(eq("https://api.example.com/rotate"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("new-api-value", HttpStatus.OK));
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, true));

        RotationHistoryResponse result = rotationService.rotateSecret(secretId, null);

        assertThat(result.success()).isTrue();
        verify(secretService).updateSecret(eq(secretId), any(UpdateSecretRequest.class), isNull());
    }

    @Test
    void rotateSecret_externalApi_httpError_recordsFailure() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setStrategy(RotationStrategy.EXTERNAL_API);
        policy.setExternalApiUrl("https://api.example.com/rotate");

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("500 Internal Server Error"));
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        RotationHistoryResponse failedResponse = buildHistoryResponse(secretId, false);
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(failedResponse);

        RotationHistoryResponse result = rotationService.rotateSecret(secretId, null);

        assertThat(result.success()).isFalse();
        ArgumentCaptor<RotationHistory> captor = ArgumentCaptor.forClass(RotationHistory.class);
        verify(rotationHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorMessage()).contains("External API call failed");
    }

    @Test
    void rotateSecret_customScript_failsGracefully() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setStrategy(RotationStrategy.CUSTOM_SCRIPT);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        RotationHistoryResponse failedResponse = buildHistoryResponse(secretId, false);
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(failedResponse);

        RotationHistoryResponse result = rotationService.rotateSecret(secretId, null);

        assertThat(result.success()).isFalse();
        ArgumentCaptor<RotationHistory> captor = ArgumentCaptor.forClass(RotationHistory.class);
        verify(rotationHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).contains("not yet implemented");
    }

    @Test
    void rotateSecret_failure_incrementsFailureCount() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setFailureCount(1);
        policy.setStrategy(RotationStrategy.CUSTOM_SCRIPT);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, false));

        rotationService.rotateSecret(secretId, null);

        assertThat(policy.getFailureCount()).isEqualTo(2);
    }

    @Test
    void rotateSecret_exceedsMaxFailures_deactivatesPolicy() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setFailureCount(4);
        policy.setMaxFailures(5);
        policy.setStrategy(RotationStrategy.CUSTOM_SCRIPT);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, false));

        rotationService.rotateSecret(secretId, null);

        assertThat(policy.getFailureCount()).isEqualTo(5);
        assertThat(policy.getIsActive()).isFalse();
    }

    @Test
    void rotateSecret_secretNotFound_throwsNotFound() {
        UUID secretId = UUID.randomUUID();

        when(secretRepository.findById(secretId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotationService.rotateSecret(secretId, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(secretId.toString());
    }

    @Test
    void rotateSecret_noPolicyFound_throwsNotFound() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotationService.rotateSecret(secretId, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("RotationPolicy");
    }

    @Test
    void rotateSecret_manualTrigger_setsUserId() {
        UUID secretId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);

        Secret updatedSecret = buildSecret(secretId);
        updatedSecret.setCurrentVersion(2);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret))
                .thenReturn(Optional.of(updatedSecret));
        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(encryptionService.generateRandomString(anyInt(), anyString())).thenReturn("val");
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId, true));

        rotationService.rotateSecret(secretId, userId);

        ArgumentCaptor<RotationHistory> captor = ArgumentCaptor.forClass(RotationHistory.class);
        verify(rotationHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggeredByUserId()).isEqualTo(userId);
    }

    // ─── generateNewValue ───────────────────────────────────

    @Test
    void generateNewValue_randomGenerate_usesEncryptionService() {
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .randomLength(32)
                .randomCharset("alphanumeric")
                .build();

        when(encryptionService.generateRandomString(32, "alphanumeric")).thenReturn("generatedValue");

        String result = rotationService.generateNewValue(policy);

        assertThat(result).isEqualTo("generatedValue");
        verify(encryptionService).generateRandomString(32, "alphanumeric");
    }

    @Test
    void generateNewValue_randomGenerate_respectsCharset() {
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .randomLength(16)
                .randomCharset("hex")
                .build();

        when(encryptionService.generateRandomString(16, "hex")).thenReturn("a1b2c3d4e5f60718");

        String result = rotationService.generateNewValue(policy);

        assertThat(result).isEqualTo("a1b2c3d4e5f60718");
        verify(encryptionService).generateRandomString(16, "hex");
    }

    @Test
    void generateNewValue_externalApi_callsRestTemplate() {
        Secret secret = buildSecret(UUID.randomUUID());
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.EXTERNAL_API)
                .externalApiUrl("https://api.example.com/generate")
                .secret(secret)
                .build();

        when(restTemplate.exchange(eq("https://api.example.com/generate"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("api-generated-value", HttpStatus.OK));

        String result = rotationService.generateNewValue(policy);

        assertThat(result).isEqualTo("api-generated-value");
    }

    @Test
    void generateNewValue_customScript_throwsNotImplemented() {
        Secret secret = buildSecret(UUID.randomUUID());
        RotationPolicy policy = RotationPolicy.builder()
                .strategy(RotationStrategy.CUSTOM_SCRIPT)
                .scriptCommand("./generate.sh")
                .secret(secret)
                .build();

        assertThatThrownBy(() -> rotationService.generateNewValue(policy))
                .isInstanceOf(CodeOpsVaultException.class)
                .hasMessageContaining("not yet implemented");
    }

    // ─── processDueRotations ────────────────────────────────

    @Test
    void processDueRotations_noDue_returnsZero() {
        when(rotationPolicyRepository.findByIsActiveTrueAndNextRotationAtBefore(any(Instant.class)))
                .thenReturn(List.of());

        int count = rotationService.processDueRotations();

        assertThat(count).isEqualTo(0);
    }

    @Test
    void processDueRotations_multipleDue_processesAll() {
        UUID secretId1 = UUID.randomUUID();
        UUID secretId2 = UUID.randomUUID();
        Secret secret1 = buildSecret(secretId1);
        Secret secret2 = buildSecret(secretId2);
        RotationPolicy policy1 = buildPolicy(secretId1, secret1);
        RotationPolicy policy2 = buildPolicy(secretId2, secret2);

        Secret updatedSecret1 = buildSecret(secretId1);
        updatedSecret1.setCurrentVersion(2);
        Secret updatedSecret2 = buildSecret(secretId2);
        updatedSecret2.setCurrentVersion(2);

        when(rotationPolicyRepository.findByIsActiveTrueAndNextRotationAtBefore(any(Instant.class)))
                .thenReturn(List.of(policy1, policy2));
        when(secretRepository.findById(secretId1))
                .thenReturn(Optional.of(secret1))
                .thenReturn(Optional.of(updatedSecret1));
        when(secretRepository.findById(secretId2))
                .thenReturn(Optional.of(secret2))
                .thenReturn(Optional.of(updatedSecret2));
        when(rotationPolicyRepository.findBySecretId(secretId1)).thenReturn(Optional.of(policy1));
        when(rotationPolicyRepository.findBySecretId(secretId2)).thenReturn(Optional.of(policy2));
        when(encryptionService.generateRandomString(anyInt(), anyString())).thenReturn("val");
        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId1, true));

        int count = rotationService.processDueRotations();

        assertThat(count).isEqualTo(2);
        verify(secretService, times(2)).updateSecret(any(UUID.class), any(UpdateSecretRequest.class), isNull());
    }

    @Test
    void processDueRotations_oneFailsOthersContinue() {
        UUID secretId1 = UUID.randomUUID();
        UUID secretId2 = UUID.randomUUID();
        Secret secret1 = buildSecret(secretId1);
        Secret secret2 = buildSecret(secretId2);
        RotationPolicy policy1 = buildPolicy(secretId1, secret1);
        policy1.setStrategy(RotationStrategy.CUSTOM_SCRIPT); // Will fail
        RotationPolicy policy2 = buildPolicy(secretId2, secret2);

        Secret updatedSecret2 = buildSecret(secretId2);
        updatedSecret2.setCurrentVersion(2);

        when(rotationPolicyRepository.findByIsActiveTrueAndNextRotationAtBefore(any(Instant.class)))
                .thenReturn(List.of(policy1, policy2));

        // Policy1 (CUSTOM_SCRIPT) - will fail in generateNewValue, but rotateSecret catches it
        when(secretRepository.findById(secretId1)).thenReturn(Optional.of(secret1));
        when(rotationPolicyRepository.findBySecretId(secretId1)).thenReturn(Optional.of(policy1));

        // Policy2 (RANDOM_GENERATE) - will succeed
        when(secretRepository.findById(secretId2))
                .thenReturn(Optional.of(secret2))
                .thenReturn(Optional.of(updatedSecret2));
        when(rotationPolicyRepository.findBySecretId(secretId2)).thenReturn(Optional.of(policy2));
        when(encryptionService.generateRandomString(anyInt(), anyString())).thenReturn("newVal");

        when(rotationHistoryRepository.save(any(RotationHistory.class))).thenAnswer(i -> {
            RotationHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(rotationPolicyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));
        when(rotationMapper.toHistoryResponse(any(RotationHistory.class))).thenReturn(buildHistoryResponse(secretId2, true));

        int count = rotationService.processDueRotations();

        // Both are attempted, so count = 2
        assertThat(count).isEqualTo(2);
    }

    @Test
    void processDueRotations_inactiveSkipped() {
        // findByIsActiveTrueAndNextRotationAtBefore already filters inactive, so
        // returning empty list means nothing is processed
        when(rotationPolicyRepository.findByIsActiveTrueAndNextRotationAtBefore(any(Instant.class)))
                .thenReturn(List.of());

        int count = rotationService.processDueRotations();

        assertThat(count).isEqualTo(0);
        verify(secretService, never()).updateSecret(any(), any(), any());
    }

    // ─── History ────────────────────────────────────────────

    @Test
    void getRotationHistory_returnsPaginated() {
        UUID secretId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        RotationHistory history = RotationHistory.builder()
                .secretId(secretId)
                .secretPath("/test/secret")
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .success(true)
                .build();
        history.setId(UUID.randomUUID());

        Page<RotationHistory> page = new PageImpl<>(List.of(history), pageable, 1);
        when(rotationHistoryRepository.findBySecretId(secretId, pageable)).thenReturn(page);
        RotationHistoryResponse response = buildHistoryResponse(secretId, true);
        when(rotationMapper.toHistoryResponse(history)).thenReturn(response);

        PageResponse<RotationHistoryResponse> result = rotationService.getRotationHistory(secretId, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getLastSuccessfulRotation_returnsLatest() {
        UUID secretId = UUID.randomUUID();
        RotationHistory history = RotationHistory.builder()
                .secretId(secretId)
                .secretPath("/test/secret")
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .success(true)
                .build();
        history.setId(UUID.randomUUID());

        when(rotationHistoryRepository.findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(secretId))
                .thenReturn(Optional.of(history));
        RotationHistoryResponse response = buildHistoryResponse(secretId, true);
        when(rotationMapper.toHistoryResponse(history)).thenReturn(response);

        RotationHistoryResponse result = rotationService.getLastSuccessfulRotation(secretId);

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    void getLastSuccessfulRotation_noHistory_returnsNull() {
        UUID secretId = UUID.randomUUID();

        when(rotationHistoryRepository.findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(secretId))
                .thenReturn(Optional.empty());

        RotationHistoryResponse result = rotationService.getLastSuccessfulRotation(secretId);

        assertThat(result).isNull();
    }

    // ─── Statistics ─────────────────────────────────────────

    @Test
    void getRotationStats_correctCounts() {
        UUID secretId = UUID.randomUUID();
        Secret secret = buildSecret(secretId);
        RotationPolicy policy = buildPolicy(secretId, secret);
        policy.setIsActive(true);

        when(rotationPolicyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(rotationHistoryRepository.countBySecretId(secretId)).thenReturn(10L);
        when(rotationHistoryRepository.countBySecretIdAndSuccessFalse(secretId)).thenReturn(2L);

        var stats = rotationService.getRotationStats(secretId);

        assertThat(stats.get("activePolicies")).isEqualTo(1L);
        assertThat(stats.get("totalRotations")).isEqualTo(10L);
        assertThat(stats.get("failedRotations")).isEqualTo(2L);
    }

    // ─── Helpers ────────────────────────────────────────────

    private Secret buildSecret(UUID secretId) {
        Secret secret = Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/test/secret/path")
                .name("test-secret")
                .secretType(SecretType.STATIC)
                .currentVersion(1)
                .isActive(true)
                .build();
        secret.setId(secretId);
        return secret;
    }

    private RotationPolicy buildPolicy(UUID secretId, Secret secret) {
        RotationPolicy policy = RotationPolicy.builder()
                .secret(secret)
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .rotationIntervalHours(24)
                .randomLength(32)
                .randomCharset("alphanumeric")
                .isActive(true)
                .failureCount(0)
                .maxFailures(5)
                .nextRotationAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        policy.setId(UUID.randomUUID());
        return policy;
    }

    private RotationPolicyResponse buildPolicyResponse(UUID secretId) {
        return new RotationPolicyResponse(
                UUID.randomUUID(), secretId, "/test/secret/path",
                RotationStrategy.RANDOM_GENERATE, 24, 32, "alphanumeric",
                null, true, 0, 5, null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                Instant.now(), Instant.now());
    }

    private RotationHistoryResponse buildHistoryResponse(UUID secretId, boolean success) {
        return new RotationHistoryResponse(
                UUID.randomUUID(), secretId, "/test/secret/path",
                RotationStrategy.RANDOM_GENERATE, 1, success ? 2 : null,
                success, success ? null : "Rotation failed",
                100L, null, Instant.now());
    }
}
