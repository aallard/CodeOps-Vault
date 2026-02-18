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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core service for secret lifecycle management in the CodeOps Vault.
 *
 * <p>Provides CRUD operations for secrets with automatic encryption,
 * versioning, hierarchical path management, metadata, and configurable
 * version retention policies.</p>
 *
 * <p>Every write operation creates a new immutable version — secrets are
 * never overwritten. The current version number on the Secret entity always
 * points to the latest version.</p>
 *
 * <h3>Encryption</h3>
 * <p>All secret values are encrypted via {@link EncryptionService} using
 * AES-256-GCM envelope encryption before being stored. Values are only
 * decrypted when explicitly requested through {@link #readSecretValue}
 * or {@link #readSecretVersionValue}.</p>
 *
 * <h3>Path Hierarchy</h3>
 * <p>Secrets are organized by hierarchical paths (e.g.,
 * {@code /services/talent-app/db/password}). Paths must start with "/"
 * and use "/" as separator. Listing operations support path prefix filtering.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecretService {

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final SecretMetadataRepository secretMetadataRepository;
    private final EncryptionService encryptionService;
    private final SecretMapper secretMapper;

    // ─── Create ─────────────────────────────────────────────

    /**
     * Creates a new secret at the specified path.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Validate path uniqueness within team</li>
     *   <li>Create Secret entity with currentVersion=1</li>
     *   <li>Encrypt the value via EncryptionService</li>
     *   <li>Create SecretVersion (version 1) with encrypted value</li>
     *   <li>Save metadata key-value pairs if provided</li>
     *   <li>Return SecretResponse (never the value)</li>
     * </ol>
     *
     * <p>For REFERENCE type secrets, no encryption is performed and no
     * version record is created — only the referenceArn is stored.</p>
     *
     * @param request The creation request with path, name, value, type, etc.
     * @param teamId  The team ID from JWT context.
     * @param userId  The user ID from JWT context.
     * @return SecretResponse with metadata (never the secret value).
     * @throws ValidationException if a secret already exists at this path for this team.
     */
    @Transactional
    public SecretResponse createSecret(CreateSecretRequest request, UUID teamId, UUID userId) {
        if (secretRepository.existsByTeamIdAndPath(teamId, request.path())) {
            throw new ValidationException("Secret already exists at path: " + request.path());
        }

        Secret secret = Secret.builder()
                .teamId(teamId)
                .path(request.path())
                .name(request.name())
                .description(request.description())
                .secretType(request.secretType())
                .currentVersion(request.secretType() == SecretType.REFERENCE ? 0 : 1)
                .maxVersions(request.maxVersions())
                .retentionDays(request.retentionDays())
                .expiresAt(request.expiresAt())
                .ownerUserId(userId)
                .referenceArn(request.referenceArn())
                .isActive(true)
                .build();

        secret = secretRepository.save(secret);

        if (request.secretType() != SecretType.REFERENCE) {
            String encryptedValue = encryptionService.encrypt(request.value());
            SecretVersion version = SecretVersion.builder()
                    .secret(secret)
                    .versionNumber(1)
                    .encryptedValue(encryptedValue)
                    .encryptionKeyId(AppConstants.DEFAULT_ENCRYPTION_KEY_ID)
                    .createdByUserId(userId)
                    .isDestroyed(false)
                    .build();
            secretVersionRepository.save(version);
        }

        Map<String, String> metadataMap = saveMetadata(secret, request.metadata());

        log.info("Created secret '{}' at path '{}' for team {}", request.name(), request.path(), teamId);
        return secretMapper.toResponse(secret, metadataMap);
    }

    // ─── Read ───────────────────────────────────────────────

    /**
     * Gets a secret's metadata by ID (never returns the value).
     *
     * @param secretId The secret ID.
     * @return SecretResponse with metadata.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional(readOnly = true)
    public SecretResponse getSecretById(UUID secretId) {
        Secret secret = findSecretById(secretId);
        Map<String, String> metadata = buildMetadataMap(secretId);
        return secretMapper.toResponse(secret, metadata);
    }

    /**
     * Gets a secret's metadata by team and path (never returns the value).
     *
     * @param teamId The team ID.
     * @param path   The hierarchical path.
     * @return SecretResponse with metadata.
     * @throws NotFoundException if no secret exists at this path for this team.
     */
    @Transactional(readOnly = true)
    public SecretResponse getSecretByPath(UUID teamId, String path) {
        Secret secret = secretRepository.findByTeamIdAndPath(teamId, path)
                .orElseThrow(() -> new NotFoundException("Secret", "path", path));
        Map<String, String> metadata = buildMetadataMap(secret.getId());
        return secretMapper.toResponse(secret, metadata);
    }

    /**
     * Reads and decrypts the current version of a secret's value.
     *
     * <p>This is the only method that returns a decrypted secret value.
     * Updates {@code lastAccessedAt} on the Secret entity.</p>
     *
     * @param secretId The secret ID.
     * @return SecretValueResponse containing the decrypted value.
     * @throws NotFoundException if the secret or its current version does not exist.
     */
    @Transactional
    public SecretValueResponse readSecretValue(UUID secretId) {
        Secret secret = findSecretById(secretId);

        SecretVersion version = secretVersionRepository
                .findBySecretIdAndVersionNumber(secretId, secret.getCurrentVersion())
                .orElseThrow(() -> new NotFoundException("SecretVersion", "versionNumber",
                        String.valueOf(secret.getCurrentVersion())));

        String decryptedValue = encryptionService.decrypt(version.getEncryptedValue());

        secret.setLastAccessedAt(Instant.now());
        secretRepository.save(secret);

        log.debug("Read secret value for secret {} (version {})", secretId, version.getVersionNumber());
        return new SecretValueResponse(
                secret.getId(), secret.getPath(), secret.getName(),
                version.getVersionNumber(), decryptedValue, version.getCreatedAt());
    }

    /**
     * Reads and decrypts a specific historical version of a secret.
     *
     * <p>Updates {@code lastAccessedAt} on the Secret entity.</p>
     *
     * @param secretId      The secret ID.
     * @param versionNumber The version to read.
     * @return SecretValueResponse containing the decrypted value for that version.
     * @throws NotFoundException   if the secret or version does not exist.
     * @throws ValidationException if the version has been destroyed.
     */
    @Transactional
    public SecretValueResponse readSecretVersionValue(UUID secretId, int versionNumber) {
        Secret secret = findSecretById(secretId);

        SecretVersion version = secretVersionRepository
                .findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> new NotFoundException("SecretVersion", "versionNumber",
                        String.valueOf(versionNumber)));

        if (version.getIsDestroyed()) {
            throw new ValidationException("Version " + versionNumber + " has been destroyed and cannot be read");
        }

        String decryptedValue = encryptionService.decrypt(version.getEncryptedValue());

        secret.setLastAccessedAt(Instant.now());
        secretRepository.save(secret);

        log.debug("Read secret version {} for secret {}", versionNumber, secretId);
        return new SecretValueResponse(
                secret.getId(), secret.getPath(), secret.getName(),
                version.getVersionNumber(), decryptedValue, version.getCreatedAt());
    }

    // ─── List ───────────────────────────────────────────────

    /**
     * Lists secrets for a team with optional filters.
     *
     * <p>Filters are applied with priority: secretType, pathPrefix, activeOnly.
     * Only one filter is applied per query.</p>
     *
     * @param teamId     The team ID.
     * @param secretType Optional type filter (null = all types).
     * @param pathPrefix Optional path prefix filter (null = all paths).
     * @param activeOnly Whether to return only active secrets.
     * @param pageable   Pagination parameters.
     * @return Paginated list of SecretResponse.
     */
    @Transactional(readOnly = true)
    public PageResponse<SecretResponse> listSecrets(UUID teamId, SecretType secretType,
                                                     String pathPrefix, boolean activeOnly,
                                                     Pageable pageable) {
        Page<Secret> page;

        if (secretType != null) {
            page = secretRepository.findByTeamIdAndSecretType(teamId, secretType, pageable);
        } else if (pathPrefix != null && !pathPrefix.isBlank()) {
            page = secretRepository.findByTeamIdAndPathStartingWith(teamId, pathPrefix, pageable);
        } else if (activeOnly) {
            page = secretRepository.findByTeamIdAndIsActiveTrue(teamId, pageable);
        } else {
            page = secretRepository.findByTeamId(teamId, pageable);
        }

        List<SecretResponse> responses = page.getContent().stream()
                .map(s -> secretMapper.toResponse(s, buildMetadataMap(s.getId())))
                .toList();

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Searches secrets by name (case-insensitive partial match).
     *
     * @param teamId   The team ID.
     * @param query    Search string matched against secret name.
     * @param pageable Pagination parameters.
     * @return Paginated list of matching SecretResponse.
     */
    @Transactional(readOnly = true)
    public PageResponse<SecretResponse> searchSecrets(UUID teamId, String query, Pageable pageable) {
        Page<Secret> page = secretRepository.findByTeamIdAndNameContainingIgnoreCase(teamId, query, pageable);

        List<SecretResponse> responses = page.getContent().stream()
                .map(s -> secretMapper.toResponse(s, buildMetadataMap(s.getId())))
                .toList();

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Lists all paths under a given prefix for a team.
     *
     * <p>Returns only the paths (not full secret objects) for lightweight
     * directory-style browsing of the secret hierarchy.</p>
     *
     * @param teamId     The team ID.
     * @param pathPrefix The prefix to list under (e.g., "/services/talent-app/").
     * @return List of paths under the prefix.
     */
    @Transactional(readOnly = true)
    public List<String> listPaths(UUID teamId, String pathPrefix) {
        String prefix = (pathPrefix != null && !pathPrefix.isBlank()) ? pathPrefix : "/";
        List<Secret> secrets = secretRepository.findByTeamIdAndPathStartingWithAndIsActiveTrue(teamId, prefix);
        return secrets.stream()
                .map(Secret::getPath)
                .distinct()
                .sorted()
                .toList();
    }

    // ─── Update ─────────────────────────────────────────────

    /**
     * Updates a secret — creates a new version if value is provided,
     * and/or updates metadata fields.
     *
     * <p>If {@code request.value()} is non-null and non-empty:</p>
     * <ol>
     *   <li>Encrypt the new value</li>
     *   <li>Increment currentVersion on Secret entity</li>
     *   <li>Create a new SecretVersion with the encrypted value</li>
     *   <li>Apply version retention policy (clean up old versions if needed)</li>
     * </ol>
     *
     * <p>Non-null metadata/description/maxVersions/retentionDays/expiresAt
     * fields update the Secret entity.</p>
     *
     * @param secretId The secret ID.
     * @param request  The update request.
     * @param userId   The user ID from JWT context.
     * @return Updated SecretResponse.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public SecretResponse updateSecret(UUID secretId, UpdateSecretRequest request, UUID userId) {
        Secret secret = findSecretById(secretId);
        boolean newVersionCreated = false;

        if (request.value() != null && !request.value().isBlank()) {
            int newVersionNumber = secret.getCurrentVersion() + 1;
            String encryptedValue = encryptionService.encrypt(request.value());

            SecretVersion version = SecretVersion.builder()
                    .secret(secret)
                    .versionNumber(newVersionNumber)
                    .encryptedValue(encryptedValue)
                    .encryptionKeyId(AppConstants.DEFAULT_ENCRYPTION_KEY_ID)
                    .changeDescription(request.changeDescription())
                    .createdByUserId(userId)
                    .isDestroyed(false)
                    .build();
            secretVersionRepository.save(version);
            secret.setCurrentVersion(newVersionNumber);
            newVersionCreated = true;

            log.info("Created version {} for secret {}", newVersionNumber, secretId);
        }

        if (request.description() != null) {
            secret.setDescription(request.description());
        }
        if (request.maxVersions() != null) {
            secret.setMaxVersions(request.maxVersions());
        }
        if (request.retentionDays() != null) {
            secret.setRetentionDays(request.retentionDays());
        }
        if (request.expiresAt() != null) {
            secret.setExpiresAt(request.expiresAt());
        }

        secret = secretRepository.save(secret);

        if (request.metadata() != null) {
            replaceMetadata(secretId, request.metadata());
        }

        if (newVersionCreated) {
            applyRetentionPolicy(secretId);
        }

        Map<String, String> metadata = buildMetadataMap(secretId);
        return secretMapper.toResponse(secret, metadata);
    }

    // ─── Delete ─────────────────────────────────────────────

    /**
     * Soft-deletes a secret by setting isActive=false.
     *
     * <p>The secret and all versions remain in the database but are
     * excluded from listing and read operations.</p>
     *
     * @param secretId The secret ID.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public void softDeleteSecret(UUID secretId) {
        Secret secret = findSecretById(secretId);
        secret.setIsActive(false);
        secretRepository.save(secret);
        log.info("Soft-deleted secret {}", secretId);
    }

    /**
     * Permanently deletes a secret and all its versions, metadata,
     * and associated rotation policy.
     *
     * <p><strong>Destructive operation.</strong> Cannot be undone.</p>
     *
     * @param secretId The secret ID.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public void hardDeleteSecret(UUID secretId) {
        Secret secret = findSecretById(secretId);
        secretMetadataRepository.deleteBySecretId(secretId);
        secretRepository.delete(secret);
        log.info("Hard-deleted secret {}", secretId);
    }

    // ─── Version Management ─────────────────────────────────

    /**
     * Lists all versions of a secret (metadata only, no decrypted values).
     *
     * @param secretId The secret ID.
     * @param pageable Pagination parameters.
     * @return Paginated list of SecretVersionResponse.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional(readOnly = true)
    public PageResponse<SecretVersionResponse> listVersions(UUID secretId, Pageable pageable) {
        if (!secretRepository.existsById(secretId)) {
            throw new NotFoundException("Secret", secretId);
        }

        Page<SecretVersion> page = secretVersionRepository.findBySecretId(secretId, pageable);
        List<SecretVersionResponse> responses = secretMapper.toVersionResponses(page.getContent());

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Gets a specific version's metadata (no decrypted value).
     *
     * @param secretId      The secret ID.
     * @param versionNumber The version number.
     * @return SecretVersionResponse.
     * @throws NotFoundException if the version does not exist.
     */
    @Transactional(readOnly = true)
    public SecretVersionResponse getVersion(UUID secretId, int versionNumber) {
        SecretVersion version = secretVersionRepository
                .findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> new NotFoundException("SecretVersion", "versionNumber",
                        String.valueOf(versionNumber)));
        return secretMapper.toVersionResponse(version);
    }

    /**
     * Destroys a specific version's encrypted value.
     *
     * <p>Sets {@code isDestroyed=true} and overwrites the encrypted value
     * with "DESTROYED". The version record remains for audit purposes but the
     * value is irrecoverable.</p>
     *
     * @param secretId      The secret ID.
     * @param versionNumber The version to destroy.
     * @throws NotFoundException   if the version does not exist.
     * @throws ValidationException if attempting to destroy the current version.
     */
    @Transactional
    public void destroyVersion(UUID secretId, int versionNumber) {
        Secret secret = findSecretById(secretId);

        if (versionNumber == secret.getCurrentVersion()) {
            throw new ValidationException("Cannot destroy the current version");
        }

        SecretVersion version = secretVersionRepository
                .findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> new NotFoundException("SecretVersion", "versionNumber",
                        String.valueOf(versionNumber)));

        destroyVersionRecord(version);
        log.info("Destroyed version {} of secret {}", versionNumber, secretId);
    }

    /**
     * Applies version retention policy for a secret.
     *
     * <p>Enforces two retention rules:</p>
     * <ul>
     *   <li>{@code maxVersions}: Keeps only the N most recent non-destroyed versions.
     *       Older versions are destroyed (value zeroed, isDestroyed=true).</li>
     *   <li>{@code retentionDays}: Destroys versions older than N days
     *       (except the current version, which is always kept).</li>
     * </ul>
     *
     * <p>Called automatically after each new version is created.
     * The current version is never destroyed by retention policies.</p>
     *
     * @param secretId The secret ID.
     */
    @Transactional
    public void applyRetentionPolicy(UUID secretId) {
        Secret secret = findSecretById(secretId);

        if (secret.getMaxVersions() == null && secret.getRetentionDays() == null) {
            return;
        }

        List<SecretVersion> versions = secretVersionRepository
                .findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(secretId);

        int currentVersion = secret.getCurrentVersion();

        if (secret.getMaxVersions() != null) {
            int count = 0;
            for (SecretVersion v : versions) {
                count++;
                if (count > secret.getMaxVersions()
                        && !v.getVersionNumber().equals(currentVersion)) {
                    destroyVersionRecord(v);
                }
            }
        }

        if (secret.getRetentionDays() != null) {
            Instant cutoff = Instant.now().minus(secret.getRetentionDays(), ChronoUnit.DAYS);
            for (SecretVersion v : versions) {
                if (!v.getIsDestroyed()
                        && v.getCreatedAt() != null
                        && v.getCreatedAt().isBefore(cutoff)
                        && !v.getVersionNumber().equals(currentVersion)) {
                    destroyVersionRecord(v);
                }
            }
        }

        log.debug("Applied retention policy for secret {}", secretId);
    }

    // ─── Metadata Management ────────────────────────────────

    /**
     * Gets all metadata key-value pairs for a secret.
     *
     * @param secretId The secret ID.
     * @return Map of metadata keys to values.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getMetadata(UUID secretId) {
        if (!secretRepository.existsById(secretId)) {
            throw new NotFoundException("Secret", secretId);
        }
        return buildMetadataMap(secretId);
    }

    /**
     * Sets a single metadata key-value pair (upsert).
     *
     * <p>If the key already exists, its value is updated.
     * If the key does not exist, a new entry is created.</p>
     *
     * @param secretId The secret ID.
     * @param key      The metadata key.
     * @param value    The metadata value.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public void setMetadata(UUID secretId, String key, String value) {
        Secret secret = findSecretById(secretId);

        secretMetadataRepository.findBySecretIdAndMetadataKey(secretId, key)
                .ifPresentOrElse(
                        existing -> {
                            existing.setMetadataValue(value);
                            secretMetadataRepository.save(existing);
                        },
                        () -> {
                            SecretMetadata metadata = SecretMetadata.builder()
                                    .secret(secret)
                                    .metadataKey(key)
                                    .metadataValue(value)
                                    .build();
                            secretMetadataRepository.save(metadata);
                        }
                );

        log.debug("Set metadata '{}' on secret {}", key, secretId);
    }

    /**
     * Removes a single metadata key.
     *
     * @param secretId The secret ID.
     * @param key      The metadata key to remove.
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public void removeMetadata(UUID secretId, String key) {
        if (!secretRepository.existsById(secretId)) {
            throw new NotFoundException("Secret", secretId);
        }
        secretMetadataRepository.deleteBySecretIdAndMetadataKey(secretId, key);
        log.debug("Removed metadata '{}' from secret {}", key, secretId);
    }

    /**
     * Replaces all metadata for a secret (deletes all existing, adds new).
     *
     * @param secretId The secret ID.
     * @param metadata New metadata map (empty map clears all metadata).
     * @throws NotFoundException if the secret does not exist.
     */
    @Transactional
    public void replaceMetadata(UUID secretId, Map<String, String> metadata) {
        Secret secret = findSecretById(secretId);
        secretMetadataRepository.deleteBySecretId(secretId);

        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                SecretMetadata md = SecretMetadata.builder()
                        .secret(secret)
                        .metadataKey(entry.getKey())
                        .metadataValue(entry.getValue())
                        .build();
                secretMetadataRepository.save(md);
            }
        }

        log.debug("Replaced metadata on secret {} ({} entries)",
                secretId, metadata != null ? metadata.size() : 0);
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets secret counts for a team, broken down by type.
     *
     * @param teamId The team ID.
     * @return Map with keys "total", "static", "dynamic", "reference".
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSecretCounts(UUID teamId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", secretRepository.countByTeamId(teamId));
        counts.put("static", secretRepository.countByTeamIdAndSecretType(teamId, SecretType.STATIC));
        counts.put("dynamic", secretRepository.countByTeamIdAndSecretType(teamId, SecretType.DYNAMIC));
        counts.put("reference", secretRepository.countByTeamIdAndSecretType(teamId, SecretType.REFERENCE));
        return counts;
    }

    /**
     * Gets secrets that are expiring soon (within the given hours).
     *
     * <p>Returns active secrets whose expiration falls between now and
     * now + withinHours.</p>
     *
     * @param teamId      The team ID.
     * @param withinHours Number of hours to look ahead.
     * @return List of SecretResponse for expiring secrets.
     */
    @Transactional(readOnly = true)
    public List<SecretResponse> getExpiringSecrets(UUID teamId, int withinHours) {
        Instant now = Instant.now();
        Instant deadline = now.plus(withinHours, ChronoUnit.HOURS);

        List<Secret> secrets = secretRepository.findByTeamId(teamId);
        return secrets.stream()
                .filter(s -> s.getIsActive())
                .filter(s -> s.getExpiresAt() != null)
                .filter(s -> !s.getExpiresAt().isBefore(now))
                .filter(s -> s.getExpiresAt().isBefore(deadline))
                .map(s -> secretMapper.toResponse(s, buildMetadataMap(s.getId())))
                .toList();
    }

    // ─── Private Helpers ────────────────────────────────────

    /**
     * Finds a secret by ID or throws NotFoundException.
     *
     * @param secretId The secret ID.
     * @return The found Secret entity.
     * @throws NotFoundException if the secret does not exist.
     */
    private Secret findSecretById(UUID secretId) {
        return secretRepository.findById(secretId)
                .orElseThrow(() -> new NotFoundException("Secret", secretId));
    }

    /**
     * Builds a metadata map from SecretMetadata entities.
     *
     * @param secretId The secret ID.
     * @return Map of metadata keys to values.
     */
    private Map<String, String> buildMetadataMap(UUID secretId) {
        return secretMetadataRepository.findBySecretId(secretId).stream()
                .collect(Collectors.toMap(
                        SecretMetadata::getMetadataKey,
                        SecretMetadata::getMetadataValue));
    }

    /**
     * Saves metadata entries from a request map.
     *
     * @param secret   The parent secret.
     * @param metadata The metadata map from the request (may be null).
     * @return The saved metadata as a map.
     */
    private Map<String, String> saveMetadata(Secret secret, Map<String, String> metadata) {
        Map<String, String> metadataMap = new LinkedHashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                SecretMetadata md = SecretMetadata.builder()
                        .secret(secret)
                        .metadataKey(entry.getKey())
                        .metadataValue(entry.getValue())
                        .build();
                secretMetadataRepository.save(md);
                metadataMap.put(entry.getKey(), entry.getValue());
            }
        }
        return metadataMap;
    }

    /**
     * Destroys a version by zeroing its encrypted value and marking it destroyed.
     *
     * @param version The version to destroy.
     */
    private void destroyVersionRecord(SecretVersion version) {
        version.setIsDestroyed(true);
        version.setEncryptedValue("DESTROYED");
        secretVersionRepository.save(version);
    }
}
