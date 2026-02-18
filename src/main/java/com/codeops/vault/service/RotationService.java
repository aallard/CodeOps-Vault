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
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.repository.RotationHistoryRepository;
import com.codeops.vault.repository.RotationPolicyRepository;
import com.codeops.vault.repository.SecretRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing secret rotation policies and executing rotations.
 *
 * <p>Supports three rotation strategies:</p>
 * <ul>
 *   <li>{@link RotationStrategy#RANDOM_GENERATE} — generates a cryptographically
 *       random string of configurable length and charset</li>
 *   <li>{@link RotationStrategy#EXTERNAL_API} — calls an external HTTP endpoint
 *       to obtain a new secret value</li>
 *   <li>{@link RotationStrategy#CUSTOM_SCRIPT} — executes a local command and
 *       uses stdout as the new value (future — logs warning and fails gracefully
 *       in current implementation)</li>
 * </ul>
 *
 * <p>Rotation is tracked via {@link RotationHistory} entries that record
 * each attempt's success/failure, timing, and version numbers.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RotationService {

    private final RotationPolicyRepository rotationPolicyRepository;
    private final RotationHistoryRepository rotationHistoryRepository;
    private final SecretRepository secretRepository;
    private final SecretService secretService;
    private final EncryptionService encryptionService;
    private final RotationMapper rotationMapper;
    private final RestTemplate restTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ─── Policy CRUD ────────────────────────────────────────

    /**
     * Creates or replaces a rotation policy for a secret.
     *
     * <p>If the secret already has a rotation policy, it is updated.
     * Sets {@code nextRotationAt} to now + rotationIntervalHours.</p>
     *
     * @param request The rotation policy request.
     * @param userId  User ID from JWT.
     * @return RotationPolicyResponse.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public RotationPolicyResponse createOrUpdatePolicy(CreateRotationPolicyRequest request, UUID userId) {
        Secret secret = secretRepository.findById(request.secretId())
                .orElseThrow(() -> new NotFoundException("Secret", request.secretId()));

        RotationPolicy policy = rotationPolicyRepository.findBySecretId(request.secretId())
                .orElse(new RotationPolicy());

        policy.setSecret(secret);
        policy.setStrategy(request.strategy());
        policy.setRotationIntervalHours(request.rotationIntervalHours());
        policy.setRandomLength(request.randomLength());
        policy.setRandomCharset(request.randomCharset());
        policy.setExternalApiUrl(request.externalApiUrl());
        policy.setExternalApiHeaders(request.externalApiHeaders());
        policy.setScriptCommand(request.scriptCommand());
        policy.setMaxFailures(request.maxFailures());
        policy.setNextRotationAt(Instant.now().plus(request.rotationIntervalHours(), ChronoUnit.HOURS));
        policy.setIsActive(true);
        policy.setFailureCount(0);

        policy = rotationPolicyRepository.save(policy);

        log.info("Created/updated rotation policy for secret {} with strategy {}", request.secretId(), request.strategy());
        return rotationMapper.toResponse(policy, secret.getPath());
    }

    /**
     * Gets the rotation policy for a secret.
     *
     * @param secretId The secret ID.
     * @return RotationPolicyResponse.
     * @throws NotFoundException if no rotation policy exists for this secret.
     */
    @Transactional(readOnly = true)
    public RotationPolicyResponse getPolicy(UUID secretId) {
        RotationPolicy policy = rotationPolicyRepository.findBySecretId(secretId)
                .orElseThrow(() -> new NotFoundException("RotationPolicy", "secretId", secretId.toString()));

        return rotationMapper.toResponse(policy, policy.getSecret().getPath());
    }

    /**
     * Updates an existing rotation policy.
     *
     * @param policyId The rotation policy ID.
     * @param request  Update request (only non-null fields applied).
     * @return Updated RotationPolicyResponse.
     * @throws NotFoundException if the policy does not exist.
     */
    @Transactional
    public RotationPolicyResponse updatePolicy(UUID policyId, UpdateRotationPolicyRequest request) {
        RotationPolicy policy = findPolicyById(policyId);

        if (request.strategy() != null) {
            policy.setStrategy(request.strategy());
        }
        if (request.rotationIntervalHours() != null) {
            policy.setRotationIntervalHours(request.rotationIntervalHours());
        }
        if (request.randomLength() != null) {
            policy.setRandomLength(request.randomLength());
        }
        if (request.randomCharset() != null) {
            policy.setRandomCharset(request.randomCharset());
        }
        if (request.externalApiUrl() != null) {
            policy.setExternalApiUrl(request.externalApiUrl());
        }
        if (request.externalApiHeaders() != null) {
            policy.setExternalApiHeaders(request.externalApiHeaders());
        }
        if (request.scriptCommand() != null) {
            policy.setScriptCommand(request.scriptCommand());
        }
        if (request.maxFailures() != null) {
            policy.setMaxFailures(request.maxFailures());
        }
        if (request.isActive() != null) {
            policy.setIsActive(request.isActive());
        }

        policy = rotationPolicyRepository.save(policy);

        log.info("Updated rotation policy {}", policyId);
        return rotationMapper.toResponse(policy, policy.getSecret().getPath());
    }

    /**
     * Deletes a rotation policy. Does NOT delete rotation history.
     *
     * @param policyId The rotation policy ID.
     * @throws NotFoundException if the policy does not exist.
     */
    @Transactional
    public void deletePolicy(UUID policyId) {
        RotationPolicy policy = findPolicyById(policyId);
        rotationPolicyRepository.delete(policy);
        log.info("Deleted rotation policy {}", policyId);
    }

    // ─── Rotation Execution ─────────────────────────────────

    /**
     * Executes rotation for a specific secret.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Load the secret and its rotation policy</li>
     *   <li>Generate a new value based on the strategy</li>
     *   <li>Call {@code secretService.updateSecret()} with the new value</li>
     *   <li>Record success in RotationHistory</li>
     *   <li>Update nextRotationAt = now + interval</li>
     *   <li>Reset failureCount to 0</li>
     * </ol>
     *
     * <p>On failure:</p>
     * <ol>
     *   <li>Record failure in RotationHistory with error message</li>
     *   <li>Increment failureCount</li>
     *   <li>If failureCount &gt;= maxFailures, deactivate the policy</li>
     *   <li>Still advance nextRotationAt to prevent retry storm</li>
     * </ol>
     *
     * @param secretId  The secret to rotate.
     * @param userId    The user triggering rotation (null for scheduled).
     * @return RotationHistoryResponse for this rotation attempt.
     * @throws NotFoundException if the secret or rotation policy does not exist.
     */
    @Transactional
    public RotationHistoryResponse rotateSecret(UUID secretId, UUID userId) {
        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> new NotFoundException("Secret", secretId));

        RotationPolicy policy = rotationPolicyRepository.findBySecretId(secretId)
                .orElseThrow(() -> new NotFoundException("RotationPolicy", "secretId", secretId.toString()));

        long startTime = System.currentTimeMillis();
        int previousVersion = secret.getCurrentVersion();

        try {
            String newValue = generateNewValue(policy);

            UpdateSecretRequest updateRequest = new UpdateSecretRequest(
                    newValue, "Rotated by " + policy.getStrategy(), null, null, null, null, null);
            secretService.updateSecret(secretId, updateRequest, userId);

            long durationMs = System.currentTimeMillis() - startTime;

            // Reload secret to get updated version number
            Secret updatedSecret = secretRepository.findById(secretId)
                    .orElseThrow(() -> new NotFoundException("Secret", secretId));

            RotationHistory history = RotationHistory.builder()
                    .secretId(secretId)
                    .secretPath(secret.getPath())
                    .strategy(policy.getStrategy())
                    .previousVersion(previousVersion)
                    .newVersion(updatedSecret.getCurrentVersion())
                    .success(true)
                    .durationMs(durationMs)
                    .triggeredByUserId(userId)
                    .build();
            history = rotationHistoryRepository.save(history);

            // Update policy on success
            policy.setLastRotatedAt(Instant.now());
            policy.setNextRotationAt(Instant.now().plus(policy.getRotationIntervalHours(), ChronoUnit.HOURS));
            policy.setFailureCount(0);
            rotationPolicyRepository.save(policy);

            // Update secret's lastRotatedAt
            updatedSecret.setLastRotatedAt(Instant.now());
            secretRepository.save(updatedSecret);

            log.info("Successfully rotated secret {} from version {} to {}",
                    secretId, previousVersion, updatedSecret.getCurrentVersion());

            return rotationMapper.toHistoryResponse(history);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;

            RotationHistory history = RotationHistory.builder()
                    .secretId(secretId)
                    .secretPath(secret.getPath())
                    .strategy(policy.getStrategy())
                    .previousVersion(previousVersion)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .triggeredByUserId(userId)
                    .build();
            history = rotationHistoryRepository.save(history);

            // Increment failure count
            policy.setFailureCount(policy.getFailureCount() + 1);

            // Deactivate if max failures exceeded
            if (policy.getMaxFailures() != null && policy.getFailureCount() >= policy.getMaxFailures()) {
                policy.setIsActive(false);
                log.warn("Rotation policy for secret {} deactivated after {} consecutive failures",
                        secretId, policy.getFailureCount());
            }

            // Always advance nextRotationAt to prevent retry storm
            policy.setNextRotationAt(Instant.now().plus(policy.getRotationIntervalHours(), ChronoUnit.HOURS));
            rotationPolicyRepository.save(policy);

            log.error("Rotation failed for secret {}: {}", secretId, e.getMessage());

            return rotationMapper.toHistoryResponse(history);
        }
    }

    /**
     * Generates a new secret value based on the rotation strategy.
     *
     * <p>Package-private for testability.</p>
     *
     * @param policy The rotation policy defining the strategy and parameters.
     * @return The generated new secret value.
     * @throws CodeOpsVaultException if value generation fails.
     */
    String generateNewValue(RotationPolicy policy) {
        return switch (policy.getStrategy()) {
            case RANDOM_GENERATE -> {
                int length = policy.getRandomLength() != null ? policy.getRandomLength() : 32;
                String charset = policy.getRandomCharset() != null ? policy.getRandomCharset() : "alphanumeric";
                yield encryptionService.generateRandomString(length, charset);
            }
            case EXTERNAL_API -> {
                if (policy.getExternalApiUrl() == null || policy.getExternalApiUrl().isBlank()) {
                    throw new CodeOpsVaultException("External API URL is not configured for rotation policy");
                }
                yield callExternalApi(policy.getExternalApiUrl(), policy.getExternalApiHeaders());
            }
            case CUSTOM_SCRIPT -> {
                log.warn("CUSTOM_SCRIPT rotation strategy is not yet implemented for policy on secret {}",
                        policy.getSecret().getId());
                throw new CodeOpsVaultException("CUSTOM_SCRIPT rotation strategy is not yet implemented");
            }
        };
    }

    /**
     * Processes all due rotation policies.
     *
     * <p>Finds all active policies where {@code nextRotationAt} is in the past
     * and executes rotation for each. Failures are logged but do not prevent
     * other rotations from executing.</p>
     *
     * @return Number of rotations attempted.
     */
    @Transactional
    public int processDueRotations() {
        List<RotationPolicy> duePolicies = rotationPolicyRepository
                .findByIsActiveTrueAndNextRotationAtBefore(Instant.now());

        int count = 0;
        for (RotationPolicy policy : duePolicies) {
            try {
                rotateSecret(policy.getSecret().getId(), null);
                count++;
            } catch (Exception e) {
                log.error("Scheduled rotation failed for secret {}: {}",
                        policy.getSecret().getId(), e.getMessage());
                count++;
            }
        }

        return count;
    }

    // ─── History ────────────────────────────────────────────

    /**
     * Lists rotation history for a secret.
     *
     * @param secretId The secret ID.
     * @param pageable Pagination.
     * @return Paginated RotationHistoryResponse list.
     */
    @Transactional(readOnly = true)
    public PageResponse<RotationHistoryResponse> getRotationHistory(UUID secretId, Pageable pageable) {
        Page<RotationHistory> page = rotationHistoryRepository.findBySecretId(secretId, pageable);
        List<RotationHistoryResponse> responses = page.getContent().stream()
                .map(rotationMapper::toHistoryResponse)
                .toList();

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Gets the most recent successful rotation for a secret.
     *
     * @param secretId The secret ID.
     * @return RotationHistoryResponse or null if never rotated.
     */
    @Transactional(readOnly = true)
    public RotationHistoryResponse getLastSuccessfulRotation(UUID secretId) {
        return rotationHistoryRepository.findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(secretId)
                .map(rotationMapper::toHistoryResponse)
                .orElse(null);
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets rotation statistics for a specific secret.
     *
     * @param secretId The secret ID.
     * @return Map with "activePolicies", "totalRotations", "failedRotations".
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getRotationStats(UUID secretId) {
        Map<String, Long> stats = new LinkedHashMap<>();

        long activePolicies = rotationPolicyRepository.findBySecretId(secretId)
                .filter(p -> p.getIsActive())
                .map(p -> 1L)
                .orElse(0L);

        stats.put("activePolicies", activePolicies);
        stats.put("totalRotations", rotationHistoryRepository.countBySecretId(secretId));
        stats.put("failedRotations", rotationHistoryRepository.countBySecretIdAndSuccessFalse(secretId));

        return stats;
    }

    // ─── Private Helpers ────────────────────────────────────

    /**
     * Finds a rotation policy by ID or throws NotFoundException.
     *
     * @param policyId The rotation policy ID.
     * @return The found RotationPolicy entity.
     * @throws NotFoundException if the policy does not exist.
     */
    private RotationPolicy findPolicyById(UUID policyId) {
        return rotationPolicyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("RotationPolicy", policyId));
    }

    /**
     * Calls an external API endpoint to obtain a new secret value.
     *
     * @param url            The endpoint URL.
     * @param headersJson    JSON-encoded HTTP headers (nullable).
     * @return The response body as the new secret value.
     * @throws CodeOpsVaultException if the API call fails.
     */
    private String callExternalApi(String url, String headersJson) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (headersJson != null && !headersJson.isBlank()) {
                Map<String, String> headerMap = OBJECT_MAPPER.readValue(
                        headersJson, new TypeReference<Map<String, String>>() {});
                headerMap.forEach(headers::set);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new CodeOpsVaultException("External API returned empty response from: " + url);
            }

            return response.getBody();

        } catch (CodeOpsVaultException e) {
            throw e;
        } catch (Exception e) {
            throw new CodeOpsVaultException("External API call failed: " + e.getMessage(), e);
        }
    }
}
