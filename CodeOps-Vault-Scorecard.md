# CodeOps-Vault -- Quality Scorecard

**Audit Date:** 2026-02-18T22:47:49Z
**Branch:** main
**Commit:** b1fc079abcd00a9ed60ef0d55958ad0886340cc1

> This scorecard is a quality assessment companion to `CodeOps-Vault-Audit.md`. It is NOT loaded into coding sessions -- it exists for architect review only.

---

## Security (10 checks, max 20)

| Check | Description | Score | Notes |
|---|---|---|---|
| SEC-01 | Auth on all mutation endpoints | 1 | No `@PreAuthorize`/`@Secured` annotations on controllers. Authentication enforced via SecurityConfig filter chain (all `/api/**` require auth), but no method-level authorization. |
| SEC-02 | No hardcoded secrets in source | 1 | Dev YAML has hardcoded defaults for `JWT_SECRET` and `VAULT_MASTER_KEY` (with `${ENV_VAR:default}` syntax). Prod YAML uses env vars only. Acceptable for dev, but dev defaults are real keys. |
| SEC-03 | Input validation on all request DTOs | 2 | 15 of 16 request DTOs have Jakarta validation annotations (@NotBlank, @NotNull, @Size, @Min, @Max). Only AuditQueryRequest lacks annotations (all fields are optional filters). |
| SEC-04 | CORS not using wildcards | 2 | CORS origins configured from properties, no wildcards. Dev uses localhost:3000,3200,5173. Prod requires `CORS_ALLOWED_ORIGINS` env var. |
| SEC-05 | Encryption key not hardcoded in Java | 2 | Keys injected via `@ConfigurationProperties` from YAML. No hardcoded keys in Java source. |
| SEC-06 | Security headers configured | 2 | CSP, X-Frame-Options DENY, X-Content-Type-Options nosniff, HSTS with includeSubDomains all configured in SecurityConfig. |
| SEC-07 | Rate limiting present | 2 | RateLimitFilter: per-IP, 100 req/60s sliding window on `/api/v1/vault/**`. Returns 429 JSON. |
| SEC-08 | SSRF protection on outbound URLs | 0 | RotationService calls arbitrary `externalApiUrl` from RotationPolicy without URL validation. No loopback/private IP checks. **BLOCKING ISSUE.** |
| SEC-09 | Token revocation / logout | 0 | No JWT blacklist or token revocation mechanism. "Revoke" references are all dynamic lease revocation, not JWT tokens. **BLOCKING ISSUE.** |
| SEC-10 | Password complexity enforcement | 0 | N/A -- this service does not manage user passwords. Scored 0 per template but not a real issue for this service type. |

**Security Score: 12 / 20 (60%)**

---

## Data Integrity (8 checks, max 16)

| Check | Description | Score | Notes |
|---|---|---|---|
| DAT-01 | All enum fields use @Enumerated(STRING) | 2 | All 5 `@Enumerated` annotations use `EnumType.STRING`. |
| DAT-02 | Database indexes on FK columns | 2 | 24 `@Index` annotations across entities covering FK columns and query-heavy fields. |
| DAT-03 | Nullable constraints on required fields | 2 | 52 `nullable=false` constraints. Required fields properly annotated. |
| DAT-04 | Optimistic locking (@Version) | 0 | No `@Version` fields on any entity. Concurrent updates could cause lost writes. **BLOCKING ISSUE.** |
| DAT-05 | No unbounded queries | 1 | 18 `List<>` return methods in repositories without Pageable. Most are filtered by team/secret ID (bounded by data design), but no hard limit. |
| DAT-06 | No in-memory filtering of DB results | 1 | 4 instances of stream filtering in services: `getExpiringSecrets()`, `getPolicyCounts()`, `getKeyStats()`. Should use DB queries. |
| DAT-07 | Proper relationship mapping | 1 | `AccessPolicy.permissions` stores comma-separated `PolicyPermission` values as a String. Should be a normalized join table or `@ElementCollection`. 20 references to comma string parsing/joining. |
| DAT-08 | Audit timestamps on entities | 2 | `BaseEntity` provides `createdAt`/`updatedAt` via `@PrePersist`/`@PreUpdate`. 10 of 11 entities extend it (AuditEntry manages its own `createdAt`). |

**Data Integrity Score: 11 / 16 (69%)**

---

## API Quality (8 checks, max 16)

| Check | Description | Score | Notes |
|---|---|---|---|
| API-01 | Consistent error responses | 2 | Single `GlobalExceptionHandler` maps all exceptions to consistent `ErrorResponse(message, timestamp)` format. |
| API-02 | Error messages sanitized | 2 | 4xx errors expose application exception messages (acceptable). 500 errors (CodeOpsVaultException + catch-all) return generic "An unexpected error occurred". Internal details logged server-side only. |
| API-03 | Audit logging on mutations | 2 | Audit logging implemented in service layer (24+ `logSuccess`/`logFailure` calls). All CRUD and business operations logged with team/user/operation/path/resource context. |
| API-04 | Pagination on list endpoints | 2 | All list endpoints accept `Pageable` parameters. `PageResponse<T>` wrapper used consistently. Default size 20, max 100 from `AppConstants`. |
| API-05 | Correct HTTP status codes | 2 | 61 `ResponseEntity` usages with proper status codes: 200 OK, 201 Created, 204 No Content used appropriately. |
| API-06 | OpenAPI / Swagger documented | 2 | `springdoc-openapi-starter-webmvc-ui 2.5.0` configured. `OpenApiConfig.java` provides metadata. 67 endpoints documented in OpenAPI spec. |
| API-07 | Consistent DTO naming | 2 | 31 DTOs follow `Create*Request`, `Update*Request`, `*Response` naming convention. MapStruct mappers with consistent naming. |
| API-08 | File upload validation | 0 | N/A -- no file upload endpoints exist. Scored 0 per template but not applicable. |

**API Quality Score: 14 / 16 (88%)**

---

## Code Quality (10 checks, max 20)

| Check | Description | Score | Notes |
|---|---|---|---|
| CQ-01 | No getReferenceById | 2 | Zero usages. All lookups use `findById`. |
| CQ-02 | Consistent exception hierarchy | 2 | 4 exceptions: `CodeOpsVaultException` (base) -> `NotFoundException`, `ValidationException`, `AuthorizationException`. Clean hierarchy. |
| CQ-03 | No TODO/FIXME/HACK | 2 | Zero TODO/FIXME/HACK/XXX in source code. |
| CQ-04 | Constants centralized | 2 | `AppConstants.java` centralizes API prefix, pagination, encryption params, rate limit config. |
| CQ-05 | Async exception handling | 2 | `AsyncConfig` implements `AsyncConfigurer` with custom `AsyncUncaughtExceptionHandler`. |
| CQ-06 | RestTemplate injected (not new'd) | 2 | RestTemplate configured as `@Bean` in `RestTemplateConfig`. Injected via constructor. Zero `new RestTemplate()` calls. |
| CQ-07 | Logging present | 2 | 29 Logger/Slf4j declarations across services, security, config classes. Structured logging via logstash-logback-encoder. |
| CQ-08 | No raw exception messages to clients | 2 | Zero `ex.getMessage()` calls in controllers. Error handling exclusively in `GlobalExceptionHandler`. |
| CQ-09 | Doc comments on classes | 0 | 2 / 56 non-DTO/entity/enum classes have Javadoc class-level comments. **BLOCKING ISSUE.** |
| CQ-10 | Doc comments on public methods | 1 | 36 / 200 public methods in service/controller/security classes have Javadoc. ~18% coverage. |

**Code Quality Score: 17 / 20 (85%)**

---

## Test Quality (10 checks, max 20)

| Check | Description | Score | Notes |
|---|---|---|---|
| TST-01 | Unit test files | 2 | 62 unit test files. |
| TST-02 | Integration test files | 0 | 0 integration test files. Testcontainers dependency present but unused. **BLOCKING ISSUE.** |
| TST-03 | Real database in ITs | 0 | No `@Testcontainers` or `PostgreSQLContainer` usage. All tests use H2 in-memory. |
| TST-04 | Source-to-test ratio | 2 | 62 test files / 26 service+controller+security source files = 2.4:1 ratio. |
| TST-05 | Code coverage >= 80% | 1 | Not measured (tests not executed during audit). JaCoCo plugin configured. Scored 1 for configuration presence. |
| TST-06 | Test config exists | 2 | `application-test.yml` and `application-integration.yml` both present. |
| TST-07 | Security tests | 2 | 88 occurrences of `@WithMockUser` / security test patterns across test files. |
| TST-08 | Auth flow e2e | 0 | No integration tests testing end-to-end auth flow. |
| TST-09 | DB state verification in ITs | 0 | No integration tests exist to verify DB state. |
| TST-10 | Total @Test methods | 2 | 628 unit test methods. Comprehensive unit coverage. |

**Test Quality Score: 11 / 20 (55%)**

---

## Infrastructure (6 checks, max 12)

| Check | Description | Score | Notes |
|---|---|---|---|
| INF-01 | Non-root Dockerfile | 2 | Dockerfile creates `appuser` non-root user via `addgroup`/`adduser`, runs app as `USER appuser`. |
| INF-02 | DB ports localhost only | 2 | Docker Compose binds PostgreSQL to `127.0.0.1:5436:5432`. Not exposed to network. |
| INF-03 | Env vars for prod secrets | 2 | 12 `${ENV_VAR}` references in `application-prod.yml`. All sensitive values require env vars. |
| INF-04 | Health check endpoint | 2 | `HealthController` at `/health` returns `{"status":"UP"}` (permitAll). Docker Compose has `pg_isready` healthcheck. |
| INF-05 | Structured logging | 2 | `logstash-logback-encoder 7.4` configured. `logback-spring.xml` with profile-based formatting. |
| INF-06 | CI/CD config | 0 | No GitHub Actions, Jenkinsfile, or GitLab CI detected. **BLOCKING ISSUE.** |

**Infrastructure Score: 10 / 12 (83%)**

---

## Scorecard Summary

```
Category             | Score | Max | %
─────────────────────|───────|─────|──────
Security             |    12 |  20 |  60%
Data Integrity       |    11 |  16 |  69%
API Quality          |    14 |  16 |  88%
Code Quality         |    17 |  20 |  85%
Test Quality         |    11 |  20 |  55%
Infrastructure       |    10 |  12 |  83%
─────────────────────|───────|─────|──────
OVERALL              |    75 | 104 |  72%

Grade: B (70-84%)
```

---

## Blocking Issues (Score = 0)

| Check | Issue | Remediation |
|---|---|---|
| SEC-08 | No SSRF protection on `RotationService` outbound HTTP calls to `externalApiUrl` | Add URL validation: reject private IPs, loopback, link-local addresses before making HTTP calls |
| SEC-09 | No JWT token revocation mechanism | Implement token blacklist (Redis or DB) checked in `JwtAuthFilter`, or switch to short-lived tokens with refresh flow |
| DAT-04 | No `@Version` optimistic locking on entities | Add `@Version Long version` to `BaseEntity` for concurrent update safety |
| CQ-09 | Only 2/56 classes have Javadoc class comments | Add `/** ... */` class-level Javadoc to all non-DTO/entity/enum classes |
| TST-02 | Zero integration tests despite Testcontainers dependency | Write integration tests using `@Testcontainers` + `PostgreSQLContainer` for critical paths |
| INF-06 | No CI/CD pipeline configuration | Add GitHub Actions workflow for build, test, and deploy |

---

## Categories Below 60%

### Test Quality (55%)

**Failing checks:**
- TST-02 (0): No integration tests
- TST-03 (0): No real database testing
- TST-08 (0): No auth flow e2e tests
- TST-09 (0): No DB state verification in integration tests

**Recommendation:** The 628 unit tests provide solid coverage of business logic, but the complete absence of integration tests means the database layer, security filter chain, and end-to-end API flows are untested against real infrastructure. Priority: add Testcontainers-based integration tests for the critical secret CRUD path, seal/unseal flow, and transit encryption operations.

---

## Not Applicable Checks (scored 0 but not real deficiencies)

- **SEC-10** (Password complexity): This service does not manage user passwords. It validates JWT tokens issued by CodeOps-Server.
- **API-08** (File upload validation): No file upload endpoints exist in this service.
