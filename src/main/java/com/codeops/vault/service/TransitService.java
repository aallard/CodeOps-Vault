package com.codeops.vault.service;

import com.codeops.vault.dto.mapper.TransitKeyMapper;
import com.codeops.vault.dto.request.CreateTransitKeyRequest;
import com.codeops.vault.dto.request.TransitDecryptRequest;
import com.codeops.vault.dto.request.TransitEncryptRequest;
import com.codeops.vault.dto.request.TransitRewrapRequest;
import com.codeops.vault.dto.request.UpdateTransitKeyRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.TransitDecryptResponse;
import com.codeops.vault.dto.response.TransitEncryptResponse;
import com.codeops.vault.dto.response.TransitKeyResponse;
import com.codeops.vault.entity.TransitKey;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import com.codeops.vault.repository.TransitKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encryption-as-a-service using named, versioned transit keys.
 *
 * <p>Transit encryption allows callers to encrypt and decrypt data without
 * storing it in the Vault. Named keys with versioning support key rotation
 * without re-encrypting existing data through envelope encryption.</p>
 *
 * <h3>Key Versioning</h3>
 * <p>Each transit key can have multiple versions. Encryption always uses
 * the current (latest) version. Decryption reads the key version from the
 * ciphertext envelope and uses the corresponding key. This allows key
 * rotation without re-encrypting all existing data — only the key needs
 * updating, and existing ciphertext remains valid.</p>
 *
 * <h3>Key Material Storage</h3>
 * <p>Key material is stored as an encrypted JSON array in the TransitKey
 * entity. The array itself is encrypted with the Vault master key.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransitService {

    private final TransitKeyRepository transitKeyRepository;
    private final EncryptionService encryptionService;
    private final TransitKeyMapper transitKeyMapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    private static final String DEFAULT_ALGORITHM = "AES-256-GCM";

    // ─── Key Management ─────────────────────────────────────

    /**
     * Creates a new named transit key.
     *
     * <p>Generates a random AES-256 key as version 1, encrypts the key
     * material array with the master key, and stores it.</p>
     *
     * @param request Creation request.
     * @param teamId  Team ID from JWT.
     * @param userId  User ID from JWT.
     * @return TransitKeyResponse (never includes key material).
     * @throws ValidationException if a key with this name already exists for the team.
     */
    @Transactional
    public TransitKeyResponse createKey(CreateTransitKeyRequest request, UUID teamId, UUID userId) {
        if (transitKeyRepository.existsByTeamIdAndName(teamId, request.name())) {
            throw new ValidationException("Transit key already exists with name: " + request.name());
        }

        // Generate version 1 key material
        byte[] rawKey = encryptionService.generateDataKey();
        String base64Key = Base64.getEncoder().encodeToString(rawKey);
        List<KeyVersion> keyVersions = new ArrayList<>();
        keyVersions.add(new KeyVersion(1, base64Key));

        String encryptedMaterial = encryptKeyMaterial(keyVersions);

        String algorithm = request.algorithm() != null && !request.algorithm().isBlank()
                ? request.algorithm() : DEFAULT_ALGORITHM;

        TransitKey transitKey = TransitKey.builder()
                .teamId(teamId)
                .name(request.name())
                .description(request.description())
                .currentVersion(1)
                .minDecryptionVersion(1)
                .keyMaterial(encryptedMaterial)
                .algorithm(algorithm)
                .isDeletable(request.isDeletable())
                .isExportable(request.isExportable())
                .isActive(true)
                .createdByUserId(userId)
                .build();

        transitKey = transitKeyRepository.save(transitKey);

        log.info("Created transit key '{}' for team {} (algorithm: {})", request.name(), teamId, algorithm);

        try { auditService.logSuccess(teamId, userId, "WRITE", request.name(), "TRANSIT_KEY", transitKey.getId(), null); }
        catch (Exception e) { log.warn("Audit log failed for createKey: {}", e.getMessage()); }

        return transitKeyMapper.toResponse(transitKey);
    }

    /**
     * Gets a transit key's metadata by ID.
     *
     * @param keyId The transit key ID.
     * @return TransitKeyResponse.
     * @throws NotFoundException if the key does not exist.
     */
    @Transactional(readOnly = true)
    public TransitKeyResponse getKeyById(UUID keyId) {
        TransitKey key = findKeyById(keyId);
        return transitKeyMapper.toResponse(key);
    }

    /**
     * Gets a transit key's metadata by team and name.
     *
     * @param teamId Team ID.
     * @param name   Key name.
     * @return TransitKeyResponse.
     * @throws NotFoundException if the key does not exist.
     */
    @Transactional(readOnly = true)
    public TransitKeyResponse getKeyByName(UUID teamId, String name) {
        TransitKey key = transitKeyRepository.findByTeamIdAndName(teamId, name)
                .orElseThrow(() -> new NotFoundException("TransitKey", "name", name));
        return transitKeyMapper.toResponse(key);
    }

    /**
     * Lists transit keys for a team.
     *
     * @param teamId     Team ID.
     * @param activeOnly Whether to return only active keys.
     * @param pageable   Pagination.
     * @return Paginated TransitKeyResponse list.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransitKeyResponse> listKeys(UUID teamId, boolean activeOnly, Pageable pageable) {
        Page<TransitKey> page;
        if (activeOnly) {
            page = transitKeyRepository.findByTeamIdAndIsActiveTrue(teamId, pageable);
        } else {
            page = transitKeyRepository.findByTeamId(teamId, pageable);
        }

        List<TransitKeyResponse> responses = page.getContent().stream()
                .map(transitKeyMapper::toResponse)
                .toList();

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Updates a transit key's metadata (not key material).
     *
     * @param keyId   The transit key ID.
     * @param request Update request.
     * @return Updated TransitKeyResponse.
     * @throws NotFoundException if the key does not exist.
     */
    @Transactional
    public TransitKeyResponse updateKey(UUID keyId, UpdateTransitKeyRequest request) {
        TransitKey key = findKeyById(keyId);

        if (request.description() != null) {
            key.setDescription(request.description());
        }
        if (request.minDecryptionVersion() != null) {
            key.setMinDecryptionVersion(request.minDecryptionVersion());
        }
        if (request.isDeletable() != null) {
            key.setIsDeletable(request.isDeletable());
        }
        if (request.isExportable() != null) {
            key.setIsExportable(request.isExportable());
        }
        if (request.isActive() != null) {
            key.setIsActive(request.isActive());
        }

        key = transitKeyRepository.save(key);

        log.info("Updated transit key {}", keyId);
        return transitKeyMapper.toResponse(key);
    }

    /**
     * Rotates a transit key — generates a new version.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Decrypt the current key material array</li>
     *   <li>Generate a new random AES-256 key</li>
     *   <li>Append it to the array as version N+1</li>
     *   <li>Re-encrypt the array with the master key</li>
     *   <li>Increment currentVersion on the entity</li>
     * </ol>
     *
     * <p>Existing ciphertext encrypted with older versions remains valid
     * as long as those versions are &gt;= minDecryptionVersion.</p>
     *
     * @param keyId The transit key ID.
     * @return Updated TransitKeyResponse with new version number.
     * @throws NotFoundException if the key does not exist.
     */
    @Transactional
    public TransitKeyResponse rotateKey(UUID keyId) {
        TransitKey key = findKeyById(keyId);

        List<KeyVersion> keyVersions = loadKeyMaterial(key);

        // Generate new version
        int newVersion = key.getCurrentVersion() + 1;
        byte[] newRawKey = encryptionService.generateDataKey();
        String newBase64Key = Base64.getEncoder().encodeToString(newRawKey);
        keyVersions.add(new KeyVersion(newVersion, newBase64Key));

        // Re-encrypt and save
        String encryptedMaterial = encryptKeyMaterial(keyVersions);
        key.setKeyMaterial(encryptedMaterial);
        key.setCurrentVersion(newVersion);
        key = transitKeyRepository.save(key);

        log.info("Rotated transit key {} to version {}", keyId, newVersion);

        try { auditService.logSuccess(key.getTeamId(), null, "ROTATE", key.getName(), "TRANSIT_KEY", keyId, null); }
        catch (Exception e) { log.warn("Audit log failed for rotateKey: {}", e.getMessage()); }

        return transitKeyMapper.toResponse(key);
    }

    /**
     * Deletes a transit key (permanently).
     *
     * <p>Only allowed if {@code isDeletable=true}.</p>
     *
     * @param keyId The transit key ID.
     * @throws NotFoundException   if the key does not exist.
     * @throws ValidationException if the key is not deletable.
     */
    @Transactional
    public void deleteKey(UUID keyId) {
        TransitKey key = findKeyById(keyId);

        if (!key.getIsDeletable()) {
            throw new ValidationException("Transit key is not deletable. Set isDeletable=true before deleting.");
        }

        UUID teamId = key.getTeamId();
        String keyName = key.getName();
        transitKeyRepository.delete(key);
        log.info("Deleted transit key {} ('{}')", keyId, keyName);

        try { auditService.logSuccess(teamId, null, "DELETE", keyName, "TRANSIT_KEY", keyId, null); }
        catch (Exception e) { log.warn("Audit log failed for deleteKey: {}", e.getMessage()); }
    }

    // ─── Encrypt / Decrypt Operations ───────────────────────

    /**
     * Encrypts plaintext using the current version of a named transit key.
     *
     * @param request Contains keyName and Base64-encoded plaintext.
     * @param teamId  Team ID from JWT.
     * @return TransitEncryptResponse with ciphertext and key version.
     * @throws NotFoundException if the key does not exist or is inactive.
     */
    @Transactional(readOnly = true)
    public TransitEncryptResponse encrypt(TransitEncryptRequest request, UUID teamId) {
        TransitKey key = findActiveKeyByName(teamId, request.keyName());

        byte[] currentKeyBytes = getKeyForVersion(key, key.getCurrentVersion());
        String keyId = key.getName() + ":v" + key.getCurrentVersion();
        String ciphertext = encryptionService.encryptWithKey(request.plaintext(), keyId, currentKeyBytes);

        log.debug("Encrypted data with transit key '{}' version {}", request.keyName(), key.getCurrentVersion());

        try { auditService.logSuccess(teamId, null, "TRANSIT_ENCRYPT", request.keyName(), "TRANSIT_KEY", key.getId(), null); }
        catch (Exception e) { log.warn("Audit log failed for encrypt: {}", e.getMessage()); }

        return new TransitEncryptResponse(request.keyName(), key.getCurrentVersion(), ciphertext);
    }

    /**
     * Decrypts ciphertext using the appropriate version of a named transit key.
     *
     * <p>Extracts the key version from the ciphertext envelope and uses
     * the corresponding key material. Fails if the version is below
     * {@code minDecryptionVersion}.</p>
     *
     * @param request Contains keyName and ciphertext.
     * @param teamId  Team ID from JWT.
     * @return TransitDecryptResponse with Base64-encoded plaintext.
     * @throws NotFoundException   if the key does not exist.
     * @throws ValidationException if the key version is below minDecryptionVersion.
     * @throws CodeOpsVaultException if decryption fails.
     */
    @Transactional(readOnly = true)
    public TransitDecryptResponse decrypt(TransitDecryptRequest request, UUID teamId) {
        TransitKey key = transitKeyRepository.findByTeamIdAndName(teamId, request.keyName())
                .orElseThrow(() -> new NotFoundException("TransitKey", "name", request.keyName()));

        // Extract the key version from the ciphertext envelope
        String embeddedKeyId = encryptionService.extractKeyId(request.ciphertext());
        int versionNumber = extractVersionFromKeyId(embeddedKeyId);

        byte[] keyBytes = getKeyForVersion(key, versionNumber);
        String plaintext = encryptionService.decryptWithKey(request.ciphertext(), keyBytes);

        log.debug("Decrypted data with transit key '{}' version {}", request.keyName(), versionNumber);

        try { auditService.logSuccess(teamId, null, "TRANSIT_DECRYPT", request.keyName(), "TRANSIT_KEY", key.getId(), null); }
        catch (Exception e) { log.warn("Audit log failed for decrypt: {}", e.getMessage()); }

        return new TransitDecryptResponse(request.keyName(), plaintext);
    }

    /**
     * Re-encrypts ciphertext with the current key version without exposing plaintext.
     *
     * <p>Used to migrate ciphertext from an older key version to the current
     * version without the caller ever seeing the decrypted data.</p>
     *
     * @param request Contains keyName and existing ciphertext.
     * @param teamId  Team ID from JWT.
     * @return TransitEncryptResponse with rewrapped ciphertext.
     * @throws NotFoundException   if the key does not exist.
     * @throws ValidationException if the source version is below minDecryptionVersion.
     */
    @Transactional(readOnly = true)
    public TransitEncryptResponse rewrap(TransitRewrapRequest request, UUID teamId) {
        TransitKey key = transitKeyRepository.findByTeamIdAndName(teamId, request.keyName())
                .orElseThrow(() -> new NotFoundException("TransitKey", "name", request.keyName()));

        // Extract old version from ciphertext
        String embeddedKeyId = encryptionService.extractKeyId(request.ciphertext());
        int oldVersion = extractVersionFromKeyId(embeddedKeyId);

        byte[] oldKeyBytes = getKeyForVersion(key, oldVersion);
        byte[] newKeyBytes = getKeyForVersion(key, key.getCurrentVersion());

        String newKeyId = key.getName() + ":v" + key.getCurrentVersion();
        String rewrapped = encryptionService.rewrap(request.ciphertext(), oldKeyBytes, newKeyBytes, newKeyId);

        log.debug("Rewrapped data from transit key '{}' version {} to version {}",
                request.keyName(), oldVersion, key.getCurrentVersion());
        return new TransitEncryptResponse(request.keyName(), key.getCurrentVersion(), rewrapped);
    }

    /**
     * Generates a new random data key and returns it in both plaintext
     * and encrypted form (wrapped with the named transit key).
     *
     * <p>The caller can use the plaintext key for their own encryption
     * and store the wrapped key for later decryption via transit decrypt.</p>
     *
     * @param keyName Key name to wrap with.
     * @param teamId  Team ID from JWT.
     * @return Map with "plaintextKey" (Base64) and "ciphertextKey" (encrypted).
     * @throws NotFoundException if the key does not exist.
     */
    @Transactional(readOnly = true)
    public Map<String, String> generateDataKey(String keyName, UUID teamId) {
        TransitKey key = findActiveKeyByName(teamId, keyName);

        byte[] dataKey = encryptionService.generateDataKey();
        String plaintextKey = Base64.getEncoder().encodeToString(dataKey);

        byte[] currentKeyBytes = getKeyForVersion(key, key.getCurrentVersion());
        String keyId = key.getName() + ":v" + key.getCurrentVersion();
        String ciphertextKey = encryptionService.encryptWithKey(plaintextKey, keyId, currentKeyBytes);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("plaintextKey", plaintextKey);
        result.put("ciphertextKey", ciphertextKey);

        log.debug("Generated data key wrapped with transit key '{}' version {}", keyName, key.getCurrentVersion());
        return result;
    }

    // ─── Internal Key Material Access (package-private) ─────

    /**
     * Loads and decrypts the key material array for a transit key.
     *
     * @param transitKey The transit key entity.
     * @return List of KeyVersion records.
     */
    List<KeyVersion> loadKeyMaterial(TransitKey transitKey) {
        try {
            String decrypted = encryptionService.decrypt(transitKey.getKeyMaterial());
            return objectMapper.readValue(decrypted, new TypeReference<List<KeyVersion>>() {});
        } catch (JsonProcessingException e) {
            throw new CodeOpsVaultException("Failed to parse key material for transit key: " + transitKey.getName(), e);
        }
    }

    /**
     * Gets the raw key bytes for a specific version.
     *
     * @param transitKey    The transit key entity.
     * @param versionNumber The version to retrieve.
     * @return Raw key bytes (32 bytes for AES-256).
     * @throws NotFoundException   if the version does not exist in the key material.
     * @throws ValidationException if the version is below minDecryptionVersion.
     */
    byte[] getKeyForVersion(TransitKey transitKey, int versionNumber) {
        if (versionNumber < transitKey.getMinDecryptionVersion()) {
            throw new ValidationException("Key version " + versionNumber + " is below minimum decryption version "
                    + transitKey.getMinDecryptionVersion());
        }

        List<KeyVersion> keyVersions = loadKeyMaterial(transitKey);
        return keyVersions.stream()
                .filter(kv -> kv.version() == versionNumber)
                .findFirst()
                .map(kv -> Base64.getDecoder().decode(kv.key()))
                .orElseThrow(() -> new NotFoundException("KeyVersion", "version", String.valueOf(versionNumber)));
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets transit key counts for a team.
     *
     * @param teamId Team ID.
     * @return Map with "total", "active" counts.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getKeyStats(UUID teamId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        List<TransitKey> allKeys = transitKeyRepository.findByTeamId(teamId);
        stats.put("total", (long) allKeys.size());
        stats.put("active", allKeys.stream().filter(TransitKey::getIsActive).count());
        return stats;
    }

    // ─── Private Helpers ────────────────────────────────────

    /**
     * Finds a transit key by ID or throws NotFoundException.
     *
     * @param keyId The transit key ID.
     * @return The found TransitKey entity.
     * @throws NotFoundException if the key does not exist.
     */
    private TransitKey findKeyById(UUID keyId) {
        return transitKeyRepository.findById(keyId)
                .orElseThrow(() -> new NotFoundException("TransitKey", keyId));
    }

    /**
     * Finds an active transit key by team and name.
     *
     * @param teamId Team ID.
     * @param name   Key name.
     * @return The active TransitKey entity.
     * @throws NotFoundException if the key does not exist or is inactive.
     */
    private TransitKey findActiveKeyByName(UUID teamId, String name) {
        TransitKey key = transitKeyRepository.findByTeamIdAndName(teamId, name)
                .orElseThrow(() -> new NotFoundException("TransitKey", "name", name));
        if (!key.getIsActive()) {
            throw new NotFoundException("TransitKey '" + name + "' is inactive");
        }
        return key;
    }

    /**
     * Encrypts a list of key versions and stores as encrypted JSON.
     *
     * @param keyVersions The list of key versions.
     * @return Encrypted JSON string.
     */
    private String encryptKeyMaterial(List<KeyVersion> keyVersions) {
        try {
            String json = objectMapper.writeValueAsString(keyVersions);
            return encryptionService.encrypt(json);
        } catch (JsonProcessingException e) {
            throw new CodeOpsVaultException("Failed to serialize key material", e);
        }
    }

    /**
     * Extracts the version number from an embedded key ID.
     *
     * <p>Key IDs follow the format {@code "keyName:vN"} where N is the version number.</p>
     *
     * @param embeddedKeyId The embedded key ID from the ciphertext.
     * @return The version number.
     * @throws ValidationException if the key ID format is invalid.
     */
    private int extractVersionFromKeyId(String embeddedKeyId) {
        int colonIdx = embeddedKeyId.lastIndexOf(":v");
        if (colonIdx < 0) {
            throw new ValidationException("Invalid ciphertext: unable to determine key version from key ID: " + embeddedKeyId);
        }
        try {
            return Integer.parseInt(embeddedKeyId.substring(colonIdx + 2));
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid key version in ciphertext key ID: " + embeddedKeyId);
        }
    }
}
