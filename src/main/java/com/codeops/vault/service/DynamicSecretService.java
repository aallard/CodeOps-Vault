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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating and managing short-lived dynamic database credentials.
 *
 * <p>Dynamic secrets provide lease-based credentials that auto-expire after
 * a configurable TTL. When a lease is created, temporary database credentials
 * are generated; when the lease expires or is revoked, the credentials are
 * cleaned up.</p>
 *
 * <h3>Supported Backends</h3>
 * <ul>
 *   <li><strong>PostgreSQL</strong> — Creates/drops ROLE with LOGIN, grants CONNECT and USAGE</li>
 *   <li><strong>MySQL</strong> — Creates/drops USER, grants SELECT/INSERT/UPDATE/DELETE</li>
 * </ul>
 *
 * <h3>Lease Lifecycle</h3>
 * <pre>
 * Request → Generate credentials → Create DB user → Record lease (ACTIVE)
 *                                                         ↓
 *                                            TTL expires or manual revoke
 *                                                         ↓
 *                                            Drop DB user → Update lease (EXPIRED/REVOKED)
 * </pre>
 *
 * <h3>Development Mode</h3>
 * <p>When {@code codeops.vault.dynamic-secrets.execute-sql} is false (default),
 * credentials are generated and leases tracked but no SQL is executed against
 * target databases. Set to true only when target databases are available.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicSecretService {

    private final DynamicLeaseRepository leaseRepository;
    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;
    private final LeaseMapper leaseMapper;
    private final DynamicSecretProperties dynamicSecretProperties;
    private final AuditService auditService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_USERNAME_LENGTH = 63;

    // ─── Lease Creation ─────────────────────────────────────

    /**
     * Creates a new dynamic lease — generates temporary credentials.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Validate the secret exists and is type DYNAMIC</li>
     *   <li>Extract backend config from secret metadata</li>
     *   <li>Generate unique username: {@code v_<secretName>_<shortUUID>} (max 63 chars)</li>
     *   <li>Generate random password via EncryptionService</li>
     *   <li>If executeSql=true: create the database user on the target backend</li>
     *   <li>Encrypt the credentials JSON</li>
     *   <li>Create DynamicLease entity with ACTIVE status and calculated expiresAt</li>
     *   <li>Return response with decrypted credentials (only time caller sees them)</li>
     * </ol>
     *
     * @param request The lease creation request (secretId, ttlSeconds).
     * @param userId  Requesting user ID.
     * @return DynamicLeaseResponse including connection details.
     * @throws NotFoundException   if the secret does not exist.
     * @throws ValidationException if the secret is not type DYNAMIC.
     * @throws CodeOpsVaultException if credential creation fails.
     */
    @Transactional
    public DynamicLeaseResponse createLease(CreateDynamicLeaseRequest request, UUID userId) {
        Secret secret = secretRepository.findById(request.secretId())
                .orElseThrow(() -> new NotFoundException("Secret", request.secretId()));

        if (secret.getSecretType() != SecretType.DYNAMIC) {
            throw new ValidationException("Secret must be of type DYNAMIC, but was: " + secret.getSecretType());
        }

        Map<String, String> metadata = parseMetadataJson(secret.getMetadataJson());
        validateBackendMetadata(metadata);

        String backendType = metadata.get("backendType");
        String host = metadata.get("host");
        int port = Integer.parseInt(metadata.get("port"));
        String database = metadata.get("database");
        String adminUser = metadata.get("adminUser");
        String adminPassword = metadata.get("adminPassword");

        // Generate unique credentials
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        String username = generateUsername(secret.getName(), shortUuid);
        String password = encryptionService.generateRandomString(
                dynamicSecretProperties.getPasswordLength(), "alphanumeric");

        // Create DB user if SQL execution is enabled
        if (dynamicSecretProperties.isExecuteSql()) {
            createDatabaseUser(backendType, host, port, database, adminUser, adminPassword, username, password);
        } else {
            log.debug("SQL execution disabled — skipping database user creation for username: {}", username);
        }

        // Encrypt credentials for storage
        Map<String, Object> credentialsMap = new LinkedHashMap<>();
        credentialsMap.put("username", username);
        credentialsMap.put("password", password);
        credentialsMap.put("host", host);
        credentialsMap.put("port", port);
        credentialsMap.put("database", database);
        credentialsMap.put("backendType", backendType);

        String credentialsJson = serializeToJson(credentialsMap);
        String encryptedCredentials = encryptionService.encrypt(credentialsJson);

        // Build metadata JSON for the lease
        Map<String, Object> leaseMetadata = new LinkedHashMap<>();
        leaseMetadata.put("host", host);
        leaseMetadata.put("port", port);
        leaseMetadata.put("database", database);
        leaseMetadata.put("username", username);
        leaseMetadata.put("backendType", backendType);

        String leaseId = "lease-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(request.ttlSeconds(), ChronoUnit.SECONDS);

        DynamicLease lease = DynamicLease.builder()
                .leaseId(leaseId)
                .secretId(request.secretId())
                .secretPath(secret.getPath())
                .backendType(backendType)
                .credentials(encryptedCredentials)
                .status(LeaseStatus.ACTIVE)
                .ttlSeconds(request.ttlSeconds())
                .expiresAt(expiresAt)
                .requestedByUserId(userId)
                .metadataJson(serializeToJson(leaseMetadata))
                .build();

        lease = leaseRepository.save(lease);

        log.info("Created dynamic lease {} for secret {} (backend: {}, TTL: {}s, user: {})",
                leaseId, request.secretId(), backendType, request.ttlSeconds(), username);

        try { auditService.logSuccess(secret.getTeamId(), userId, "LEASE_CREATE", secret.getPath(), "DYNAMIC_LEASE", lease.getId(), null); }
        catch (Exception e) { log.warn("Audit log failed for createLease: {}", e.getMessage()); }

        // Return response with connection details (only time they're exposed)
        return leaseMapper.toResponse(lease, credentialsMap);
    }

    // ─── Lease Management ───────────────────────────────────

    /**
     * Gets a lease by its lease ID (not entity UUID).
     *
     * @param leaseId The unique lease identifier string.
     * @return DynamicLeaseResponse (credentials NOT included — only metadata).
     * @throws NotFoundException if the lease does not exist.
     */
    @Transactional(readOnly = true)
    public DynamicLeaseResponse getLease(String leaseId) {
        DynamicLease lease = leaseRepository.findByLeaseId(leaseId)
                .orElseThrow(() -> new NotFoundException("DynamicLease", "leaseId", leaseId));

        return leaseMapper.toResponse(lease, null);
    }

    /**
     * Lists leases for a dynamic secret.
     *
     * @param secretId The source secret ID.
     * @param pageable Pagination.
     * @return Paginated DynamicLeaseResponse list.
     */
    @Transactional(readOnly = true)
    public PageResponse<DynamicLeaseResponse> listLeases(UUID secretId, Pageable pageable) {
        Page<DynamicLease> page = leaseRepository.findBySecretId(secretId, pageable);
        List<DynamicLeaseResponse> responses = page.getContent().stream()
                .map(lease -> leaseMapper.toResponse(lease, null))
                .toList();

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Revokes an active lease — cleans up the database user and marks revoked.
     *
     * @param leaseId The lease identifier to revoke.
     * @param userId  User performing the revocation.
     * @throws NotFoundException   if the lease does not exist.
     * @throws ValidationException if the lease is not in ACTIVE status.
     */
    @Transactional
    public void revokeLease(String leaseId, UUID userId) {
        DynamicLease lease = leaseRepository.findByLeaseId(leaseId)
                .orElseThrow(() -> new NotFoundException("DynamicLease", "leaseId", leaseId));

        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new ValidationException("Lease is not active — current status: " + lease.getStatus());
        }

        cleanupLeaseCredentials(lease);

        lease.setStatus(LeaseStatus.REVOKED);
        lease.setRevokedAt(Instant.now());
        lease.setRevokedByUserId(userId);
        leaseRepository.save(lease);

        log.info("Revoked lease {} (secret: {})", leaseId, lease.getSecretId());

        try { auditService.logSuccess(null, userId, "LEASE_REVOKE", lease.getSecretPath(), "DYNAMIC_LEASE", lease.getId(), null); }
        catch (Exception e) { log.warn("Audit log failed for revokeLease: {}", e.getMessage()); }
    }

    /**
     * Revokes all active leases for a secret.
     *
     * @param secretId The source secret ID.
     * @param userId   User performing the revocation.
     * @return Number of leases revoked.
     */
    @Transactional
    public int revokeAllLeases(UUID secretId, UUID userId) {
        List<DynamicLease> leases = leaseRepository.findBySecretId(secretId);
        int count = 0;

        for (DynamicLease lease : leases) {
            if (lease.getStatus() == LeaseStatus.ACTIVE) {
                cleanupLeaseCredentials(lease);
                lease.setStatus(LeaseStatus.REVOKED);
                lease.setRevokedAt(Instant.now());
                lease.setRevokedByUserId(userId);
                leaseRepository.save(lease);
                count++;
            }
        }

        if (count > 0) {
            log.info("Revoked {} active lease(s) for secret {}", count, secretId);
        }

        return count;
    }

    // ─── Expiry Processing ──────────────────────────────────

    /**
     * Processes expired leases — finds ACTIVE leases past their expiresAt
     * and transitions them to EXPIRED, cleaning up credentials.
     *
     * <p>Called by the lease expiry scheduler.</p>
     *
     * @return Number of leases expired.
     */
    @Transactional
    public int processExpiredLeases() {
        List<DynamicLease> expiredLeases = leaseRepository
                .findByStatusAndExpiresAtBefore(LeaseStatus.ACTIVE, Instant.now());

        for (DynamicLease lease : expiredLeases) {
            cleanupLeaseCredentials(lease);
            lease.setStatus(LeaseStatus.EXPIRED);
            leaseRepository.save(lease);
            log.debug("Expired lease {} for secret {}", lease.getLeaseId(), lease.getSecretId());
        }

        return expiredLeases.size();
    }

    // ─── Backend Operations (package-private) ───────────────

    /**
     * Creates a database user on the target backend.
     *
     * <p>Only executes when {@code executeSql=true}.</p>
     *
     * @param backendType   "postgresql" or "mysql".
     * @param host          Database host.
     * @param port          Database port.
     * @param database      Database name.
     * @param adminUser     Admin username for executing DDL.
     * @param adminPassword Admin password.
     * @param newUsername    The username to create.
     * @param newPassword   The password for the new user.
     * @throws CodeOpsVaultException if SQL execution fails.
     */
    void createDatabaseUser(String backendType, String host, int port,
                            String database, String adminUser, String adminPassword,
                            String newUsername, String newPassword) {
        if (!dynamicSecretProperties.isExecuteSql()) {
            return;
        }

        String jdbcUrl = buildJdbcUrl(backendType, host, port, database);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             Statement stmt = conn.createStatement()) {

            if ("postgresql".equalsIgnoreCase(backendType)) {
                stmt.execute("CREATE ROLE \"" + newUsername + "\" WITH LOGIN PASSWORD '" + newPassword + "'");
                stmt.execute("GRANT CONNECT ON DATABASE \"" + database + "\" TO \"" + newUsername + "\"");
                stmt.execute("GRANT USAGE ON SCHEMA public TO \"" + newUsername + "\"");
            } else if ("mysql".equalsIgnoreCase(backendType)) {
                stmt.execute("CREATE USER '" + newUsername + "'@'%' IDENTIFIED BY '" + newPassword + "'");
                stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + database + ".* TO '" + newUsername + "'@'%'");
                stmt.execute("FLUSH PRIVILEGES");
            } else {
                throw new CodeOpsVaultException("Unsupported backend type: " + backendType);
            }

            log.info("Created database user '{}' on {} backend ({}:{})", newUsername, backendType, host, port);

        } catch (CodeOpsVaultException e) {
            throw e;
        } catch (Exception e) {
            throw new CodeOpsVaultException("Failed to create database user: " + e.getMessage(), e);
        }
    }

    /**
     * Drops a database user on the target backend.
     *
     * <p>Only executes when {@code executeSql=true}. Failures are logged
     * but do not throw — best-effort cleanup.</p>
     *
     * @param backendType   "postgresql" or "mysql".
     * @param host          Database host.
     * @param port          Database port.
     * @param database      Database name.
     * @param adminUser     Admin username.
     * @param adminPassword Admin password.
     * @param username      The username to drop.
     */
    void dropDatabaseUser(String backendType, String host, int port,
                          String database, String adminUser, String adminPassword,
                          String username) {
        if (!dynamicSecretProperties.isExecuteSql()) {
            return;
        }

        String jdbcUrl = buildJdbcUrl(backendType, host, port, database);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             Statement stmt = conn.createStatement()) {

            if ("postgresql".equalsIgnoreCase(backendType)) {
                stmt.execute("DROP ROLE IF EXISTS \"" + username + "\"");
            } else if ("mysql".equalsIgnoreCase(backendType)) {
                stmt.execute("DROP USER IF EXISTS '" + username + "'@'%'");
            }

            log.info("Dropped database user '{}' on {} backend", username, backendType);

        } catch (Exception e) {
            log.error("Failed to drop database user '{}' on {} backend: {}", username, backendType, e.getMessage());
        }
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets lease statistics for a secret.
     *
     * @param secretId The source secret ID.
     * @return Map with "active", "expired", "revoked" counts.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getLeaseStats(UUID secretId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("active", leaseRepository.countBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE));
        stats.put("expired", leaseRepository.countBySecretIdAndStatus(secretId, LeaseStatus.EXPIRED));
        stats.put("revoked", leaseRepository.countBySecretIdAndStatus(secretId, LeaseStatus.REVOKED));
        return stats;
    }

    /**
     * Gets total active leases across all secrets.
     *
     * @return Active lease count.
     */
    @Transactional(readOnly = true)
    public long getTotalActiveLeases() {
        return leaseRepository.countByStatus(LeaseStatus.ACTIVE);
    }

    // ─── Private Helpers ────────────────────────────────────

    /**
     * Generates a unique username with the configured prefix, truncated to max length.
     *
     * @param secretName The secret name for inclusion in username.
     * @param shortUuid  Short UUID suffix for uniqueness.
     * @return Generated username, max 63 characters.
     */
    private String generateUsername(String secretName, String shortUuid) {
        String prefix = dynamicSecretProperties.getUsernamePrefix();
        String sanitized = secretName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        String full = prefix + sanitized + "_" + shortUuid;

        if (full.length() > MAX_USERNAME_LENGTH) {
            int availableForName = MAX_USERNAME_LENGTH - prefix.length() - shortUuid.length() - 1;
            if (availableForName > 0) {
                sanitized = sanitized.substring(0, Math.min(sanitized.length(), availableForName));
            } else {
                sanitized = "";
            }
            full = prefix + sanitized + "_" + shortUuid;
        }

        return full.length() > MAX_USERNAME_LENGTH ? full.substring(0, MAX_USERNAME_LENGTH) : full;
    }

    /**
     * Cleans up credentials for a lease by dropping the database user.
     *
     * @param lease The lease to clean up.
     */
    private void cleanupLeaseCredentials(DynamicLease lease) {
        if (!dynamicSecretProperties.isExecuteSql()) {
            return;
        }

        try {
            String decryptedCredentials = encryptionService.decrypt(lease.getCredentials());
            Map<String, Object> creds = OBJECT_MAPPER.readValue(
                    decryptedCredentials, new TypeReference<Map<String, Object>>() {});

            String username = (String) creds.get("username");
            String host = (String) creds.get("host");
            int port = creds.get("port") instanceof Integer ? (Integer) creds.get("port") : Integer.parseInt(creds.get("port").toString());
            String database = (String) creds.get("database");

            // Need admin credentials from the source secret's metadata
            Secret secret = secretRepository.findById(lease.getSecretId()).orElse(null);
            if (secret != null) {
                Map<String, String> metadata = parseMetadataJson(secret.getMetadataJson());
                String adminUser = metadata.get("adminUser");
                String adminPassword = metadata.get("adminPassword");

                dropDatabaseUser(lease.getBackendType(), host, port, database, adminUser, adminPassword, username);
            } else {
                log.warn("Cannot clean up credentials for lease {} — source secret not found", lease.getLeaseId());
            }
        } catch (Exception e) {
            log.error("Failed to clean up credentials for lease {}: {}", lease.getLeaseId(), e.getMessage());
        }
    }

    /**
     * Parses a JSON string into a metadata map.
     *
     * @param json The JSON string (nullable).
     * @return Parsed map, or empty map if null/blank.
     */
    private Map<String, String> parseMetadataJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new CodeOpsVaultException("Failed to parse metadata JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that required backend metadata keys are present.
     *
     * @param metadata The metadata map.
     * @throws ValidationException if required keys are missing.
     */
    private void validateBackendMetadata(Map<String, String> metadata) {
        List<String> required = List.of("backendType", "host", "port", "database", "adminUser", "adminPassword");
        for (String key : required) {
            if (!metadata.containsKey(key) || metadata.get(key) == null || metadata.get(key).isBlank()) {
                throw new ValidationException("Missing required metadata key for dynamic secret: " + key);
            }
        }
    }

    /**
     * Serializes a map to JSON string.
     *
     * @param map The map to serialize.
     * @return JSON string.
     */
    private String serializeToJson(Map<String, Object> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new CodeOpsVaultException("Failed to serialize to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a JDBC URL for the target backend.
     *
     * @param backendType "postgresql" or "mysql".
     * @param host        Database host.
     * @param port        Database port.
     * @param database    Database name.
     * @return JDBC connection URL.
     */
    private String buildJdbcUrl(String backendType, String host, int port, String database) {
        if ("postgresql".equalsIgnoreCase(backendType)) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        } else if ("mysql".equalsIgnoreCase(backendType)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database;
        }
        throw new CodeOpsVaultException("Unsupported backend type for JDBC URL: " + backendType);
    }
}
