# CodeOps-Vault -- Codebase Audit

**Audit Date:** 2026-02-18T22:47:49Z
**Branch:** main
**Commit:** b1fc079abcd00a9ed60ef0d55958ad0886340cc1 CV-014: OpenAPI 3.0.3 spec -- 67 endpoints across 8 controllers, 44 schemas
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
Project Name:            CodeOps-Vault
Repository URL:          https://github.com/adamallard/CodeOps-Vault (inferred)
Primary Language:        Java 21 / Spring Boot 3.3.0
Build Tool:              Maven (wrapper)
Current Branch:          main
Latest Commit Hash:      b1fc079abcd00a9ed60ef0d55958ad0886340cc1
Latest Commit Message:   CV-014: OpenAPI 3.0.3 spec -- 67 endpoints across 8 controllers, 44 schemas
Audit Timestamp:         2026-02-18T22:47:49Z
```

---

## 2. Directory Structure

```
CodeOps-Vault/
├── .mvn/wrapper/maven-wrapper.properties
├── CodeOps-Vault-OpenAPI.yaml
├── docker-compose.yml
├── Dockerfile
├── pom.xml
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
│   ├── controller/
│   │   ├── AuditController.java
│   │   ├── DynamicSecretController.java
│   │   ├── PolicyController.java
│   │   ├── RotationController.java
│   │   ├── SealController.java
│   │   ├── SecretController.java
│   │   └── TransitController.java
│   ├── dto/
│   │   ├── mapper/ (6 MapStruct mappers)
│   │   ├── request/ (16 request records)
│   │   └── response/ (14 response records)
│   ├── entity/
│   │   ├── BaseEntity.java
│   │   ├── Secret.java, SecretVersion.java, SecretMetadata.java
│   │   ├── AccessPolicy.java, PolicyBinding.java
│   │   ├── RotationPolicy.java, RotationHistory.java
│   │   ├── DynamicLease.java, TransitKey.java, AuditEntry.java
│   │   └── enums/ (6 enums)
│   ├── exception/ (4 exception classes)
│   ├── repository/ (10 JPA repositories)
│   ├── security/
│   │   ├── SecurityConfig.java, JwtAuthFilter.java
│   │   ├── JwtTokenValidator.java, RateLimitFilter.java
│   │   └── SecurityUtils.java
│   └── service/
│       ├── EncryptionService.java, SecretService.java
│       ├── PolicyService.java, RotationService.java
│       ├── TransitService.java, DynamicSecretService.java
│       ├── SealService.java, AuditService.java
│       ├── HkdfUtil.java, AccessDecision.java
│       ├── KeyVersion.java, GeneratedDataKey.java
│       ├── RotationScheduler.java, LeaseExpiryScheduler.java
├── src/main/resources/
│   ├── application.yml, application-dev.yml
│   ├── application-prod.yml, application-test.yml
│   ├── application-integration.yml, logback-spring.xml
└── src/test/java/com/codeops/vault/ (62 unit test files)
```

Single-module Maven project. Source code under `src/main/java/com/codeops/vault/`, organized by layer: config, controller, dto, entity, exception, repository, security, service.

---

## 3. Build & Dependency Manifest

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | 3.3.0 (parent) | REST API framework |
| spring-boot-starter-data-jpa | 3.3.0 (parent) | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 (parent) | Authentication/authorization |
| spring-boot-starter-validation | 3.3.0 (parent) | Jakarta Bean Validation |
| postgresql | runtime (managed) | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token validation |
| lombok | 1.18.42 (overridden) | Boilerplate reduction |
| mapstruct | 1.5.5.Final | DTO mapping |
| jackson-datatype-jsr310 | managed | Java 8+ date/time serialization |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | Swagger UI / OpenAPI generation |
| logstash-logback-encoder | 7.4 | Structured JSON logging |
| spring-boot-starter-test | 3.3.0 (parent) | Test framework |
| spring-security-test | managed | Security test utilities |
| testcontainers (postgresql + junit-jupiter) | 1.19.8 | Container-based integration tests (present but unused) |
| h2 | managed | In-memory test database |

**Version Overrides (Java 25 compatibility):** `mockito 5.21.0`, `byte-buddy 1.18.4`

**Build Plugins:**
1. `spring-boot-maven-plugin` -- excludes Lombok from fat JAR
2. `maven-compiler-plugin` -- source/target 21, annotationProcessorPaths for Lombok + MapStruct
3. `maven-surefire-plugin` -- `--add-opens` for Java 25 reflection, includes `*Test.java` and `*IT.java`
4. `jacoco-maven-plugin` 0.8.14 -- prepare-agent + report in test phase

```
Build:   ./mvnw clean package -DskipTests
Test:    ./mvnw test
Run:     ./mvnw spring-boot:run
Package: ./mvnw clean package
```

---

## 4. Configuration & Infrastructure Summary

- **`src/main/resources/application.yml`** -- Default profile `dev`, server port `8097`.
- **`src/main/resources/application-dev.yml`** -- PostgreSQL at `localhost:5436/codeops_vault`, `ddl-auto: update`, `show-sql: true`. Dev defaults for `JWT_SECRET` and `VAULT_MASTER_KEY`. Seal auto-unseal true. Dynamic secrets `execute-sql: false`. CORS allows `localhost:3000,3200,5173`. Service URLs for server(8095), registry(8096), logger(8098).
- **`src/main/resources/application-prod.yml`** -- All values via env vars (no defaults for `DATABASE_URL`, `JWT_SECRET`, `VAULT_MASTER_KEY`, `CORS_ALLOWED_ORIGINS`). `ddl-auto: validate`, `show-sql: false`, auto-unseal defaults to false.
- **`src/main/resources/application-test.yml`** -- H2 in-memory `MODE=PostgreSQL`, `ddl-auto: create-drop`. Fixed test secrets for JWT and master key. All external services point to localhost.
- **`src/main/resources/logback-spring.xml`** -- (exists, provides profile-based logging)
- **`docker-compose.yml`** -- PostgreSQL 16 Alpine, container `codeops-vault-db`, port `127.0.0.1:5436:5432`, named volume `codeops-vault-data`, healthcheck via `pg_isready`.
- **`Dockerfile`** -- `eclipse-temurin:21-jre-alpine`, non-root user `appuser`, `EXPOSE 8097`, `java -jar app.jar`.

**Connection Map:**
```
Database:       PostgreSQL, localhost, 5436, codeops_vault
Cache:          None
Message Broker: None
External APIs:  RestTemplate configured (10s connect, 30s read) -- used by RotationService for EXTERNAL_API strategy
Cloud Services: None
```

**CI/CD:** None detected.

---

## 5. Startup & Runtime Behavior

- **Entry point:** `CodeOpsVaultApplication.java` (standard `@SpringBootApplication`)
- **@PostConstruct initialization:**
  1. `VaultProperties.validateMasterKey()` -- fails if master key < 32 chars
  2. `JwtTokenValidator.validateSecret()` -- fails if JWT secret < 32 chars
  3. `EncryptionService.validateMasterKey()` -- test encrypt/decrypt cycle to verify AES-256-GCM works
  4. `SealService.initialize()` -- auto-unseals if `auto-unseal=true`, otherwise stays SEALED
- **Seed data:** `DataSeeder` (`@Profile("dev")` CommandLineRunner) seeds 3 secrets, 2 policies, 3 bindings, 1 rotation policy, 1 transit key. Values stored as `SEED:` + Base64 (not encrypted).
- **Scheduled tasks:**
  - `RotationScheduler` (`@Profile("!test")`) -- every 60s, calls `RotationService.processDueRotations()`
  - `LeaseExpiryScheduler` (`@Profile("!test")`) -- every 30s, calls `DynamicSecretService.processExpiredLeases()`
- **Health check:** `GET /health` returns `{"status": "UP"}` (HealthController, permitAll)

---

## 6. Entity / Data Model Layer

### BaseEntity.java
```
@MappedSuperclass
Primary Key: id: UUID (GenerationType.UUID)
Fields:
  - createdAt: Instant [@Column(nullable=false, updatable=false)] @PrePersist
  - updatedAt: Instant [@Column] @PreUpdate
```

### Secret.java
```
Table: "secrets"
Extends: BaseEntity

Fields:
  - teamId: UUID [@Column(nullable=false)]
  - path: String [@Column(nullable=false, length=500)]
  - name: String [@Column(nullable=false, length=200)]
  - description: String [@Column(columnDefinition="TEXT")]
  - secretType: SecretType [@Enumerated(STRING), @Column(nullable=false, length=20)]
  - currentVersion: Integer [@Column(nullable=false)] default=1
  - maxVersions: Integer [@Column] nullable
  - retentionDays: Integer [@Column] nullable
  - expiresAt: Instant [@Column] nullable
  - lastAccessedAt: Instant [@Column] nullable
  - lastRotatedAt: Instant [@Column] nullable
  - ownerUserId: UUID [@Column] nullable
  - referenceArn: String [@Column(length=500)] nullable
  - metadataJson: String [@Column(columnDefinition="TEXT")] nullable
  - isActive: Boolean [@Column(nullable=false)] default=true

Relationships:
  - versions: @OneToMany -> SecretVersion (mappedBy="secret", CascadeType.ALL, orphanRemoval=true)
  - rotationPolicy: @OneToOne -> RotationPolicy (mappedBy="secret", CascadeType.ALL, orphanRemoval=true)

Unique Constraints: uk_secret_team_path (team_id, path)
Indexes: idx_secret_team_id (team_id), idx_secret_path (path), idx_secret_type (secret_type)
```

### SecretVersion.java
```
Table: "secret_versions"
Extends: BaseEntity

Fields:
  - secret: Secret [@ManyToOne(LAZY), @JoinColumn(nullable=false)]
  - versionNumber: Integer [@Column(nullable=false)]
  - encryptedValue: String [@Column(nullable=false, columnDefinition="TEXT")]
  - encryptionKeyId: String [@Column(length=100)]
  - changeDescription: String [@Column(length=500)]
  - createdByUserId: UUID [@Column] nullable
  - isDestroyed: Boolean [@Column(nullable=false)] default=false

Unique Constraints: uk_sv_secret_version (secret_id, version_number)
Indexes: idx_sv_secret_id (secret_id), idx_sv_version (version_number)
```

### SecretMetadata.java
```
Table: "secret_metadata"
Extends: BaseEntity

Fields:
  - secret: Secret [@ManyToOne(LAZY), @JoinColumn(nullable=false)]
  - metadataKey: String [@Column(nullable=false, length=200)]
  - metadataValue: String [@Column(nullable=false, columnDefinition="TEXT")]

Unique Constraints: uk_sm_secret_key (secret_id, metadata_key)
Indexes: idx_sm_secret_id (secret_id)
```

### AccessPolicy.java
```
Table: "access_policies"
Extends: BaseEntity

Fields:
  - teamId: UUID [@Column(nullable=false)]
  - name: String [@Column(nullable=false, length=200)]
  - description: String [@Column(columnDefinition="TEXT")]
  - pathPattern: String [@Column(nullable=false, length=500)]
  - permissions: String [@Column(nullable=false, length=200)] -- comma-separated PolicyPermission values
  - isDenyPolicy: Boolean [@Column(nullable=false)] default=false
  - isActive: Boolean [@Column(nullable=false)] default=true
  - createdByUserId: UUID [@Column] nullable

Unique Constraints: uk_ap_team_name (team_id, name)
Indexes: idx_ap_team_id (team_id)
```

### PolicyBinding.java
```
Table: "policy_bindings"
Extends: BaseEntity

Fields:
  - policy: AccessPolicy [@ManyToOne(LAZY), @JoinColumn(nullable=false)]
  - bindingType: BindingType [@Enumerated(STRING), @Column(nullable=false, length=20)]
  - bindingTargetId: UUID [@Column(nullable=false)]
  - createdByUserId: UUID [@Column] nullable

Unique Constraints: uk_pb_policy_type_target (policy_id, binding_type, binding_target_id)
Indexes: idx_pb_policy_id (policy_id), idx_pb_target (binding_target_id)
```

### RotationPolicy.java
```
Table: "rotation_policies"
Extends: BaseEntity

Fields:
  - secret: Secret [@OneToOne(LAZY), @JoinColumn(nullable=false, unique=true)]
  - strategy: RotationStrategy [@Enumerated(STRING), @Column(nullable=false, length=30)]
  - rotationIntervalHours: Integer [@Column(nullable=false)]
  - randomLength: Integer [@Column] nullable
  - randomCharset: String [@Column(length=100)] nullable
  - externalApiUrl: String [@Column(length=500)] nullable
  - externalApiHeaders: String [@Column(columnDefinition="TEXT")] nullable
  - scriptCommand: String [@Column(columnDefinition="TEXT")] nullable
  - lastRotatedAt: Instant [@Column] nullable
  - nextRotationAt: Instant [@Column] nullable
  - isActive: Boolean [@Column(nullable=false)] default=true
  - failureCount: Integer [@Column(nullable=false)] default=0
  - maxFailures: Integer [@Column] nullable

Indexes: idx_rp_next_rotation (next_rotation_at), idx_rp_active (is_active)
```

### RotationHistory.java
```
Table: "rotation_history"
Extends: BaseEntity

Fields:
  - secretId: UUID [@Column(nullable=false)] -- NOT a FK
  - secretPath: String [@Column(nullable=false, length=500)]
  - strategy: RotationStrategy [@Enumerated(STRING), @Column(nullable=false, length=30)]
  - previousVersion: Integer [@Column] nullable
  - newVersion: Integer [@Column] nullable
  - success: Boolean [@Column(nullable=false)]
  - errorMessage: String [@Column(columnDefinition="TEXT")]
  - durationMs: Long [@Column] nullable
  - triggeredByUserId: UUID [@Column] nullable

Indexes: idx_rh_secret_id (secret_id), idx_rh_created_at (created_at)
```

### DynamicLease.java
```
Table: "dynamic_leases"
Extends: BaseEntity

Fields:
  - leaseId: String [@Column(nullable=false, unique=true, length=100)]
  - secretId: UUID [@Column(nullable=false)] -- NOT a FK
  - secretPath: String [@Column(nullable=false, length=500)]
  - backendType: String [@Column(nullable=false, length=50)]
  - credentials: String [@Column(nullable=false, columnDefinition="TEXT")] -- encrypted JSON
  - status: LeaseStatus [@Enumerated(STRING), @Column(nullable=false, length=20)]
  - ttlSeconds: Integer [@Column(nullable=false)]
  - expiresAt: Instant [@Column(nullable=false)]
  - revokedAt: Instant [@Column] nullable
  - revokedByUserId: UUID [@Column] nullable
  - requestedByUserId: UUID [@Column] nullable
  - metadataJson: String [@Column(columnDefinition="TEXT")]

Indexes: idx_dl_lease_id (lease_id), idx_dl_secret_id (secret_id), idx_dl_status (status), idx_dl_expires_at (expires_at)
```

### TransitKey.java
```
Table: "transit_keys"
Extends: BaseEntity

Fields:
  - teamId: UUID [@Column(nullable=false)]
  - name: String [@Column(nullable=false, length=200)]
  - description: String [@Column(columnDefinition="TEXT")]
  - currentVersion: Integer [@Column(nullable=false)] default=1
  - minDecryptionVersion: Integer [@Column(nullable=false)] default=1
  - keyMaterial: String [@Column(nullable=false, columnDefinition="TEXT")] -- encrypted JSON array
  - algorithm: String [@Column(nullable=false, length=30)]
  - isDeletable: Boolean [@Column(nullable=false)] default=false
  - isExportable: Boolean [@Column(nullable=false)] default=false
  - isActive: Boolean [@Column(nullable=false)] default=true
  - createdByUserId: UUID [@Column] nullable

Unique Constraints: uk_tk_team_name (team_id, name)
Indexes: idx_tk_team_id (team_id)
```

### AuditEntry.java
```
Table: "vault_audit_log"
Does NOT extend BaseEntity -- uses Long auto-increment PK

Fields:
  - id: Long [@Id, @GeneratedValue(IDENTITY)]
  - teamId: UUID [@Column] nullable
  - userId: UUID [@Column] nullable
  - operation: String [@Column(nullable=false, length=50)]
  - path: String [@Column(length=500)]
  - resourceType: String [@Column(length=50)]
  - resourceId: UUID [@Column] nullable
  - success: Boolean [@Column(nullable=false)]
  - errorMessage: String [@Column(columnDefinition="TEXT")]
  - ipAddress: String [@Column(length=45)]
  - correlationId: String [@Column(length=100)]
  - detailsJson: String [@Column(columnDefinition="TEXT")]
  - createdAt: Instant [@Column(nullable=false)] @PrePersist

Indexes: idx_val_team_id (team_id), idx_val_user_id (user_id), idx_val_operation (operation),
         idx_val_path (path), idx_val_created_at (created_at), idx_val_resource (resource_type, resource_id)
```

### Entity Relationship Summary
```
Secret --[OneToMany]--> SecretVersion (via secret_id FK)
SecretVersion --[ManyToOne]--> Secret
Secret --[OneToMany]--> SecretMetadata (via secret_id FK, implicit from JoinColumn)
SecretMetadata --[ManyToOne]--> Secret
Secret --[OneToOne]--> RotationPolicy (via secret_id FK, unique)
RotationPolicy --[OneToOne]--> Secret
PolicyBinding --[ManyToOne]--> AccessPolicy (via policy_id FK)
RotationHistory.secretId -- plain UUID, NOT a FK (history preserved on delete)
DynamicLease.secretId -- plain UUID, NOT a FK (lease preserved on delete)
AuditEntry -- no relationships (standalone audit log)
```

---

## 7. Enum Definitions

```
=== SecretType.java ===
Values: STATIC, DYNAMIC, REFERENCE
Used By: Secret.secretType

=== PolicyPermission.java ===
Values: READ, WRITE, DELETE, LIST, ROTATE
Used By: AccessPolicy.permissions (stored as comma-separated string)

=== BindingType.java ===
Values: USER, TEAM, SERVICE
Used By: PolicyBinding.bindingType

=== RotationStrategy.java ===
Values: RANDOM_GENERATE, EXTERNAL_API, CUSTOM_SCRIPT
Used By: RotationPolicy.strategy, RotationHistory.strategy

=== LeaseStatus.java ===
Values: ACTIVE, EXPIRED, REVOKED
Used By: DynamicLease.status

=== SealStatus.java ===
Values: SEALED, UNSEALED, UNSEALING
Used By: SealService (in-memory state, not persisted)
```

---

## 8. Repository Layer

### SecretRepository.java
```
Extends: JpaRepository<Secret, UUID>

Derived Query Methods:
  - Optional<Secret> findByTeamIdAndPath(UUID teamId, String path)
  - List<Secret> findByTeamId(UUID teamId)
  - Page<Secret> findByTeamId(UUID teamId, Pageable pageable)
  - Page<Secret> findByTeamIdAndSecretType(UUID teamId, SecretType secretType, Pageable pageable)
  - Page<Secret> findByTeamIdAndPathStartingWith(UUID teamId, String pathPrefix, Pageable pageable)
  - Page<Secret> findByTeamIdAndIsActiveTrue(UUID teamId, Pageable pageable)
  - Page<Secret> findByTeamIdAndNameContainingIgnoreCase(UUID teamId, String name, Pageable pageable)
  - List<Secret> findByTeamIdAndPathStartingWithAndIsActiveTrue(UUID teamId, String pathPrefix)
  - boolean existsByTeamIdAndPath(UUID teamId, String path)
  - long countByTeamId(UUID teamId)
  - long countByTeamIdAndSecretType(UUID teamId, SecretType secretType)
  - List<Secret> findByExpiresAtBeforeAndIsActiveTrue(Instant now)
```

### SecretVersionRepository.java
```
Extends: JpaRepository<SecretVersion, UUID>

Derived Query Methods:
  - List<SecretVersion> findBySecretIdOrderByVersionNumberDesc(UUID secretId)
  - Page<SecretVersion> findBySecretId(UUID secretId, Pageable pageable)
  - Optional<SecretVersion> findBySecretIdAndVersionNumber(UUID secretId, Integer versionNumber)
  - Optional<SecretVersion> findTopBySecretIdOrderByVersionNumberDesc(UUID secretId)
  - long countBySecretId(UUID secretId)
  - List<SecretVersion> findBySecretIdAndVersionNumberLessThan(UUID secretId, Integer version)
  - void deleteBySecretIdAndVersionNumberLessThan(UUID secretId, Integer version)
  - List<SecretVersion> findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(UUID secretId)
```

### SecretMetadataRepository.java
```
Extends: JpaRepository<SecretMetadata, UUID>

Derived Query Methods:
  - List<SecretMetadata> findBySecretId(UUID secretId)
  - Optional<SecretMetadata> findBySecretIdAndMetadataKey(UUID secretId, String metadataKey)
  - void deleteBySecretIdAndMetadataKey(UUID secretId, String metadataKey)
  - void deleteBySecretId(UUID secretId)
  - boolean existsBySecretIdAndMetadataKey(UUID secretId, String metadataKey)
```

### AccessPolicyRepository.java
```
Extends: JpaRepository<AccessPolicy, UUID>

Derived Query Methods:
  - Optional<AccessPolicy> findByTeamIdAndName(UUID teamId, String name)
  - List<AccessPolicy> findByTeamId(UUID teamId)
  - Page<AccessPolicy> findByTeamId(UUID teamId, Pageable pageable)
  - Page<AccessPolicy> findByTeamIdAndIsActiveTrue(UUID teamId, Pageable pageable)
  - List<AccessPolicy> findByTeamIdAndIsActiveTrue(UUID teamId)
  - boolean existsByTeamIdAndName(UUID teamId, String name)
  - long countByTeamId(UUID teamId)
```

### PolicyBindingRepository.java
```
Extends: JpaRepository<PolicyBinding, UUID>

Derived Query Methods:
  - List<PolicyBinding> findByPolicyId(UUID policyId)
  - List<PolicyBinding> findByBindingTypeAndBindingTargetId(BindingType bindingType, UUID targetId)
  - Optional<PolicyBinding> findByPolicyIdAndBindingTypeAndBindingTargetId(UUID policyId, BindingType bindingType, UUID targetId)
  - boolean existsByPolicyIdAndBindingTypeAndBindingTargetId(UUID policyId, BindingType bindingType, UUID targetId)
  - void deleteByPolicyId(UUID policyId)
  - long countByPolicyId(UUID policyId)

Custom @Query Methods:
  - @Query("SELECT pb FROM PolicyBinding pb JOIN pb.policy p WHERE p.teamId = :teamId AND p.isActive = true AND pb.bindingType = :bindingType AND pb.bindingTargetId = :targetId")
    List<PolicyBinding> findActiveBindingsForTarget(UUID teamId, BindingType bindingType, UUID targetId)
```

### RotationPolicyRepository.java
```
Extends: JpaRepository<RotationPolicy, UUID>

Derived Query Methods:
  - Optional<RotationPolicy> findBySecretId(UUID secretId)
  - List<RotationPolicy> findByIsActiveTrueAndNextRotationAtBefore(Instant now)
  - List<RotationPolicy> findByIsActiveTrue()
  - long countByIsActiveTrue()
```

### RotationHistoryRepository.java
```
Extends: JpaRepository<RotationHistory, UUID>

Derived Query Methods:
  - Page<RotationHistory> findBySecretId(UUID secretId, Pageable pageable)
  - List<RotationHistory> findBySecretIdOrderByCreatedAtDesc(UUID secretId)
  - Optional<RotationHistory> findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(UUID secretId)
  - long countBySecretId(UUID secretId)
  - long countBySecretIdAndSuccessFalse(UUID secretId)
```

### DynamicLeaseRepository.java
```
Extends: JpaRepository<DynamicLease, UUID>

Derived Query Methods:
  - Optional<DynamicLease> findByLeaseId(String leaseId)
  - List<DynamicLease> findBySecretId(UUID secretId)
  - Page<DynamicLease> findBySecretId(UUID secretId, Pageable pageable)
  - List<DynamicLease> findByStatus(LeaseStatus status)
  - List<DynamicLease> findByStatusAndExpiresAtBefore(LeaseStatus status, Instant now)
  - long countBySecretIdAndStatus(UUID secretId, LeaseStatus status)
  - long countByStatus(LeaseStatus status)
```

### TransitKeyRepository.java
```
Extends: JpaRepository<TransitKey, UUID>

Derived Query Methods:
  - Optional<TransitKey> findByTeamIdAndName(UUID teamId, String name)
  - List<TransitKey> findByTeamId(UUID teamId)
  - Page<TransitKey> findByTeamId(UUID teamId, Pageable pageable)
  - Page<TransitKey> findByTeamIdAndIsActiveTrue(UUID teamId, Pageable pageable)
  - boolean existsByTeamIdAndName(UUID teamId, String name)
  - long countByTeamId(UUID teamId)
```

### AuditEntryRepository.java
```
Extends: JpaRepository<AuditEntry, Long>

Derived Query Methods:
  - Page<AuditEntry> findByTeamId(UUID teamId, Pageable pageable)
  - Page<AuditEntry> findByUserId(UUID userId, Pageable pageable)
  - Page<AuditEntry> findByTeamIdAndOperation(UUID teamId, String operation, Pageable pageable)
  - Page<AuditEntry> findByTeamIdAndPath(UUID teamId, String path, Pageable pageable)
  - Page<AuditEntry> findByTeamIdAndCreatedAtBetween(UUID teamId, Instant start, Instant end, Pageable pageable)
  - Page<AuditEntry> findByTeamIdAndResourceTypeAndResourceId(UUID teamId, String resourceType, UUID resourceId, Pageable pageable)
  - Page<AuditEntry> findByTeamIdAndSuccessFalse(UUID teamId, Pageable pageable)
  - long countByTeamId(UUID teamId)
  - long countByTeamIdAndOperation(UUID teamId, String operation)
  - long countByTeamIdAndSuccessFalse(UUID teamId)
```

---

## 9. Service Layer

### EncryptionService.java
```
Injected Dependencies: VaultProperties

Methods:
  --- encrypt(String plaintext) -> String
      Purpose: Encrypts plaintext using envelope encryption with master-derived KEK.
      Authorization: NONE (internal service)
      Logic: deriveKey("secret-storage") -> encryptWithKey(plaintext, "master-v1", kek)
      Throws: ValidationException (null/empty plaintext)
      Audit Logged: No

  --- encryptWithKey(String plaintext, String keyId, byte[] keyMaterial) -> String
      Purpose: Encrypts plaintext with specific key ID and material (for transit).
      Authorization: NONE
      Logic: Generate random DEK -> AES-GCM encrypt plaintext with DEK -> AES-GCM encrypt DEK with keyMaterial -> Build envelope [version|keyIdLen|keyId|dekBlockLen|dekIV+encDEK|IV|ciphertext] -> Base64 encode
      Throws: ValidationException, CodeOpsVaultException

  --- decrypt(String encryptedEnvelope) -> String
      Purpose: Decrypts envelope using master-derived KEK.
      Authorization: NONE
      Logic: deriveKey("secret-storage") -> decryptWithKey(envelope, kek)
      Throws: ValidationException, CodeOpsVaultException

  --- decryptWithKey(String encryptedEnvelope, byte[] keyMaterial) -> String
      Purpose: Decrypts envelope with specific key material.
      Authorization: NONE
      Logic: Base64 decode -> parse version, keyId, dekBlock, IV, ciphertext -> decrypt DEK with keyMaterial -> decrypt ciphertext with DEK
      Throws: ValidationException, CodeOpsVaultException (AEADBadTagException = tampered data)

  --- deriveKey(String purpose) -> byte[]
      Purpose: Derives purpose-specific key via HKDF from master key.
      Authorization: NONE
      Logic: HkdfUtil.derive(masterKeyBytes, null, "codeops-vault-" + purpose, 32)
      Throws: ValidationException (null/blank purpose)

  --- generateDataKey() -> byte[]
      Purpose: Generates random 32-byte AES key.

  --- generateAndWrapDataKey() -> GeneratedDataKey
      Purpose: Generates random key and returns both plaintext (Base64) and encrypted forms.

  --- rewrap(String encryptedEnvelope, byte[] oldKeyMaterial, byte[] newKeyMaterial, String newKeyId) -> String
      Purpose: Re-encrypts envelope with new key without exposing plaintext.
      Logic: decryptWithKey(old) -> encryptWithKey(new)
      Throws: ValidationException, CodeOpsVaultException

  --- extractKeyId(String encryptedEnvelope) -> String
      Purpose: Extracts key ID from envelope without decrypting.

  --- generateRandomString(int length, String charset) -> String
      Purpose: Generates random string. Charsets: alphanumeric, alpha, numeric, hex, ascii-printable, or custom.
      Throws: ValidationException (length < 1, null charset)

  --- hash(String data) -> String
      Purpose: SHA-256 hash, hex-encoded.

  --- @PostConstruct validateMasterKey()
      Purpose: Fail-fast test encrypt/decrypt cycle at startup.
```

### SecretService.java
```
Injected Dependencies: SecretRepository, SecretVersionRepository, SecretMetadataRepository, EncryptionService, SecretMapper, AuditService

Methods:
  --- createSecret(CreateSecretRequest, UUID teamId, UUID userId) -> SecretResponse
      Purpose: Creates secret at path with encrypted value and optional metadata.
      Authorization: NONE (controller responsibility)
      Logic: Validate path uniqueness -> save Secret entity -> encrypt value -> save SecretVersion(v1) -> save metadata -> audit log
      Throws: ValidationException (duplicate path)
      Audit Logged: Yes (WRITE/SECRET)

  --- getSecretById(UUID secretId) -> SecretResponse
      Purpose: Get secret metadata by ID (never returns value).
      Throws: NotFoundException

  --- getSecretByPath(UUID teamId, String path) -> SecretResponse
      Purpose: Get secret metadata by team+path.
      Throws: NotFoundException

  --- readSecretValue(UUID secretId) -> SecretValueResponse
      Purpose: Decrypt and return current version's value. Updates lastAccessedAt.
      Throws: NotFoundException
      Audit Logged: Yes (READ/SECRET)

  --- readSecretVersionValue(UUID secretId, int versionNumber) -> SecretValueResponse
      Purpose: Decrypt and return specific version's value.
      Throws: NotFoundException, ValidationException (if version destroyed)
      Audit Logged: Yes (READ/SECRET)

  --- listSecrets(UUID teamId, SecretType, String pathPrefix, boolean activeOnly, Pageable) -> PageResponse<SecretResponse>
      Purpose: List secrets with filter priority: secretType > pathPrefix > activeOnly > all.
      Paginated: Yes

  --- searchSecrets(UUID teamId, String query, Pageable) -> PageResponse<SecretResponse>
      Purpose: Search by name (case-insensitive).
      Paginated: Yes

  --- listPaths(UUID teamId, String pathPrefix) -> List<String>
      Purpose: List distinct paths under prefix for directory browsing.

  --- updateSecret(UUID secretId, UpdateSecretRequest, UUID userId) -> SecretResponse
      Purpose: Update secret. If value provided, creates new version + applies retention.
      Logic: If value present: encrypt -> increment version -> save SecretVersion. Update metadata/description/maxVersions/retentionDays/expiresAt if non-null. Apply retention policy.
      Throws: NotFoundException
      Audit Logged: Yes (WRITE/SECRET)

  --- softDeleteSecret(UUID secretId) -> void
      Purpose: Sets isActive=false.
      Audit Logged: Yes (DELETE/SECRET)

  --- hardDeleteSecret(UUID secretId) -> void
      Purpose: Permanently deletes secret, versions, metadata, rotation policy (cascade).
      Audit Logged: Yes (DELETE/SECRET)

  --- listVersions(UUID secretId, Pageable) -> PageResponse<SecretVersionResponse>
      Paginated: Yes

  --- getVersion(UUID secretId, int versionNumber) -> SecretVersionResponse
      Throws: NotFoundException

  --- destroyVersion(UUID secretId, int versionNumber) -> void
      Purpose: Zeroes encrypted value, sets isDestroyed=true. Cannot destroy current version.
      Throws: NotFoundException, ValidationException (current version)
      Audit Logged: Yes (DELETE/SECRET)

  --- applyRetentionPolicy(UUID secretId) -> void
      Purpose: Enforces maxVersions (destroys excess) and retentionDays (destroys old). Never destroys current version.

  --- getMetadata(UUID secretId) -> Map<String, String>
  --- setMetadata(UUID secretId, String key, String value) -> void (upsert)
  --- removeMetadata(UUID secretId, String key) -> void
  --- replaceMetadata(UUID secretId, Map<String, String>) -> void (delete all + add new)

  --- getSecretCounts(UUID teamId) -> Map<String, Long>
      Returns: total, static, dynamic, reference counts.

  --- getExpiringSecrets(UUID teamId, int withinHours) -> List<SecretResponse>
      Purpose: Active secrets expiring within N hours. In-memory filtering on findByTeamId.
```

### PolicyService.java
```
Injected Dependencies: AccessPolicyRepository, PolicyBindingRepository, PolicyMapper, AuditService

Methods:
  --- createPolicy(CreatePolicyRequest, UUID teamId, UUID userId) -> AccessPolicyResponse
      Throws: ValidationException (duplicate name)
      Audit Logged: Yes (POLICY_CREATE/POLICY)

  --- getPolicyById(UUID policyId) -> AccessPolicyResponse
      Throws: NotFoundException

  --- listPolicies(UUID teamId, boolean activeOnly, Pageable) -> PageResponse<AccessPolicyResponse>
      Paginated: Yes

  --- updatePolicy(UUID policyId, UpdatePolicyRequest) -> AccessPolicyResponse
      Purpose: Partial update (non-null fields only). Name change checked for conflicts.
      Throws: NotFoundException, ValidationException
      Audit Logged: Yes (POLICY_UPDATE/POLICY)

  --- deletePolicy(UUID policyId) -> void
      Purpose: Deletes policy and all its bindings.
      Audit Logged: Yes (POLICY_DELETE/POLICY)

  --- createBinding(CreateBindingRequest, UUID userId) -> PolicyBindingResponse
      Throws: NotFoundException, ValidationException (duplicate binding)
      Audit Logged: Yes (BIND/POLICY_BINDING)

  --- listBindingsForPolicy(UUID policyId) -> List<PolicyBindingResponse>
  --- listBindingsForTarget(BindingType, UUID targetId) -> List<PolicyBindingResponse>

  --- deleteBinding(UUID bindingId) -> void
      Audit Logged: Yes (UNBIND/POLICY_BINDING)

  --- evaluateAccess(UUID userId, UUID teamId, String secretPath, PolicyPermission) -> AccessDecision
      Purpose: Core access control. Collects USER + TEAM bindings, evaluates deny-overrides-allow.
      Logic: Find active bindings for user+team -> collect policies -> filter by path match -> check deny first -> check allow -> default deny.

  --- evaluateServiceAccess(UUID serviceId, UUID teamId, String secretPath, PolicyPermission) -> AccessDecision
      Purpose: Access evaluation for SERVICE binding type.

  --- matchesPath(String secretPath, String pathPattern) -> boolean
      Purpose: Glob matching. "*" matches single segment. Same segment count required.

  --- getPolicyCounts(UUID teamId) -> Map<String, Long>
      Returns: total, active, deny counts. In-memory filtering on findByTeamId.
```

### RotationService.java
```
Injected Dependencies: RotationPolicyRepository, RotationHistoryRepository, SecretRepository, SecretService, EncryptionService, RotationMapper, RestTemplate, AuditService

Methods:
  --- createOrUpdatePolicy(CreateRotationPolicyRequest, UUID userId) -> RotationPolicyResponse
      Purpose: Creates or replaces rotation policy for a secret. Sets nextRotationAt.
      Throws: NotFoundException (secret)
      Audit Logged: Yes (POLICY_CREATE/ROTATION_POLICY)

  --- getPolicy(UUID secretId) -> RotationPolicyResponse
      Throws: NotFoundException

  --- updatePolicy(UUID policyId, UpdateRotationPolicyRequest) -> RotationPolicyResponse
      Purpose: Partial update.
      Throws: NotFoundException

  --- deletePolicy(UUID policyId) -> void
      Purpose: Deletes rotation policy. Does NOT delete history.
      Throws: NotFoundException

  --- rotateSecret(UUID secretId, UUID userId) -> RotationHistoryResponse
      Purpose: Executes rotation.
      Logic: Load secret+policy -> generateNewValue(policy) -> secretService.updateSecret(newValue) -> record success in RotationHistory -> update nextRotationAt, reset failureCount.
      On failure: Record failure in history -> increment failureCount -> if >= maxFailures, deactivate policy -> advance nextRotationAt (prevent retry storm).
      Audit Logged: Yes (ROTATE/SECRET)

  --- generateNewValue(RotationPolicy) -> String
      Logic: RANDOM_GENERATE: encryptionService.generateRandomString(length, charset). EXTERNAL_API: callExternalApi(url, headers) via RestTemplate GET. CUSTOM_SCRIPT: throws CodeOpsVaultException ("not yet implemented").

  --- processDueRotations() -> int
      Purpose: Finds all active policies with nextRotationAt in the past and rotates each.

  --- getRotationHistory(UUID secretId, Pageable) -> PageResponse<RotationHistoryResponse>
      Paginated: Yes

  --- getLastSuccessfulRotation(UUID secretId) -> RotationHistoryResponse (or null)

  --- getRotationStats(UUID secretId) -> Map<String, Long>
      Returns: activePolicies, totalRotations, failedRotations.
```

### TransitService.java
```
Injected Dependencies: TransitKeyRepository, EncryptionService, TransitKeyMapper, ObjectMapper, AuditService

Methods:
  --- createKey(CreateTransitKeyRequest, UUID teamId, UUID userId) -> TransitKeyResponse
      Purpose: Creates named transit key with random AES-256 key as version 1.
      Logic: Generate random key -> wrap as KeyVersion(1, base64Key) -> encrypt material with master key -> save.
      Throws: ValidationException (duplicate name)
      Audit Logged: Yes (WRITE/TRANSIT_KEY)

  --- getKeyById(UUID keyId) -> TransitKeyResponse
  --- getKeyByName(UUID teamId, String name) -> TransitKeyResponse

  --- listKeys(UUID teamId, boolean activeOnly, Pageable) -> PageResponse<TransitKeyResponse>
      Paginated: Yes

  --- updateKey(UUID keyId, UpdateTransitKeyRequest) -> TransitKeyResponse
      Purpose: Updates metadata (description, minDecryptionVersion, isDeletable, isExportable, isActive).

  --- rotateKey(UUID keyId) -> TransitKeyResponse
      Purpose: Generates new key version. Decrypt material array -> append new version -> re-encrypt -> save.
      Audit Logged: Yes (ROTATE/TRANSIT_KEY)

  --- deleteKey(UUID keyId) -> void
      Purpose: Permanently deletes key. Only if isDeletable=true.
      Throws: ValidationException (not deletable)
      Audit Logged: Yes (DELETE/TRANSIT_KEY)

  --- encrypt(TransitEncryptRequest, UUID teamId) -> TransitEncryptResponse
      Purpose: Encrypts plaintext with current key version.
      Logic: Load key -> get current version bytes -> encryptWithKey(plaintext, "keyName:vN", keyBytes).
      Audit Logged: Yes (TRANSIT_ENCRYPT/TRANSIT_KEY)

  --- decrypt(TransitDecryptRequest, UUID teamId) -> TransitDecryptResponse
      Purpose: Decrypts ciphertext by extracting key version from envelope.
      Logic: Extract key version from ciphertext -> validate >= minDecryptionVersion -> decryptWithKey.
      Throws: NotFoundException, ValidationException (version below min)
      Audit Logged: Yes (TRANSIT_DECRYPT/TRANSIT_KEY)

  --- rewrap(TransitRewrapRequest, UUID teamId) -> TransitEncryptResponse
      Purpose: Re-encrypts ciphertext with current key version without exposing plaintext.

  --- generateDataKey(String keyName, UUID teamId) -> Map<String, String>
      Purpose: Returns plaintext + ciphertext data key wrapped with named transit key.

  --- getKeyStats(UUID teamId) -> Map<String, Long>
      Returns: total, active counts. In-memory filtering.
```

### DynamicSecretService.java
```
Injected Dependencies: DynamicLeaseRepository, SecretRepository, EncryptionService, LeaseMapper, DynamicSecretProperties, AuditService

Methods:
  --- createLease(CreateDynamicLeaseRequest, UUID userId) -> DynamicLeaseResponse
      Purpose: Generates temporary DB credentials with TTL.
      Logic: Validate secret type=DYNAMIC -> extract backend metadata from metadataJson -> generate username (v_<name>_<uuid8>, max 63 chars) + password -> if executeSql=true: CREATE ROLE/USER on target DB -> encrypt credentials -> save DynamicLease(ACTIVE).
      Throws: NotFoundException, ValidationException (not DYNAMIC type, missing metadata)
      Audit Logged: Yes (LEASE_CREATE/DYNAMIC_LEASE)

  --- getLease(String leaseId) -> DynamicLeaseResponse (no credentials)
  --- listLeases(UUID secretId, Pageable) -> PageResponse<DynamicLeaseResponse>
      Paginated: Yes

  --- revokeLease(String leaseId, UUID userId) -> void
      Purpose: Revokes active lease. Drops DB user if executeSql=true.
      Throws: NotFoundException, ValidationException (not ACTIVE)
      Audit Logged: Yes (LEASE_REVOKE/DYNAMIC_LEASE)

  --- revokeAllLeases(UUID secretId, UUID userId) -> int
      Purpose: Revokes all active leases for a secret.

  --- processExpiredLeases() -> int
      Purpose: Transitions ACTIVE leases past expiresAt to EXPIRED. Best-effort cleanup.

  --- createDatabaseUser(backendType, host, port, database, adminUser, adminPassword, username, password) -> void
      Purpose: Executes CREATE ROLE/USER SQL. PostgreSQL: CREATE ROLE + GRANT CONNECT + GRANT USAGE. MySQL: CREATE USER + GRANT SELECT/INSERT/UPDATE/DELETE.

  --- dropDatabaseUser(...) -> void
      Purpose: DROP ROLE/USER. Best-effort, failures logged not thrown.

  --- getLeaseStats(UUID secretId) -> Map<String, Long>
      Returns: active, expired, revoked counts.

  --- getTotalActiveLeases() -> long
```

### SealService.java
```
Injected Dependencies: VaultProperties, AuditService

State: volatile SealStatus status, List<byte[]> collectedShares, List<Integer> collectedShareIndices, int totalShares(5), int threshold(3)

Methods:
  --- @PostConstruct initialize()
      Purpose: If auto-unseal=true, sets status to UNSEALED.

  --- getStatus() -> SealStatusResponse
  --- isUnsealed() -> boolean
  --- requireUnsealed() -> void
      Throws: CodeOpsVaultException ("Vault is sealed")

  --- seal() -> void [synchronized]
      Purpose: Sets SEALED, clears shares.
      Throws: ValidationException (already sealed)
      Audit Logged: Yes (SEAL/VAULT)

  --- submitKeyShare(String keyShareBase64) -> SealStatusResponse [synchronized]
      Purpose: Submits a Shamir key share. Format: Base64([1-byte index][share bytes]). First share transitions SEALED->UNSEALING. At threshold, reconstructs key via Lagrange interpolation, verifies against master key. If match: UNSEALED. If mismatch: reset to SEALED + throw.
      Throws: ValidationException (already unsealed), CodeOpsVaultException (key mismatch)
      Audit Logged: Yes (UNSEAL/VAULT)

  --- splitSecret(byte[] secret, int totalN, int thresholdM) -> byte[][]
      Purpose: Shamir's Secret Sharing. Random polynomial of degree (M-1) over GF(256) for each byte.

  --- reconstructSecret(byte[][] shares, int[] shareIndices) -> byte[]
      Purpose: Lagrange interpolation at x=0 over GF(256).

  --- generateKeyShares() -> String[]
      Purpose: Splits master key into N shares. Each encoded as Base64 with 1-byte index prefix.
      Throws: ValidationException (not unsealed)
      Audit Logged: Yes (WRITE/VAULT_SHARES)

  --- getSealInfo() -> Map<String, Object>

  --- GF(256) arithmetic: gf256Mul, gf256Div, evaluatePolynomial using LOG/EXP lookup tables with AES polynomial 0x11B.
```

### AuditService.java
```
Injected Dependencies: AuditEntryRepository, AuditMapper

Methods:
  --- logSuccess(UUID teamId, UUID userId, String operation, String path, String resourceType, UUID resourceId, String detailsJson) -> void
      Purpose: Records successful operation. Extracts IP from HttpServletRequest, correlationId from MDC. Failures silently logged.

  --- logFailure(UUID teamId, UUID userId, String operation, String path, String resourceType, UUID resourceId, String errorMessage) -> void
      Purpose: Records failed operation.

  --- writeAuditEntry(AuditEntry entry) -> void [@Transactional(propagation=REQUIRES_NEW)]
      Purpose: Persists in separate transaction to avoid coupling with caller's transaction.

  --- queryAuditLog(UUID teamId, AuditQueryRequest, Pageable) -> PageResponse<AuditEntryResponse>
      Purpose: Queries with filter priority: resourceType+resourceId > userId > operation > path > time range > failuresOnly > all.
      Paginated: Yes

  --- getAuditForResource(UUID teamId, String resourceType, UUID resourceId, Pageable) -> PageResponse<AuditEntryResponse>
      Paginated: Yes

  --- getAuditStats(UUID teamId) -> Map<String, Long>
      Returns: totalEntries, failedEntries, readOperations, writeOperations, deleteOperations.
```

### Supporting Records
```
AccessDecision(boolean allowed, String reason, UUID decidingPolicyId, String decidingPolicyName)
  Static factories: allowed(policyId, policyName), denied(policyId, policyName), defaultDenied()

KeyVersion(int version, String key)
  Used in: TransitService key material JSON array

GeneratedDataKey(String plaintextKey, String encryptedKey)
  Used in: EncryptionService.generateAndWrapDataKey()
```

### HkdfUtil.java
```
Static utility. HMAC-SHA256 based HKDF per RFC 5869.
  - derive(byte[] ikm, byte[] salt, byte[] info, int outputLength) -> byte[]
  - extract(byte[] salt, byte[] ikm) -> byte[] (PRK)
  - expand(byte[] prk, byte[] info, int outputLength) -> byte[] (OKM)
```

### Schedulers
```
RotationScheduler (@Profile("!test"), @Scheduled(fixedDelay=60000))
  -> RotationService.processDueRotations()

LeaseExpiryScheduler (@Profile("!test"), @Scheduled(fixedDelay=30000))
  -> DynamicSecretService.processExpiredLeases()
```

---

## 10. Security Architecture

**Authentication Flow:**
- JWT tokens are issued by CodeOps-Server. This service only validates (never issues).
- JwtAuthFilter extracts Bearer token from Authorization header.
- JwtTokenValidator verifies HMAC-SHA signature using jjwt 0.12.6 with shared secret.
- Claims extracted: sub (userId UUID), teamId (UUID), roles (List), permissions (List).
- Principal is set to UUID; teamId stored in authentication details map.
- No token revocation/blacklist mechanism.

**Authorization Model:**
- Roles from JWT mapped to SimpleGrantedAuthority with ROLE_ prefix.
- Permissions from JWT mapped to plain SimpleGrantedAuthority.
- Controllers use SecurityUtils.getCurrentUserId() and getCurrentTeamId() to extract context.
- PolicyService.evaluateAccess() provides path-based access control but is NOT enforced by controllers automatically -- must be called explicitly.

**Security Filter Chain (order):**
1. RequestCorrelationFilter (adds X-Correlation-ID to MDC)
2. RateLimitFilter (per-IP, 100 req/60s on /api/v1/vault/**)
3. JwtAuthFilter (Bearer token extraction and validation)

**Public Endpoints (permitAll):**
- `GET /health`
- `GET /api/v1/vault/seal/status`
- `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`

**All other `/api/**` require authentication.**

**CORS:** Origins from config (dev: localhost:3000,3200,5173). Methods: GET/POST/PUT/DELETE/PATCH/OPTIONS. Credentials: true. MaxAge: 3600.

**Encryption:**
- AES-256-GCM envelope encryption. Master key -> HKDF -> KEK. Random DEK per encryption.
- Envelope format: [version(1B)|keyIdLen(4B)|keyId|dekBlockLen(4B)|dekIV(12B)+encDEK|IV(12B)|ciphertext+GCMtag]
- Master key configured via `VAULT_MASTER_KEY` env var (min 32 chars, validated at startup).

**Password Policy:** N/A -- this service does not manage user passwords.

**Rate Limiting:**
- Per-IP, ConcurrentHashMap sliding window, 100 requests per 60 seconds on /api/v1/vault/**
- Returns HTTP 429 JSON response: `{"status":429,"message":"Rate limit exceeded. Try again later."}`
- IP resolved from X-Forwarded-For header or remoteAddr.

**Security Headers:**
- Content-Security-Policy: `default-src 'self'; frame-ancestors 'none'`
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- Strict-Transport-Security: max-age=31536000, includeSubDomains
- CSRF: disabled (stateless JWT API)

---

## 11. Notification / Messaging Layer

No notification or messaging layer exists in this project.

---

## 12. Error Handling

```
Exception Type                       -> HTTP Status -> Response Body
NotFoundException                    -> 404         -> {"message": "<entity> not found...", "timestamp": "..."}
ValidationException                  -> 400         -> {"message": "<validation detail>", "timestamp": "..."}
AuthorizationException               -> 403         -> {"message": "<auth detail>", "timestamp": "..."}
AccessDeniedException (Spring)       -> 403         -> {"message": "Access denied", "timestamp": "..."}
MethodArgumentNotValidException      -> 400         -> {"message": "field1: msg, field2: msg", "timestamp": "..."}
HttpMessageNotReadableException      -> 400         -> {"message": "Malformed request body", "timestamp": "..."}
NoResourceFoundException             -> 404         -> {"message": "Resource not found", "timestamp": "..."}
CodeOpsVaultException                -> 500         -> {"message": "An unexpected error occurred", "timestamp": "..."}
Exception (catch-all)                -> 500         -> {"message": "An unexpected error occurred", "timestamp": "..."}
```

Exception hierarchy: `CodeOpsVaultException` (base RuntimeException) -> `NotFoundException`, `ValidationException`, `AuthorizationException`. Internal details logged server-side, never exposed to clients for 500 errors. For 4xx errors, the application exception message IS exposed.

---

## 13. Test Coverage

- **Unit test files:** 62
- **Integration test files:** 0
- **@Test methods:** 628 (unit), 0 (integration)
- **Test framework:** JUnit 5 + Mockito 5.21.0 + Spring Boot Test
- **Test database:** H2 in-memory with `MODE=PostgreSQL`, `ddl-auto: create-drop`
- **Test config:** `src/main/resources/application-test.yml`
- **Testcontainers:** Dependency present (postgresql + junit-jupiter 1.19.8) but unused (0 integration tests)
- **Schedulers excluded in test:** Both schedulers annotated `@Profile("!test")`

---

## 14. Cross-Cutting Patterns & Conventions

- **Naming conventions:** Controllers use REST-style methods (create/get/list/update/delete). Services match controller method names. DTOs follow `Create*Request`, `Update*Request`, `*Response` pattern.
- **Package structure:** Layer-based (config, controller, dto, entity, exception, repository, security, service).
- **Base classes:** `BaseEntity` (@MappedSuperclass) with UUID PK and audit timestamps. `AuditEntry` is the only entity that does NOT extend it (uses Long PK for write perf).
- **Audit logging:** Called in try/catch at the end of each service method. Failures are caught and logged at WARN, never blocking the primary operation. AuditService.writeAuditEntry uses `REQUIRES_NEW` propagation.
- **Error handling:** Exceptions thrown from service layer, caught by `GlobalExceptionHandler` in controller advice.
- **Pagination:** `PageResponse<T>` record wrapping content, page number, size, totalElements, totalPages, isLast. Default page size 20, max 100 (AppConstants).
- **Validation:** Jakarta Bean Validation on request DTOs + business validation in services (throwing ValidationException).
- **Constants:** `AppConstants` -- centralized, includes API prefix, pagination limits, encryption params, rate limit config.
- **Documentation:** Javadoc on all classes and public methods. Entities, services, security, and config classes fully documented.
- **Permissions stored as strings:** AccessPolicy.permissions is a comma-separated string (e.g., "READ,WRITE,LIST"), parsed in PolicyService.

---

## 15. Known Issues, TODOs, and Technical Debt

No TODOs, FIXMEs, or HACKs found in source code. Observations:

1. **CUSTOM_SCRIPT rotation not implemented:** `RotationService.generateNewValue()` throws `CodeOpsVaultException("CUSTOM_SCRIPT rotation strategy is not yet implemented")` for the CUSTOM_SCRIPT strategy.

2. **Policy evaluation not enforced:** `PolicyService.evaluateAccess()` exists but controllers do not call it before operations. Access control relies solely on JWT authentication -- no path-based authorization enforcement.

3. **Seed data not encrypted:** `DataSeeder` stores values as `"SEED:" + Base64` rather than using `EncryptionService.encrypt()`. These are not valid encrypted envelopes and will fail decryption.

4. **In-memory filtering in some methods:** `SecretService.getExpiringSecrets()` loads all team secrets then filters in memory. `PolicyService.getPolicyCounts()` and `TransitService.getKeyStats()` also use in-memory filtering on full team result sets.

---

## 16. OpenAPI Specification

See `CodeOps-Vault-OpenAPI.yaml` in the project root for the complete API specification covering 67 endpoints across 8 controllers (Secrets, Policies, Rotation, Transit, Dynamic Secrets, Seal, Audit, Health) with 44 schemas.

---

## 17. Database -- Live Schema Audit

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
| `DB_USERNAME` | No | `codeops` | application-dev.yml | Database username |
| `DB_PASSWORD` | No | `codeops` | application-dev.yml | Database password |
| `DATABASE_URL` | Yes (prod) | none | application-prod.yml | JDBC connection URL |
| `DATABASE_USERNAME` | Yes (prod) | none | application-prod.yml | Database username |
| `DATABASE_PASSWORD` | Yes (prod) | none | application-prod.yml | Database password |
| `JWT_SECRET` | Yes (prod) | dev default (dev only) | JwtProperties | HMAC signing secret (>= 32 chars) |
| `VAULT_MASTER_KEY` | Yes (prod) | dev default (dev only) | VaultProperties | AES-256 master encryption key (>= 32 chars) |
| `VAULT_AUTO_UNSEAL` | No | `false` (prod) | SealService | Auto-unseal at startup |
| `VAULT_TOTAL_SHARES` | No | `5` | SealService | Shamir total share count |
| `VAULT_THRESHOLD` | No | `3` | SealService | Shamir threshold |
| `CORS_ALLOWED_ORIGINS` | Yes (prod) | none | CorsConfig | Allowed CORS origins |
| `CODEOPS_SERVER_URL` | No | `http://localhost:8095` | ServiceUrlProperties | CodeOps-Server URL |
| `CODEOPS_REGISTRY_URL` | No | `http://localhost:8096` | ServiceUrlProperties | CodeOps-Registry URL |
| `CODEOPS_LOGGER_URL` | No | `http://localhost:8098` | ServiceUrlProperties | CodeOps-Logger URL |

**Dangerous defaults:** `JWT_SECRET` and `VAULT_MASTER_KEY` have hardcoded dev defaults in `application-dev.yml`. These are NOT present in `application-prod.yml` (env vars required). Dev defaults are too weak for production.

---

## 21. Inter-Service Communication Map

**Outbound HTTP:**
- `RestTemplate` bean configured in `RestTemplateConfig` (10s connect timeout, 30s read timeout).
- `RotationService` uses RestTemplate for `EXTERNAL_API` rotation strategy -- calls arbitrary URLs configured in `RotationPolicy.externalApiUrl`.
- `ServiceUrlProperties` holds URLs for CodeOps-Server, CodeOps-Registry, and CodeOps-Logger, but no service code actively calls them.

**Inbound:** Other CodeOps services (Server, Registry, Client) connect to this service's API at port 8097 using JWT tokens issued by CodeOps-Server.

Standalone service with no active outbound service-to-service HTTP calls (only external API rotation, which is user-configured).
