package com.codeops.vault.config;

import com.codeops.vault.entity.*;
import com.codeops.vault.entity.enums.BindingType;
import com.codeops.vault.entity.enums.RotationStrategy;
import com.codeops.vault.entity.enums.SecretType;
import com.codeops.vault.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Database seeder for the Vault service (dev profile only).
 *
 * <p>Seeds sample secrets, access policies, policy bindings, a rotation policy,
 * and a transit key for development testing. Idempotent — checks if secrets
 * already exist before seeding.</p>
 *
 * <p>Encrypted values use a {@code SEED:} prefix with Base64-encoded plaintext.
 * The EncryptionService (CV-004) will detect and re-encrypt these on first run.</p>
 */
@Component
@Slf4j
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final UUID DEV_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final AccessPolicyRepository accessPolicyRepository;
    private final PolicyBindingRepository policyBindingRepository;
    private final RotationPolicyRepository rotationPolicyRepository;
    private final TransitKeyRepository transitKeyRepository;

    /**
     * Seeds development data on application startup.
     *
     * @param args command-line arguments (unused)
     */
    @Override
    public void run(String... args) {
        if (secretRepository.countByTeamId(DEV_TEAM_ID) > 0) {
            log.info("DataSeeder: data already exists, skipping");
            return;
        }

        log.info("DataSeeder: seeding development data...");

        seedSecrets();
        seedAccessPolicies();
        seedRotationPolicy();
        seedTransitKey();

        log.info("DataSeeder: seeding complete");
    }

    /**
     * Seeds 3 secrets with versions.
     */
    private void seedSecrets() {
        // Secret 1: talent-app db password (2 versions)
        Secret dbPassword = secretRepository.save(Secret.builder()
                .teamId(DEV_TEAM_ID)
                .path("/services/talent-app/db/password")
                .name("Talent App DB Password")
                .description("PostgreSQL password for the Talent App service")
                .secretType(SecretType.STATIC)
                .currentVersion(2)
                .ownerUserId(DEV_USER_ID)
                .build());

        secretVersionRepository.save(SecretVersion.builder()
                .secret(dbPassword)
                .versionNumber(1)
                .encryptedValue(seedValue("oldpassword123"))
                .changeDescription("Initial password")
                .createdByUserId(DEV_USER_ID)
                .build());

        secretVersionRepository.save(SecretVersion.builder()
                .secret(dbPassword)
                .versionNumber(2)
                .encryptedValue(seedValue("password123"))
                .changeDescription("Rotated password")
                .createdByUserId(DEV_USER_ID)
                .build());

        // Secret 2: talent-app JWT secret (1 version)
        Secret jwtSecret = secretRepository.save(Secret.builder()
                .teamId(DEV_TEAM_ID)
                .path("/services/talent-app/api/jwt-secret")
                .name("Talent App JWT Secret")
                .description("HMAC-SHA256 signing key for Talent App JWTs")
                .secretType(SecretType.STATIC)
                .currentVersion(1)
                .ownerUserId(DEV_USER_ID)
                .build());

        secretVersionRepository.save(SecretVersion.builder()
                .secret(jwtSecret)
                .versionNumber(1)
                .encryptedValue(seedValue("super-secret-jwt-key-for-talent-app"))
                .changeDescription("Initial secret")
                .createdByUserId(DEV_USER_ID)
                .build());

        // Secret 3: client-portal db password (1 version)
        Secret clientPortalDb = secretRepository.save(Secret.builder()
                .teamId(DEV_TEAM_ID)
                .path("/services/client-portal/db/password")
                .name("Client Portal DB Password")
                .description("PostgreSQL password for the Client Portal service")
                .secretType(SecretType.STATIC)
                .currentVersion(1)
                .ownerUserId(DEV_USER_ID)
                .build());

        secretVersionRepository.save(SecretVersion.builder()
                .secret(clientPortalDb)
                .versionNumber(1)
                .encryptedValue(seedValue("clientdbpass456"))
                .changeDescription("Initial password")
                .createdByUserId(DEV_USER_ID)
                .build());

        log.info("DataSeeder: seeded 3 secrets with 4 versions");
    }

    /**
     * Seeds 2 access policies with 3 bindings.
     */
    private void seedAccessPolicies() {
        // Policy 1: talent-app-full-access
        AccessPolicy talentAppPolicy = accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(DEV_TEAM_ID)
                .name("talent-app-full-access")
                .description("Full access to Talent App secrets")
                .pathPattern("/services/talent-app/*")
                .permissions("READ,WRITE,ROTATE")
                .createdByUserId(DEV_USER_ID)
                .build());

        // Policy 2: global-read-only
        AccessPolicy globalReadOnly = accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(DEV_TEAM_ID)
                .name("global-read-only")
                .description("Read-only access to all service secrets")
                .pathPattern("/services/*")
                .permissions("READ,LIST")
                .createdByUserId(DEV_USER_ID)
                .build());

        // Bind talent-app-full-access to the dev team
        policyBindingRepository.save(PolicyBinding.builder()
                .policy(talentAppPolicy)
                .bindingType(BindingType.TEAM)
                .bindingTargetId(DEV_TEAM_ID)
                .createdByUserId(DEV_USER_ID)
                .build());

        // Bind global-read-only to the dev team
        policyBindingRepository.save(PolicyBinding.builder()
                .policy(globalReadOnly)
                .bindingType(BindingType.TEAM)
                .bindingTargetId(DEV_TEAM_ID)
                .createdByUserId(DEV_USER_ID)
                .build());

        // Bind talent-app-full-access to the dev user
        policyBindingRepository.save(PolicyBinding.builder()
                .policy(talentAppPolicy)
                .bindingType(BindingType.USER)
                .bindingTargetId(DEV_USER_ID)
                .createdByUserId(DEV_USER_ID)
                .build());

        log.info("DataSeeder: seeded 2 policies with 3 bindings");
    }

    /**
     * Seeds a rotation policy on the talent-app db password secret.
     */
    private void seedRotationPolicy() {
        Secret dbPassword = secretRepository.findByTeamIdAndPath(DEV_TEAM_ID, "/services/talent-app/db/password")
                .orElseThrow();

        Instant now = Instant.now();
        rotationPolicyRepository.save(RotationPolicy.builder()
                .secret(dbPassword)
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .rotationIntervalHours(720)
                .randomLength(32)
                .randomCharset("alphanumeric")
                .lastRotatedAt(now)
                .nextRotationAt(now.plus(720, ChronoUnit.HOURS))
                .build());

        log.info("DataSeeder: seeded 1 rotation policy");
    }

    /**
     * Seeds a transit encryption key.
     */
    private void seedTransitKey() {
        transitKeyRepository.save(TransitKey.builder()
                .teamId(DEV_TEAM_ID)
                .name("payment-data-key")
                .description("AES-256-GCM key for encrypting payment-related data")
                .currentVersion(1)
                .minDecryptionVersion(1)
                .keyMaterial(seedValue("transit-key-material-placeholder"))
                .algorithm("AES-256-GCM")
                .createdByUserId(DEV_USER_ID)
                .build());

        log.info("DataSeeder: seeded 1 transit key");
    }

    /**
     * Creates a seed-prefixed Base64-encoded value for development.
     *
     * <p>The EncryptionService (CV-004) will detect the {@code SEED:} prefix
     * and re-encrypt these values on first run.</p>
     *
     * @param plaintext the plaintext value to encode
     * @return the seed-encoded value
     */
    private static String seedValue(String plaintext) {
        return "SEED:" + Base64.getEncoder().encodeToString(plaintext.getBytes());
    }
}
