# CodeOps-Vault — Codebase Audit

**Audit Date:** 2026-02-20T23:34:23Z
**Branch:** main
**Commit:** eb634db83e699108846e2ebb06063b290122b08f CV-015a: Fix scorecard scoring errors
**Auditor:** Claude Code (Automated)
**Purpose:** Zero-context reference for AI-assisted development
**Audit File:** CodeOps-Vault-Audit.md
**Scorecard:** CodeOps-Vault-Scorecard.md
**OpenAPI Spec:** CodeOps-Vault-OpenAPI.yaml

> This audit is the single source of truth for the CodeOps-Vault codebase.
> The OpenAPI spec (CodeOps-Vault-OpenAPI.yaml) is the source of truth for all endpoints, DTOs, and API contracts.
> An AI reading this audit + the OpenAPI spec should be able to generate accurate code
> changes, new features, tests, and fixes without filesystem access.

---

## 1. Project Identity

```
Project Name: CodeOps-Vault
Repository URL: (private GitHub repository)
Primary Language / Framework: Java 21 / Spring Boot 3.3.0
Java Version: 21 (running on Java 25 with compatibility overrides)
Build Tool + Version: Maven 3 (via wrapper)
Current Branch: main
Latest Commit Hash: eb634db83e699108846e2ebb06063b290122b08f
Latest Commit Message: CV-015a: Fix scorecard scoring errors — SEC-01, CQ-09, CQ-10 verified from source
Audit Timestamp: 2026-02-20T23:34:23Z
```

---

## 2. Directory Structure

```
CodeOps-Vault/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
├── CONVENTIONS.md (symlink to global — should not exist here)
├── src/main/java/com/codeops/vault/
│   ├── CodeOpsVaultApplication.java
│   ├── config/
│   │   ├── AppConstants.java
│   │   ├── AsyncConfig.java
│   │   ├── CorsConfig.java
│   │   ├── DataSeeder.java
│   │   ├── DynamicSecretProperties.java
│   │   ├── GlobalExceptionHandler.java
│   │   ├── HealthController.java
│   │   ├── JwtProperties.java
│   │   ├── LoggingInterceptor.java
│   │   ├── OpenApiConfig.java
│   │   ├── RequestCorrelationFilter.java
│   │   ├── RestTemplateConfig.java
│   │   ├── ServiceUrlProperties.java
│   │   ├── VaultProperties.java
│   │   └── WebMvcConfig.java
│   ├── controller/ (7 controllers)
│   ├── dto/
│   │   ├── mapper/ (6 MapStruct mappers)
│   │   ├── request/ (16 request DTOs)
│   │   └── response/ (15 response DTOs)
│   ├── entity/ (10 entities + BaseEntity)
│   │   └── enums/ (6 enums)
│   ├── exception/ (4 exception classes)
│   ├── repository/ (10 repositories)
│   ├── security/ (5 security classes)
│   └── service/ (8 services + 2 schedulers + 3 records)
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── application-test.yml
│   ├── application-integration.yml
│   └── logback-spring.xml
└── src/test/java/ (62 test files)
```

Single-module Spring Boot application. Source code under `com.codeops.vault` with standard layered architecture (controller → service → repository → entity).

---

## 3. Build & Dependency Manifest

**Build file:** `pom.xml`

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | 3.3.0 (parent) | REST API framework |
| spring-boot-starter-data-jpa | 3.3.0 | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 | Spring Security |
| spring-boot-starter-validation | 3.3.0 | Jakarta Bean Validation |
| postgresql | (managed) | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token validation |
| lombok | 1.18.42 | Boilerplate reduction (Java 25 compatible override) |
| mapstruct | 1.5.5.Final | Entity-to-DTO mapping |
| jackson-datatype-jsr310 | (managed) | Java 8+ date/time serialization |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | Swagger UI + OpenAPI generation |
| logstash-logback-encoder | 7.4 | JSON structured logging (prod) |
| spring-boot-starter-test | 3.3.0 | JUnit 5 + Spring Test |
| spring-security-test | (managed) | @WithMockUser etc. |
| testcontainers postgresql | 1.19.8 | PostgreSQL container for ITs |
| testcontainers junit-jupiter | 1.19.8 | Testcontainers JUnit 5 integration |
| h2 | (managed) | In-memory DB for unit tests |
| mockito | 5.21.0 | Mocking (Java 25 compatible override) |
| byte-buddy | 1.18.4 | ByteBuddy (Java 25 compatible override) |

**Build Plugins:**
- `spring-boot-maven-plugin` — Excludes Lombok from final JAR
- `maven-compiler-plugin` — Java 21, annotation processors: Lombok + MapStruct
- `maven-surefire-plugin` — `--add-opens` for Java 25, includes `*Test.java` and `*IT.java`
- `jacoco-maven-plugin` 0.8.14 — Code coverage with prepare-agent + report

**Build Commands:**
```
Build: ./mvnw clean package -DskipTests
Test: ./mvnw test
Run: ./mvnw spring-boot:run
Package: ./mvnw clean package
```

---

## 4. Configuration & Infrastructure Summary

- **`application.yml`** — Default profile: dev, server port: 8097
- **`application-dev.yml`** — PostgreSQL at localhost:5436/codeops_vault, Hibernate ddl-auto: update, show-sql: true, dev JWT secret (profile-gated), dev master key (profile-gated), auto-unseal: true, dynamic-secrets execute-sql: false, CORS: localhost:3000/3200/5173
- **`application-prod.yml`** — All secrets from env vars (DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, JWT_SECRET, VAULT_MASTER_KEY, VAULT_AUTO_UNSEAL, VAULT_TOTAL_SHARES, VAULT_THRESHOLD, CORS_ALLOWED_ORIGINS, CODEOPS_SERVER_URL, CODEOPS_REGISTRY_URL, CODEOPS_LOGGER_URL), ddl-auto: validate, show-sql: false
- **`application-test.yml`** — H2 in-memory (PostgreSQL mode), ddl-auto: create-drop, test JWT secret, test master key, auto-unseal: true, execute-sql: false
- **`application-integration.yml`** — PostgreSQL driver (Testcontainers supplies URL), ddl-auto: create-drop, integration JWT secret, integration master key
- **`logback-spring.xml`** — dev: human-readable console with MDC (correlationId, userId), prod: LogstashEncoder JSON with service name, test: WARN only
- **`docker-compose.yml`** — PostgreSQL 16-alpine, container `codeops-vault-db`, port 127.0.0.1:5436:5432, healthcheck, named volume `codeops-vault-data`
- **`Dockerfile`** — eclipse-temurin:21-jre-alpine, non-root (appuser:appgroup), port 8097, `java -jar app.jar`
- **`.env`** — Not present. No `.env.example`.

**Connection Map:**
```
Database: PostgreSQL 16, localhost:5436, database: codeops_vault
Cache: None
Message Broker: None
External APIs: RotationService → user-provided external API URLs (RestTemplate)
Cloud Services: None
```

**CI/CD:** None detected.

---

## 5. Startup & Runtime Behavior

**Entry point:** `CodeOpsVaultApplication.main()` — `@SpringBootApplication`, `@EnableScheduling`, `@EnableConfigurationProperties`

**Startup sequence:**
1. Spring Boot auto-configuration (JPA, Security, Web, Validation)
2. `@PostConstruct` in `EncryptionService.validateMasterKey()` — test encrypt/decrypt cycle with master key
3. `@PostConstruct` in `JwtTokenValidator.validateSecret()` — validates JWT secret >= 32 chars
4. `@PostConstruct` in `SealService.initialize()` — if auto-unseal=true, immediately sets UNSEALED
5. `DataSeeder.run()` (dev profile only) — seeds 3 secrets, 2 policies, 3 bindings, 1 rotation policy, 1 transit key. Idempotent.

**Scheduled tasks:**
- `RotationScheduler` — every 60s, calls `RotationService.processDueRotations()`. `@Profile("!test")`
- `LeaseExpiryScheduler` — every 30s, calls `DynamicSecretService.processExpiredLeases()`. `@Profile("!test")`

**Health check:** `GET /health` → `{"status":"UP","service":"codeops-vault","timestamp":"..."}`

---

## 6. Entity / Data Model Layer

### === BaseEntity.java ===
Table: N/A (mapped superclass)
Primary Key: `id` UUID (GenerationType.UUID)

Fields:
  - id: UUID [@Id, @GeneratedValue(UUID)]
  - createdAt: Instant [@Column(updatable=false)]
  - updatedAt: Instant

Auditing: @PrePersist sets createdAt+updatedAt, @PreUpdate sets updatedAt

---

### === Secret.java ===
Table: `secrets`
Primary Key: inherited UUID from BaseEntity

Fields:
  - teamId: UUID [nullable=false]
  - path: String(500) [nullable=false]
  - name: String(200) [nullable=false]
  - description: String(TEXT)
  - secretType: SecretType [@Enumerated(STRING), nullable=false, length=20]
  - currentVersion: int [nullable=false, default=1]
  - maxVersions: Integer
  - retentionDays: Integer
  - expiresAt: Instant
  - lastAccessedAt: Instant
  - lastRotatedAt: Instant
  - ownerUserId: UUID
  - referenceArn: String(500)
  - isActive: Boolean [nullable=false, default=true]

Indexes: idx_secret_team_id (team_id), idx_secret_team_path (team_id, path), idx_secret_team_type (team_id, secret_type), idx_secret_expires (expires_at)
Unique Constraints: uk_secret_team_path (team_id, path)

---

### === SecretVersion.java ===
Table: `secret_versions`
Primary Key: inherited UUID from BaseEntity

Fields:
  - secret: Secret [@ManyToOne(LAZY), JoinColumn(secret_id, nullable=false)]
  - versionNumber: int [nullable=false]
  - encryptedValue: String(TEXT) [nullable=false]
  - encryptionKeyId: String(100)
  - changeDescription: String(500)
  - createdByUserId: UUID
  - isDestroyed: Boolean [nullable=false, default=false]

Relationships: @ManyToOne(LAZY) → Secret
Indexes: idx_sv_secret_id (secret_id), idx_sv_created_at (created_at)
Unique Constraints: uk_sv_secret_version (secret_id, version_number)

---

### === SecretMetadata.java ===
Table: `secret_metadata`
Primary Key: inherited UUID from BaseEntity

Fields:
  - secret: Secret [@ManyToOne(LAZY), JoinColumn(secret_id, nullable=false)]
  - metadataKey: String(200) [nullable=false]
  - metadataValue: String(TEXT) [nullable=false]

Relationships: @ManyToOne(LAZY) → Secret
Indexes: idx_sm_secret_id (secret_id)
Unique Constraints: uk_sm_secret_key (secret_id, metadata_key)

---

### === AccessPolicy.java ===
Table: `access_policies`
Primary Key: inherited UUID from BaseEntity

Fields:
  - teamId: UUID [nullable=false]
  - name: String(200) [nullable=false]
  - description: String(TEXT)
  - pathPattern: String(500) [nullable=false]
  - permissions: String(500) [nullable=false] — comma-separated PolicyPermission values
  - isDenyPolicy: Boolean [nullable=false, default=false]
  - isActive: Boolean [nullable=false, default=true]
  - createdByUserId: UUID

Indexes: idx_ap_team_id (team_id), idx_ap_team_active (team_id, is_active)
Unique Constraints: uk_ap_team_name (team_id, name)

---

### === PolicyBinding.java ===
Table: `policy_bindings`
Primary Key: inherited UUID from BaseEntity

Fields:
  - policy: AccessPolicy [@ManyToOne(LAZY), JoinColumn(policy_id, nullable=false)]
  - bindingType: BindingType [@Enumerated(STRING), nullable=false, length=20]
  - bindingTargetId: UUID [nullable=false]
  - createdByUserId: UUID

Relationships: @ManyToOne(LAZY) → AccessPolicy
Indexes: idx_pb_policy_id (policy_id), idx_pb_target_id (binding_target_id)
Unique Constraints: uk_pb_policy_type_target (policy_id, binding_type, binding_target_id)

---

### === RotationPolicy.java ===
Table: `rotation_policies`
Primary Key: inherited UUID from BaseEntity

Fields:
  - secret: Secret [@OneToOne(LAZY), JoinColumn(secret_id, nullable=false, unique=true)]
  - strategy: RotationStrategy [@Enumerated(STRING), nullable=false, length=30]
  - rotationIntervalHours: Integer [nullable=false]
  - randomLength: Integer
  - randomCharset: String(100)
  - externalApiUrl: String(500)
  - externalApiHeaders: String(TEXT)
  - scriptCommand: String(TEXT)
  - lastRotatedAt: Instant
  - nextRotationAt: Instant
  - isActive: Boolean [nullable=false, default=true]
  - failureCount: Integer [nullable=false, default=0]
  - maxFailures: Integer

Relationships: @OneToOne(LAZY) → Secret (unique)
Indexes: idx_rp_next_rotation (next_rotation_at), idx_rp_active (is_active)

---

### === RotationHistory.java ===
Table: `rotation_history`
Primary Key: inherited UUID from BaseEntity

Fields:
  - secretId: UUID [nullable=false] — plain column, no FK (history survives secret deletion)
  - secretPath: String(500) [nullable=false]
  - strategy: RotationStrategy [@Enumerated(STRING), nullable=false, length=30]
  - previousVersion: Integer
  - newVersion: Integer
  - success: Boolean [nullable=false]
  - errorMessage: String(TEXT)
  - durationMs: Long
  - triggeredByUserId: UUID

Indexes: idx_rh_secret_id (secret_id), idx_rh_created_at (created_at)

---

### === DynamicLease.java ===
Table: `dynamic_leases`
Primary Key: inherited UUID from BaseEntity

Fields:
  - leaseId: String(100) [nullable=false, unique=true]
  - secretId: UUID [nullable=false] — plain column, no FK
  - secretPath: String(500) [nullable=false]
  - backendType: String(50) [nullable=false]
  - credentials: String(TEXT) [nullable=false] — encrypted
  - status: LeaseStatus [@Enumerated(STRING), nullable=false, length=20]
  - ttlSeconds: Integer [nullable=false]
  - expiresAt: Instant [nullable=false]
  - revokedAt: Instant
  - revokedByUserId: UUID
  - requestedByUserId: UUID
  - metadataJson: String(TEXT)

Indexes: idx_dl_lease_id (lease_id), idx_dl_secret_id (secret_id), idx_dl_status (status), idx_dl_expires_at (expires_at)

---

### === TransitKey.java ===
Table: `transit_keys`
Primary Key: inherited UUID from BaseEntity

Fields:
  - teamId: UUID [nullable=false]
  - name: String(200) [nullable=false]
  - description: String(TEXT)
  - currentVersion: Integer [nullable=false, default=1]
  - minDecryptionVersion: Integer [nullable=false, default=1]
  - keyMaterial: String(TEXT) [nullable=false] — encrypted JSON array of KeyVersion records
  - algorithm: String(30) [nullable=false]
  - isDeletable: Boolean [nullable=false, default=false]
  - isExportable: Boolean [nullable=false, default=false]
  - isActive: Boolean [nullable=false, default=true]
  - createdByUserId: UUID

Indexes: idx_tk_team_id (team_id)
Unique Constraints: uk_tk_team_name (team_id, name)

---

### === AuditEntry.java ===
Table: `vault_audit_log`
Primary Key: `id` Long (GenerationType.IDENTITY) — does NOT extend BaseEntity

Fields:
  - id: Long [@Id, @GeneratedValue(IDENTITY)]
  - teamId: UUID
  - userId: UUID
  - operation: String(50) [nullable=false]
  - path: String(500)
  - resourceType: String(50)
  - resourceId: UUID
  - success: Boolean [nullable=false]
  - errorMessage: String(TEXT)
  - ipAddress: String(45)
  - correlationId: String(100)
  - detailsJson: String(TEXT)
  - createdAt: Instant [nullable=false]

Auditing: Own @PrePersist sets createdAt. Immutable — no updatedAt.
Indexes: idx_val_team_id (team_id), idx_val_user_id (user_id), idx_val_operation (operation), idx_val_path (path), idx_val_created_at (created_at), idx_val_resource (resource_type, resource_id)

---

### Entity Relationship Summary

```
Secret --[OneToMany]--> SecretVersion (via secret_id FK)
Secret --[OneToMany]--> SecretMetadata (via secret_id FK)
Secret --[OneToOne]--> RotationPolicy (via secret_id FK, unique)
AccessPolicy --[OneToMany]--> PolicyBinding (via policy_id FK)
RotationHistory --[plain UUID]--> Secret (no FK — history preserved after deletion)
DynamicLease --[plain UUID]--> Secret (no FK — lease preserved after deletion)
AuditEntry — standalone (no FKs to any entity)
TransitKey — standalone (scoped by teamId, no FKs)
```

---

## 7. Enum Definitions

### === SecretType.java ===
Values: STATIC, DYNAMIC, REFERENCE
Used By: Secret.secretType

### === PolicyPermission.java ===
Values: READ, WRITE, DELETE, LIST, ROTATE
Used By: AccessPolicy.permissions (stored as CSV string, parsed via PolicyMapper)

### === BindingType.java ===
Values: USER, TEAM, SERVICE
Used By: PolicyBinding.bindingType

### === RotationStrategy.java ===
Values: RANDOM_GENERATE, EXTERNAL_API, CUSTOM_SCRIPT
Used By: RotationPolicy.strategy, RotationHistory.strategy

### === LeaseStatus.java ===
Values: ACTIVE, EXPIRED, REVOKED
Used By: DynamicLease.status

### === SealStatus.java ===
Values: SEALED, UNSEALED, UNSEALING
Used By: SealService (in-memory state, not persisted)

---

## 8. Repository Layer

### === SecretRepository.java ===
Extends: JpaRepository<Secret, UUID>

Custom Methods:
  - Optional<Secret> findByTeamIdAndPath(UUID, String)
  - List<Secret> findByTeamId(UUID)
  - Page<Secret> findByTeamId(UUID, Pageable)
  - Page<Secret> findByTeamIdAndSecretType(UUID, SecretType, Pageable)
  - Page<Secret> findByTeamIdAndPathStartingWith(UUID, String, Pageable)
  - Page<Secret> findByTeamIdAndIsActiveTrue(UUID, Pageable)
  - Page<Secret> findByTeamIdAndNameContainingIgnoreCase(UUID, String, Pageable)
  - List<Secret> findByTeamIdAndPathStartingWithAndIsActiveTrue(UUID, String)
  - boolean existsByTeamIdAndPath(UUID, String)
  - long countByTeamId(UUID)
  - long countByTeamIdAndSecretType(UUID, SecretType)
  - List<Secret> findByExpiresAtBeforeAndIsActiveTrue(Instant)

### === SecretVersionRepository.java ===
Extends: JpaRepository<SecretVersion, UUID>

Custom Methods:
  - List<SecretVersion> findBySecretIdOrderByVersionNumberDesc(UUID)
  - Page<SecretVersion> findBySecretId(UUID, Pageable)
  - Optional<SecretVersion> findBySecretIdAndVersionNumber(UUID, Integer)
  - Optional<SecretVersion> findTopBySecretIdOrderByVersionNumberDesc(UUID)
  - long countBySecretId(UUID)
  - List<SecretVersion> findBySecretIdAndVersionNumberLessThan(UUID, Integer)
  - void deleteBySecretIdAndVersionNumberLessThan(UUID, Integer)
  - List<SecretVersion> findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(UUID)

### === SecretMetadataRepository.java ===
Extends: JpaRepository<SecretMetadata, UUID>

Custom Methods:
  - List<SecretMetadata> findBySecretId(UUID)
  - Optional<SecretMetadata> findBySecretIdAndMetadataKey(UUID, String)
  - void deleteBySecretIdAndMetadataKey(UUID, String)
  - void deleteBySecretId(UUID)
  - boolean existsBySecretIdAndMetadataKey(UUID, String)

### === AccessPolicyRepository.java ===
Extends: JpaRepository<AccessPolicy, UUID>

Custom Methods:
  - Optional<AccessPolicy> findByTeamIdAndName(UUID, String)
  - List<AccessPolicy> findByTeamId(UUID)
  - Page<AccessPolicy> findByTeamId(UUID, Pageable)
  - Page<AccessPolicy> findByTeamIdAndIsActiveTrue(UUID, Pageable)
  - List<AccessPolicy> findByTeamIdAndIsActiveTrue(UUID)
  - boolean existsByTeamIdAndName(UUID, String)
  - long countByTeamId(UUID)

### === PolicyBindingRepository.java ===
Extends: JpaRepository<PolicyBinding, UUID>

Custom Methods:
  - List<PolicyBinding> findByPolicyId(UUID)
  - List<PolicyBinding> findByBindingTypeAndBindingTargetId(BindingType, UUID)
  - Optional<PolicyBinding> findByPolicyIdAndBindingTypeAndBindingTargetId(UUID, BindingType, UUID)
  - boolean existsByPolicyIdAndBindingTypeAndBindingTargetId(UUID, BindingType, UUID)
  - void deleteByPolicyId(UUID)
  - long countByPolicyId(UUID)
  - @Query("SELECT pb FROM PolicyBinding pb JOIN pb.policy p WHERE p.teamId = :teamId AND p.isActive = true AND pb.bindingType = :bindingType AND pb.bindingTargetId = :targetId")
    List<PolicyBinding> findActiveBindingsForTarget(UUID teamId, BindingType bindingType, UUID targetId)

### === RotationPolicyRepository.java ===
Extends: JpaRepository<RotationPolicy, UUID>

Custom Methods:
  - Optional<RotationPolicy> findBySecretId(UUID)
  - List<RotationPolicy> findByIsActiveTrueAndNextRotationAtBefore(Instant)
  - List<RotationPolicy> findByIsActiveTrue()
  - long countByIsActiveTrue()

### === RotationHistoryRepository.java ===
Extends: JpaRepository<RotationHistory, UUID>

Custom Methods:
  - Page<RotationHistory> findBySecretId(UUID, Pageable)
  - List<RotationHistory> findBySecretIdOrderByCreatedAtDesc(UUID)
  - Optional<RotationHistory> findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(UUID)
  - long countBySecretId(UUID)
  - long countBySecretIdAndSuccessFalse(UUID)

### === DynamicLeaseRepository.java ===
Extends: JpaRepository<DynamicLease, UUID>

Custom Methods:
  - Optional<DynamicLease> findByLeaseId(String)
  - List<DynamicLease> findBySecretId(UUID)
  - Page<DynamicLease> findBySecretId(UUID, Pageable)
  - List<DynamicLease> findByStatus(LeaseStatus)
  - List<DynamicLease> findByStatusAndExpiresAtBefore(LeaseStatus, Instant)
  - long countBySecretIdAndStatus(UUID, LeaseStatus)
  - long countByStatus(LeaseStatus)

### === TransitKeyRepository.java ===
Extends: JpaRepository<TransitKey, UUID>

Custom Methods:
  - Optional<TransitKey> findByTeamIdAndName(UUID, String)
  - List<TransitKey> findByTeamId(UUID)
  - Page<TransitKey> findByTeamId(UUID, Pageable)
  - Page<TransitKey> findByTeamIdAndIsActiveTrue(UUID, Pageable)
  - boolean existsByTeamIdAndName(UUID, String)
  - long countByTeamId(UUID)

### === AuditEntryRepository.java ===
Extends: JpaRepository<AuditEntry, Long>

Custom Methods (all paginated):
  - Page<AuditEntry> findByTeamId(UUID, Pageable)
  - Page<AuditEntry> findByUserId(UUID, Pageable)
  - Page<AuditEntry> findByTeamIdAndOperation(UUID, String, Pageable)
  - Page<AuditEntry> findByTeamIdAndPath(UUID, String, Pageable)
  - Page<AuditEntry> findByTeamIdAndCreatedAtBetween(UUID, Instant, Instant, Pageable)
  - Page<AuditEntry> findByTeamIdAndResourceTypeAndResourceId(UUID, String, UUID, Pageable)
  - Page<AuditEntry> findByTeamIdAndSuccessFalse(UUID, Pageable)
  - long countByTeamId(UUID)
  - long countByTeamIdAndOperation(UUID, String)
  - long countByTeamIdAndSuccessFalse(UUID)

---

## 9. Service Layer

### === SecretService.java ===
Injected Dependencies: SecretRepository, SecretVersionRepository, SecretMetadataRepository, EncryptionService, SecretMapper, AuditService

Methods:
  ─── createSecret(CreateSecretRequest, UUID teamId, UUID userId) → SecretResponse
      Purpose: Creates a new secret with optional initial value and metadata
      Authorization: None at service level
      Logic: Validate path uniqueness → create Secret entity → encrypt value via EncryptionService → create SecretVersion v1 → save metadata → audit log
      Throws: ValidationException (duplicate path), CodeOpsVaultException (encryption failure)
      Audit Logged: Yes
      Paginated: No

  ─── getSecretById(UUID) → SecretResponse
      Purpose: Returns secret metadata (never value)
      Throws: NotFoundException

  ─── getSecretByPath(UUID teamId, String path) → SecretResponse
      Purpose: Lookup by team+path
      Throws: NotFoundException

  ─── readSecretValue(UUID secretId) → SecretValueResponse
      Purpose: Decrypts current version value, updates lastAccessedAt
      Logic: Find secret → find latest version → decrypt → update lastAccessedAt → audit log
      Throws: NotFoundException, CodeOpsVaultException (destroyed version)

  ─── readSecretVersionValue(UUID secretId, int versionNumber) → SecretValueResponse
      Purpose: Decrypts a specific historical version
      Throws: NotFoundException, ValidationException (destroyed version)

  ─── listSecrets(UUID teamId, SecretType, String pathPrefix, boolean activeOnly, Pageable) → PageResponse<SecretResponse>
      Purpose: Paginated secret listing with priority filters: secretType > pathPrefix > activeOnly > all

  ─── searchSecrets(UUID teamId, String query, Pageable) → PageResponse<SecretResponse>
      Purpose: Case-insensitive name search

  ─── listPaths(UUID teamId, String pathPrefix) → List<String>
      Purpose: Returns path strings for directory browsing

  ─── updateSecret(UUID secretId, UpdateSecretRequest, UUID userId) → SecretResponse
      Purpose: Creates new version if value provided, updates metadata, applies retention
      Logic: Partial update (null fields skipped) → new version if value present → retention policy enforcement
      Audit Logged: Yes

  ─── softDeleteSecret(UUID secretId) → void
      Purpose: Sets isActive=false
      Audit Logged: Yes

  ─── hardDeleteSecret(UUID secretId) → void
      Purpose: Permanently deletes secret, metadata, and all versions
      Audit Logged: Yes

  ─── listVersions(UUID secretId, Pageable) → PageResponse<SecretVersionResponse>
  ─── getVersion(UUID secretId, int versionNumber) → SecretVersionResponse
  ─── destroyVersion(UUID secretId, int versionNumber) → void
      Purpose: Sets isDestroyed=true, overwrites encrypted value with "DESTROYED". Prevents destroying current version.

  ─── applyRetentionPolicy(UUID secretId) → void
      Purpose: Enforces maxVersions and retentionDays. Never destroys current version.

  ─── getMetadata(UUID secretId) → Map<String,String>
  ─── setMetadata(UUID secretId, String key, String value) → void
  ─── removeMetadata(UUID secretId, String key) → void
  ─── replaceMetadata(UUID secretId, Map) → void

  ─── getSecretCounts(UUID teamId) → Map<String,Long>
      Purpose: Returns total/static/dynamic/reference counts

  ─── getExpiringSecrets(UUID teamId, int withinHours) → List<SecretResponse>
      Purpose: Finds active secrets expiring within N hours

---

### === PolicyService.java ===
Injected Dependencies: AccessPolicyRepository, PolicyBindingRepository, PolicyMapper, AuditService

Methods:
  ─── createPolicy(CreatePolicyRequest, UUID teamId, UUID userId) → AccessPolicyResponse
      Purpose: Creates an access policy
      Logic: Validate name uniqueness → store permissions as CSV → set isActive=true
      Throws: ValidationException (duplicate name)
      Audit Logged: Yes

  ─── getPolicyById(UUID) → AccessPolicyResponse
  ─── listPolicies(UUID teamId, boolean activeOnly, Pageable) → PageResponse<AccessPolicyResponse>
  ─── updatePolicy(UUID policyId, UpdatePolicyRequest) → AccessPolicyResponse
      Purpose: Partial update (null fields skipped). Name change validated for uniqueness.
  ─── deletePolicy(UUID policyId) → void
      Purpose: Cascades to delete all bindings.

  ─── createBinding(CreateBindingRequest, UUID userId) → PolicyBindingResponse
      Logic: Validate policy exists → duplicate binding check
  ─── listBindingsForPolicy(UUID policyId) → List<PolicyBindingResponse>
  ─── listBindingsForTarget(BindingType, UUID targetId) → List<PolicyBindingResponse>
  ─── deleteBinding(UUID bindingId) → void

  ─── evaluateAccess(UUID userId, UUID teamId, String secretPath, PolicyPermission) → AccessDecision
      Purpose: Core access control. Collects USER + TEAM bindings. Deny-overrides-allow semantics.
      Logic: Find active bindings for user + team → collect policies → evaluate: if any deny policy matches → DENIED; if any allow policy matches → ALLOWED; else → default DENIED

  ─── evaluateServiceAccess(UUID serviceId, UUID teamId, String secretPath, PolicyPermission) → AccessDecision
      Purpose: Same as evaluateAccess but for SERVICE bindings

  ─── getPolicyCounts(UUID teamId) → Map<String,Long>

  Package-private: matchesPath(String secretPath, String pathPattern) — glob-style segment matching with `*` wildcard

---

### === RotationService.java ===
Injected Dependencies: RotationPolicyRepository, RotationHistoryRepository, SecretRepository, SecretService, EncryptionService, RotationMapper, RestTemplate, AuditService

Methods:
  ─── createOrUpdatePolicy(CreateRotationPolicyRequest, UUID userId) → RotationPolicyResponse
      Purpose: Creates or upserts rotation policy. Sets nextRotationAt, resets failureCount.

  ─── getPolicy(UUID secretId) → RotationPolicyResponse
  ─── updatePolicy(UUID policyId, UpdateRotationPolicyRequest) → RotationPolicyResponse
  ─── deletePolicy(UUID policyId) → void (does NOT delete history)

  ─── rotateSecret(UUID secretId, UUID userId) → RotationHistoryResponse
      Purpose: Generates new value per strategy, creates new version, records history.
      Logic: Find policy → generateNewValue(strategy) → secretService.updateSecret → record RotationHistory
      Error Handling: On failure: increments failureCount, deactivates if >= maxFailures, always advances nextRotationAt

  ─── processDueRotations() → int
      Purpose: Finds active policies past nextRotationAt, rotates each. Called by RotationScheduler.

  ─── getRotationHistory(UUID secretId, Pageable) → PageResponse<RotationHistoryResponse>
  ─── getLastSuccessfulRotation(UUID secretId) → RotationHistoryResponse
  ─── getRotationStats(UUID secretId) → Map<String,Long>

  Package-private: generateNewValue(RotationPolicy) — RANDOM_GENERATE: configurable length/charset; EXTERNAL_API: HTTP GET via RestTemplate; CUSTOM_SCRIPT: throws not-implemented

---

### === DynamicSecretService.java ===
Injected Dependencies: DynamicLeaseRepository, SecretRepository, EncryptionService, LeaseMapper, DynamicSecretProperties, AuditService

Methods:
  ─── createLease(CreateDynamicLeaseRequest, UUID userId) → DynamicLeaseResponse
      Purpose: Creates a dynamic secret lease with generated DB credentials
      Logic: Validate secret is DYNAMIC type → parse metadata JSON for backend config → generate username (`v_<name>_<uuid8>`, max 63 chars) + password → create DB user if executeSql=true → encrypt credentials → create DynamicLease (ACTIVE) → return with connection details
      Audit Logged: Yes

  ─── getLease(String leaseId) → DynamicLeaseResponse (without credentials)
  ─── listLeases(UUID secretId, Pageable) → PageResponse<DynamicLeaseResponse>

  ─── revokeLease(String leaseId, UUID userId) → void
      Purpose: Validates ACTIVE status → cleans up DB user → sets REVOKED status
  ─── revokeAllLeases(UUID secretId, UUID userId) → int
  ─── processExpiredLeases() → int
      Purpose: Finds ACTIVE leases past expiresAt → cleanup + EXPIRED status. Called by LeaseExpiryScheduler.

  ─── getLeaseStats(UUID secretId) → Map<String,Long>
  ─── getTotalActiveLeases() → long

  Package-private: createDatabaseUser / dropDatabaseUser — Direct JDBC (PostgreSQL: CREATE ROLE + GRANT; MySQL: CREATE USER + GRANT + FLUSH)
  Development Mode: `dynamicSecretProperties.isExecuteSql()` gates actual SQL execution

---

### === TransitService.java ===
Injected Dependencies: TransitKeyRepository, EncryptionService, TransitKeyMapper, ObjectMapper, AuditService

Methods:
  ─── createKey(CreateTransitKeyRequest, UUID teamId, UUID userId) → TransitKeyResponse
      Purpose: Creates a named transit encryption key
      Logic: Validate name uniqueness → generate AES-256 key → encrypt key material array with master key
      Audit Logged: Yes

  ─── getKeyById(UUID) → TransitKeyResponse
  ─── getKeyByName(UUID teamId, String name) → TransitKeyResponse
  ─── listKeys(UUID teamId, boolean activeOnly, Pageable) → PageResponse<TransitKeyResponse>
  ─── updateKey(UUID keyId, UpdateTransitKeyRequest) → TransitKeyResponse
      Purpose: Metadata-only updates (description, minDecryptionVersion, isDeletable, isExportable, isActive)

  ─── rotateKey(UUID keyId) → TransitKeyResponse
      Purpose: Loads key material → generates new version → appends to array → re-encrypts → increments currentVersion

  ─── deleteKey(UUID keyId) → void
      Purpose: Only if isDeletable=true. Throws ValidationException otherwise.

  ─── encrypt(TransitEncryptRequest, UUID teamId) → TransitEncryptResponse
      Purpose: Encrypts plaintext using current key version
  ─── decrypt(TransitDecryptRequest, UUID teamId) → TransitDecryptResponse
      Purpose: Extracts version from ciphertext envelope, validates >= minDecryptionVersion
  ─── rewrap(TransitRewrapRequest, UUID teamId) → TransitEncryptResponse
      Purpose: Re-encrypts with current version without exposing plaintext
  ─── generateDataKey(String keyName, UUID teamId) → Map<String,String>
      Purpose: Returns plaintextKey + ciphertextKey
  ─── getKeyStats(UUID teamId) → Map<String,Long>

---

### === SealService.java ===
Injected Dependencies (constructor): VaultProperties, AuditService
@Value Injected: autoUnseal, configuredTotalShares (default 5), configuredThreshold (default 3)

State: volatile SealStatus, List<byte[]> collectedShares, List<Integer> collectedShareIndices, totalShares, threshold

Methods:
  ─── initialize() → void [@PostConstruct]
      Purpose: If autoUnseal=true, immediately sets UNSEALED

  ─── getStatus() → SealStatusResponse
  ─── isUnsealed() → boolean
  ─── requireUnsealed() → void
      Purpose: Throws CodeOpsVaultException if not UNSEALED. Called by every controller endpoint.

  ─── seal() → void [synchronized]
      Purpose: Clears shares, sets SEALED, timestamps

  ─── submitKeyShare(String keyShareBase64) → SealStatusResponse [synchronized]
      Purpose: Parses share (first byte = 1-based index) → SEALED→UNSEALING on first share → when >= threshold shares: reconstructs via Lagrange interpolation → verifies against master key
      Throws: CodeOpsVaultException (invalid share, mismatch — resets to SEALED)

  ─── generateKeyShares() → String[]
      Purpose: Splits master key into N shares via Shamir's Secret Sharing. Returns Base64-encoded shares.

  ─── getSealInfo() → Map<String,Object>

  Package-private: splitSecret / reconstructSecret — Shamir's Secret Sharing over GF(256) with Lagrange interpolation

---

### === AuditService.java ===
Injected Dependencies: AuditEntryRepository, AuditMapper

Methods:
  ─── logSuccess(UUID teamId, UUID userId, String operation, String path, String resourceType, UUID resourceId, String detailsJson) → void
      Purpose: Best-effort audit logging. Extracts IP from HttpServletRequest, correlationId from MDC.

  ─── logFailure(UUID teamId, UUID userId, String operation, String path, String resourceType, UUID resourceId, String errorMessage) → void

  ─── queryAuditLog(UUID teamId, AuditQueryRequest, Pageable) → PageResponse<AuditEntryResponse>
      Purpose: Priority filter: resourceType+resourceId > userId > operation > path > time range > successOnly=false > all

  ─── getAuditForResource(UUID teamId, String resourceType, UUID resourceId, Pageable) → PageResponse<AuditEntryResponse>
  ─── getAuditStats(UUID teamId) → Map<String,Long>

  ─── writeAuditEntry(AuditEntry) → void [@Transactional(REQUIRES_NEW)]
      Purpose: Persists in new transaction — decoupled from calling service's transaction

---

### === EncryptionService.java ===
Injected Dependencies: VaultProperties

Methods:
  ─── encrypt(String plaintext) → String
      Purpose: Envelope encryption with master-derived KEK (purpose: "secret-storage")
      Format: [1B version][4B keyIdLen][keyId][4B dekBlockLen][dekIV(12B)+encryptedDEK][12B dataIV][ciphertext+tag]

  ─── encryptWithKey(String plaintext, String keyId, byte[] keyMaterial) → String
      Purpose: Envelope encryption with explicit key

  ─── decrypt(String encryptedEnvelope) → String
  ─── decryptWithKey(String encryptedEnvelope, byte[] keyMaterial) → String
  ─── deriveKey(String purpose) → byte[]
      Purpose: HKDF from master key with purpose-specific info
  ─── generateDataKey() → byte[]
  ─── generateAndWrapDataKey() → GeneratedDataKey
  ─── rewrap(String encryptedEnvelope, byte[] oldKeyMaterial, byte[] newKeyMaterial, String newKeyId) → String
  ─── extractKeyId(String encryptedEnvelope) → String
  ─── generateRandomString(int length, String charset) → String
  ─── hash(String data) → String (SHA-256 hex)
  ─── validateMasterKey() → void [@PostConstruct]

---

### === RotationScheduler.java ===
@Scheduled(fixedDelay=60000), @Profile("!test"). Calls `rotationService.processDueRotations()`.

### === LeaseExpiryScheduler.java ===
@Scheduled(fixedDelay=30000), @Profile("!test"). Calls `dynamicSecretService.processExpiredLeases()`.

### === HkdfUtil.java ===
Static utility: RFC 5869 HKDF-Extract + HKDF-Expand with HMAC-SHA256. Max output: 255 * 32 bytes.

### === GeneratedDataKey.java ===
Record: `record GeneratedDataKey(String plaintextKey, String encryptedKey)`

### === KeyVersion.java ===
Record: `record KeyVersion(int version, String key)` — stored in encrypted key material JSON array for transit keys.

### === AccessDecision.java ===
Record: `record AccessDecision(boolean allowed, String reason, UUID decidingPolicyId, String decidingPolicyName)`
Static factories: `allowed(policyId, name)`, `denied(policyId, name)`, `defaultDenied()`

---

## 10. Security Architecture

**Authentication Flow:**
- JWT tokens issued by CodeOps-Server. Vault ONLY validates, never issues.
- Token validation: HMAC signature + expiration check via `JwtTokenValidator`
- Claims extracted: `sub` (userId UUID), `teamId` (UUID), `roles` (List), `permissions` (List)
- Token revocation: **None.** Stateless JWT only — tokens valid until expiry.

**Authorization Model:**
- Roles from JWT: extracted as `ROLE_*` authorities (e.g., `ROLE_ADMIN`)
- Permissions from JWT: extracted as plain string authorities
- 6 controllers use class-level `@PreAuthorize("hasRole('ADMIN')")`
- SealController uses method-level `@PreAuthorize("hasRole('ADMIN')")` on 4 of 5 methods (status is public)
- Vault also has its own access policy system (PolicyService.evaluateAccess) for fine-grained path-based permissions

**Security Filter Chain (order):**
1. RequestCorrelationFilter — generates/extracts correlation ID for MDC
2. RateLimitFilter — 100 req/min per IP on /api/v1/vault/**
3. JwtAuthFilter — extracts JWT, validates, sets SecurityContext
4. UsernamePasswordAuthenticationFilter (Spring default)

**Public Paths (permitAll):**
- `/health`
- `/api/v1/vault/seal/status`
- `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`

**Authenticated Paths:** All `/api/**` endpoints

**CORS Configuration:**
- Allowed origins: from property (dev: localhost:3000/3200/5173)
- Methods, headers, credentials: configured via CorsConfig bean

**Encryption:**
- Algorithm: AES-256-GCM with envelope encryption
- Key derivation: HKDF (HMAC-SHA256) from master key
- Master key: 32+ chars, from `VAULT_MASTER_KEY` env var (dev default profile-gated)
- Validated at startup via `EncryptionService.validateMasterKey()`

**Password Policy:**
- N/A — Vault does not manage user passwords. Authentication delegated to CodeOps-Server.

**Rate Limiting:**
- Scope: per-IP on `/api/v1/vault/**`
- Limit: 100 requests per 60-second window
- Strategy: in-memory ConcurrentHashMap with sliding window
- Response: 429 `{"status":429,"message":"Rate limit exceeded. Try again later."}`

---

## 11. Notification / Messaging Layer

No email, webhook, or messaging services. No Kafka, RabbitMQ, SQS/SNS integration.

---

## 12. Error Handling

**GlobalExceptionHandler** (8 handlers):

```
NotFoundException           → 404 → {"message": ex.getMessage(), "timestamp": "..."}
ValidationException         → 400 → {"message": ex.getMessage(), "timestamp": "..."}
AuthorizationException      → 403 → {"message": ex.getMessage(), "timestamp": "..."}
AccessDeniedException       → 403 → {"message": "Access denied", "timestamp": "..."}
MethodArgumentNotValidException → 400 → {"message": "field: error, ...", "timestamp": "..."}
HttpMessageNotReadableException → 400 → {"message": "Malformed request body", "timestamp": "..."}
NoResourceFoundException    → 404 → {"message": "Resource not found", "timestamp": "..."}
CodeOpsVaultException       → 500 → {"message": "An unexpected error occurred", "timestamp": "..."}
Exception (catch-all)       → 500 → {"message": "An unexpected error occurred", "timestamp": "..."}
```

Business exception messages (NotFoundException, ValidationException, AuthorizationException) are exposed to clients. All 500 errors use generic message. Stack traces logged server-side only.

---

## 13. Test Coverage

```
Unit Test Files: 62
Integration Test Files: 0
@Test Methods: 628 (unit) + 0 (integration) = 628 total
```

- **Framework:** JUnit 5 + Mockito 5.21.0 + Spring Boot Test
- **Unit tests:** H2 in-memory (PostgreSQL mode) via application-test.yml
- **Integration tests:** Testcontainers configured in pom.xml but 0 IT files exist
- **Test config:** application-test.yml and application-integration.yml in src/main/resources (not src/test/resources)
- **Security tests:** 88 annotations (@WithMockUser, @WithAnonymousUser, Bearer headers)

---

## 14. Cross-Cutting Patterns & Conventions

- **Naming:** Controllers use `@RequestMapping` with resource-based paths. Service methods follow CRUD conventions (create*, get*, list*, update*, delete*).
- **Package structure:** config / controller / dto (mapper, request, response) / entity (enums) / exception / repository / security / service
- **Base classes:** `BaseEntity` with UUID PK and audit timestamps. No BaseService or BaseController.
- **DTO mapping:** MapStruct mappers (6 interfaces, `@Mapper(componentModel="spring")`). DTOs are Java records.
- **Audit logging:** AuditService.logSuccess/logFailure called from services (not controllers). Best-effort via try/catch. IP from X-Forwarded-For, correlationId from MDC. REQUIRES_NEW transaction.
- **Error handling:** Services throw NotFoundException/ValidationException/AuthorizationException. GlobalExceptionHandler catches and maps to HTTP status codes.
- **Pagination:** Controllers accept page/size/sortBy/sortDir params → PageRequest → service returns PageResponse.from(page).
- **Validation:** Jakarta validation annotations on request DTOs. Business validation in services (duplicate checks, state validation).
- **Constants:** AppConstants — VAULT_API_PREFIX, pagination (DEFAULT_PAGE_SIZE=20, MAX_PAGE_SIZE=100), secret limits (MAX_SECRET_PATH_LENGTH=500, MAX_SECRET_VALUE_SIZE=1MB), rate limiting (100 req/min), encryption (AES_KEY_SIZE=32, GCM_IV=12, GCM_TAG=128, ENCRYPTION_FORMAT_VERSION=1).
- **Documentation:** All non-DTO, non-enum, non-entity classes have comprehensive Javadoc. All public methods in services, controllers, and security classes have Javadoc.
- **Seal gate:** Every controller endpoint calls `sealService.requireUnsealed()` before proceeding.

---

## 15. Known Issues, TODOs, and Technical Debt

0 TODO/FIXME/HACK/XXX/WORKAROUND/TEMPORARY found in source code.

---

## 16. OpenAPI Specification

See `CodeOps-Vault-OpenAPI.yaml` in the project root. Generated from source code — 67 endpoints across 8 controllers.

---

## 17. Database — Live Schema Audit

Database not available for live audit. Schema documented from JPA entities only (Section 6).

---

## 18. Kafka / Message Broker

No message broker (Kafka, RabbitMQ, SQS/SNS) detected in this project.

---

## 19. Redis / Cache Layer

No Redis or caching layer detected in this project.

---

## 20. Environment Variable Inventory

| Variable | Required | Default | Used By | Purpose |
|---|---|---|---|---|
| DATABASE_URL | Prod only | jdbc:postgresql://localhost:5436/codeops_vault (dev) | application-prod.yml | JDBC connection URL |
| DATABASE_USERNAME | Prod only | codeops (dev) | application-prod.yml | Database username |
| DATABASE_PASSWORD | Prod only | codeops (dev) | application-prod.yml | Database password |
| DB_USERNAME | No | codeops | application-dev.yml | Dev database username |
| DB_PASSWORD | No | codeops | application-dev.yml | Dev database password |
| JWT_SECRET | Prod only | dev-secret-key-... (dev) | JwtProperties | HMAC signing key (shared with CodeOps-Server) |
| VAULT_MASTER_KEY | Prod only | dev-vault-master-key-... (dev) | VaultProperties | AES-256 master key for envelope encryption |
| VAULT_AUTO_UNSEAL | No | false (prod), true (dev) | VaultProperties | Skip Shamir unseal on startup |
| VAULT_TOTAL_SHARES | No | 5 | VaultProperties | Shamir total shares |
| VAULT_THRESHOLD | No | 3 | VaultProperties | Shamir threshold |
| CORS_ALLOWED_ORIGINS | Prod only | localhost:3000,3200,5173 (dev) | CorsConfig | Allowed CORS origins |
| CODEOPS_SERVER_URL | No | http://localhost:8095 | ServiceUrlProperties | CodeOps-Server base URL |
| CODEOPS_REGISTRY_URL | No | http://localhost:8096 | ServiceUrlProperties | CodeOps-Registry base URL |
| CODEOPS_LOGGER_URL | No | http://localhost:8098 | ServiceUrlProperties | CodeOps-Logger base URL |

**Dangerous defaults:** JWT_SECRET and VAULT_MASTER_KEY have dev defaults that are profile-gated (dev only). Prod profile requires env vars with no fallback defaults.

---

## 21. Inter-Service Communication Map

**Outbound HTTP:**
- `RotationService.callExternalApi()` → user-provided external API URLs via RestTemplate (for EXTERNAL_API rotation strategy). No SSRF protection.

**Inbound dependencies:**
- CodeOps-Server issues JWT tokens that Vault validates
- Other CodeOps services may call Vault's API to read/write secrets

**Service URL configuration:**
- `codeops.services.server-url` → CodeOps-Server (referenced but not actively called)
- `codeops.services.registry-url` → CodeOps-Registry (referenced but not actively called)
- `codeops.services.logger-url` → CodeOps-Logger (referenced but not actively called)

No active outbound service-to-service HTTP calls except the external API rotation mechanism.
