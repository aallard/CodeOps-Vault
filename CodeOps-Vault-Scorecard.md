# CodeOps-Vault — Quality Scorecard

**Audit Date:** 2026-02-20T23:34:23Z
**Branch:** main
**Commit:** eb634db8
**Auditor:** Claude Code (Automated)

---

## Security (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| SEC-01 Auth on all mutation endpoints | 2 | 6 controllers use class-level @PreAuthorize("hasRole('ADMIN')"); SealController has 4 method-level; all 67 endpoints protected |
| SEC-02 No hardcoded secrets in source | 2 | 0 hardcoded secrets found. Dev JWT secret and master key are profile-gated (dev only) |
| SEC-03 Input validation on all request DTOs | 2 | 48 validation annotations across 16 request DTOs. All string fields constrained |
| SEC-04 CORS not using wildcards | 2 | No wildcard origins. Configured via property with explicit origins |
| SEC-05 Encryption key not hardcoded | 2 | JWT secret and master key from env vars in prod. Dev defaults are profile-gated |
| SEC-06 Security headers configured | 2 | CSP, X-Frame-Options DENY, X-Content-Type-Options, HSTS (4 header configs) |
| SEC-07 Rate limiting present | 2 | RateLimitFilter: 100 req/min per IP on /api/v1/vault/** |
| SEC-08 SSRF protection on outbound URLs | 0 | **No SSRF protection on external API URLs.** RotationService calls user-provided URLs via RestTemplate without validation |
| SEC-09 Token revocation / logout | 0 | **No JWT token revocation or blacklist.** Stateless JWT only — tokens valid until expiry. (Lease revocation exists but is unrelated) |
| SEC-10 Password complexity enforcement | 0 | N/A — Vault service does not manage passwords (authenticates via JWT from CodeOps-Server) |

**Security Score: 14 / 20 (70%)**

---

## Data Integrity (8 checks, max 16)

| Check | Score | Notes |
|---|---|---|
| DAT-01 All enum fields use @Enumerated(STRING) | 2 | 5 @Enumerated(STRING) annotations. All enum fields properly mapped |
| DAT-02 Database indexes on FK columns | 2 | 24 @Index annotations covering all FK and query columns across 10 entities |
| DAT-03 Nullable constraints on required fields | 2 | 52 nullable=false constraints on entity fields |
| DAT-04 Optimistic locking (@Version) | 0 | **No @Version on any entity.** Concurrent updates could overwrite data |
| DAT-05 No unbounded queries | 1 | 18 unbounded List queries in repositories; most scoped by teamId/secretId but no hard size limit |
| DAT-06 No in-memory filtering of DB results | 1 | 3 instances of in-memory stream filtering in services/mappers |
| DAT-07 Proper relationship mapping | 2 | JPA relationships used for SecretVersion/Metadata/PolicyBinding/RotationPolicy. split(",") in PolicyMapper is intentional CSV→enum list conversion, not comma-separated IDs |
| DAT-08 Audit timestamps on entities | 2 | BaseEntity with @PrePersist/@PreUpdate across 10 entities. AuditEntry has own @PrePersist |

**Data Integrity Score: 12 / 16 (75%)**

---

## API Quality (8 checks, max 16)

| Check | Score | Notes |
|---|---|---|
| API-01 Consistent error responses | 2 | GlobalExceptionHandler with 8 exception handlers. Structured ErrorResponse(message, timestamp) |
| API-02 Error messages sanitized | 1 | ex.getMessage() exposed for business exceptions (NotFoundException, ValidationException, AuthorizationException) but 500s masked with generic message |
| API-03 Audit logging on mutations | 2 | AuditService integrated into all services. Every mutation operation logged via logSuccess/logFailure |
| API-04 Pagination on list endpoints | 2 | 15 Pageable/Page references in controllers; list endpoints use PageResponse wrapper |
| API-05 Correct HTTP status codes | 2 | 201 for creates, 204 for deletes, 200 for updates/reads. Consistent across all controllers |
| API-06 OpenAPI / Swagger documented | 2 | springdoc-openapi-starter-webmvc-ui 2.5.0 with OpenApiConfig and @Operation annotations |
| API-07 Consistent DTO naming | 2 | 31 files following *Request.java/*Response.java convention |
| API-08 File upload validation | 0 | N/A — no file upload endpoints |

**API Quality Score: 13 / 16 (81%)**

---

## Code Quality (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| CQ-01 No getReferenceById | 2 | 0 uses. All lookups use findById with proper Optional handling |
| CQ-02 Consistent exception hierarchy | 2 | 4 exceptions: CodeOpsVaultException (base) → NotFoundException, ValidationException, AuthorizationException |
| CQ-03 No TODO/FIXME/HACK | 2 | 0 found in source |
| CQ-04 Constants centralized | 2 | AppConstants with pagination, secret limits, rate limiting, encryption constants, HKDF info |
| CQ-05 Async exception handling | 2 | AsyncConfig with AsyncUncaughtExceptionHandler (5 references) |
| CQ-06 RestTemplate injected (not new'd) | 2 | 0 instances of `new RestTemplate()`. Bean injection via RestTemplateConfig |
| CQ-07 Logging in services/security | 2 | 29 logger declarations (@Slf4j/LoggerFactory) across services, controllers, security, config |
| CQ-08 No raw exception messages to clients | 2 | 0 uses of ex.getMessage() in controllers (all in GlobalExceptionHandler) |
| CQ-09 Doc comments on classes | 2 | All non-DTO, non-enum, non-entity classes have comprehensive Javadoc |
| CQ-10 Doc comments on public methods | 2 | All public methods in services, controllers, and security classes have Javadoc |

**Code Quality Score: 20 / 20 (100%)**

---

## Test Quality (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| TST-01 Unit test files | 2 | 62 unit test files covering all services, controllers, security, config, entities, repositories, DTOs, mappers |
| TST-02 Integration test files | 0 | **0 integration test files** |
| TST-03 Real database in ITs | 0 | **Testcontainers configured in pom.xml but 0 integration tests use it** |
| TST-04 Source-to-test ratio | 2 | 62 unit tests / 24 source files (controllers + services + security) = 2.58:1 |
| TST-05 Code coverage >= 80% | 1 | Coverage report not generated (app not running). 628 @Test methods suggest good coverage |
| TST-06 Test config exists | 0 | **No application-test.yml in src/test/resources.** Config only in src/main/resources |
| TST-07 Security tests | 2 | 88 security test annotations (@WithMockUser, @WithAnonymousUser, Bearer) |
| TST-08 Auth flow e2e | 0 | **No integration tests for auth flow** (0 IT files) |
| TST-09 DB state verification in ITs | 0 | **No integration tests with DB verification** (0 IT @Test methods) |
| TST-10 Total @Test methods | 2 | 628 unit + 0 integration = 628 total |

**Test Quality Score: 9 / 20 (45%)**

---

## Infrastructure (6 checks, max 12)

| Check | Score | Notes |
|---|---|---|
| INF-01 Non-root Dockerfile | 2 | Dockerfile creates appuser:appgroup, runs as non-root |
| INF-02 DB ports localhost only | 2 | docker-compose binds to 127.0.0.1:5436:5432 |
| INF-03 Env vars for prod secrets | 2 | 12 ${...} references in application-prod.yml |
| INF-04 Health check endpoint | 2 | /health (public, returns JSON status) + /api/v1/vault/seal/status |
| INF-05 Structured logging | 2 | LogstashEncoder in logback-spring.xml for prod profile |
| INF-06 CI/CD config | 0 | **No CI/CD pipeline detected** |

**Infrastructure Score: 10 / 12 (83%)**

---

## Scorecard Summary

| Category | Score | Max | % |
|---|---|---|---|
| Security | 14 | 20 | 70% |
| Data Integrity | 12 | 16 | 75% |
| API Quality | 13 | 16 | 81% |
| Code Quality | 20 | 20 | 100% |
| Test Quality | 9 | 20 | 45% |
| Infrastructure | 10 | 12 | 83% |
| **OVERALL** | **78** | **104** | **75%** |

**Grade: B (70-84%)**

---

## Categories Below 60%

### Test Quality (45%) — Failing Checks

- **TST-02** (0): No integration test files exist
- **TST-03** (0): Testcontainers configured but unused — no ITs execute against real DB
- **TST-06** (0): No test-specific config in `src/test/resources/`
- **TST-08** (0): **BLOCKING** — No end-to-end auth flow tests
- **TST-09** (0): **BLOCKING** — No database state verification in integration tests

### Blocking Issues (Score = 0)

| Check | Category | Impact | Remediation |
|---|---|---|---|
| SEC-08 | Security | External API URLs in RotationService accept arbitrary user input → SSRF risk | Add URL validation (block private/loopback ranges) in RotationService.callExternalApi |
| SEC-09 | Security | JWT tokens cannot be revoked before expiry | Acceptable for development; add Redis-backed blacklist for production |
| DAT-04 | Data Integrity | No optimistic locking → lost updates under concurrent writes | Add `@Version private Long version` to BaseEntity |
| TST-08 | Test Quality | No integration tests verify auth flow end-to-end | Add IT extending BaseIntegrationTest with real JWT + DB |
| TST-09 | Test Quality | No integration tests verify database state after operations | Add IT with repository assertions |
| INF-06 | Infrastructure | No CI/CD pipeline | Add GitHub Actions workflow for build + test + lint |
