package com.codeops.vault.repository;

import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link SecretRepository} using H2 in-memory database.
 */
@DataJpaTest
@ActiveProfiles("test")
class SecretRepositoryTest {

    @Autowired
    private SecretRepository secretRepository;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID OTHER_TEAM_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        secretRepository.deleteAll();

        secretRepository.save(Secret.builder()
                .teamId(TEAM_ID)
                .path("/services/app-a/db/password")
                .name("App A DB Password")
                .secretType(SecretType.STATIC)
                .ownerUserId(OWNER_ID)
                .build());

        secretRepository.save(Secret.builder()
                .teamId(TEAM_ID)
                .path("/services/app-a/api/key")
                .name("App A API Key")
                .secretType(SecretType.STATIC)
                .ownerUserId(OWNER_ID)
                .build());

        secretRepository.save(Secret.builder()
                .teamId(TEAM_ID)
                .path("/services/app-b/db/creds")
                .name("App B DB Creds")
                .secretType(SecretType.DYNAMIC)
                .ownerUserId(OWNER_ID)
                .build());

        Secret inactive = Secret.builder()
                .teamId(TEAM_ID)
                .path("/services/old/secret")
                .name("Old Secret")
                .secretType(SecretType.STATIC)
                .isActive(false)
                .ownerUserId(OWNER_ID)
                .build();
        secretRepository.save(inactive);

        secretRepository.save(Secret.builder()
                .teamId(OTHER_TEAM_ID)
                .path("/services/other/key")
                .name("Other Team Key")
                .secretType(SecretType.REFERENCE)
                .ownerUserId(OWNER_ID)
                .build());
    }

    @Test
    void findByTeamIdAndPath_existingPath_returnsSecret() {
        Optional<Secret> result = secretRepository.findByTeamIdAndPath(TEAM_ID, "/services/app-a/db/password");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("App A DB Password");
    }

    @Test
    void findByTeamIdAndPath_nonExistentPath_returnsEmpty() {
        Optional<Secret> result = secretRepository.findByTeamIdAndPath(TEAM_ID, "/services/nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void findByTeamId_returnsList() {
        List<Secret> secrets = secretRepository.findByTeamId(TEAM_ID);
        assertThat(secrets).hasSize(4);
    }

    @Test
    void findByTeamId_page_returnsPage() {
        Page<Secret> page = secretRepository.findByTeamId(TEAM_ID, PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void findByTeamIdAndSecretType_filtersCorrectly() {
        Page<Secret> page = secretRepository.findByTeamIdAndSecretType(TEAM_ID, SecretType.DYNAMIC, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("App B DB Creds");
    }

    @Test
    void findByTeamIdAndPathStartingWith_matchesPrefix() {
        Page<Secret> page = secretRepository.findByTeamIdAndPathStartingWith(TEAM_ID, "/services/app-a", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByTeamIdAndIsActiveTrue_excludesInactive() {
        Page<Secret> page = secretRepository.findByTeamIdAndIsActiveTrue(TEAM_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).allMatch(s -> s.getIsActive());
    }

    @Test
    void findByTeamIdAndNameContainingIgnoreCase_matchesSubstring() {
        Page<Secret> page = secretRepository.findByTeamIdAndNameContainingIgnoreCase(TEAM_ID, "db", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByTeamIdAndPathStartingWithAndIsActiveTrue_combinesFilters() {
        List<Secret> results = secretRepository.findByTeamIdAndPathStartingWithAndIsActiveTrue(TEAM_ID, "/services/app-a");
        assertThat(results).hasSize(2);
    }

    @Test
    void existsByTeamIdAndPath_existingSecret_returnsTrue() {
        assertThat(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/services/app-a/db/password")).isTrue();
    }

    @Test
    void existsByTeamIdAndPath_nonExistent_returnsFalse() {
        assertThat(secretRepository.existsByTeamIdAndPath(TEAM_ID, "/services/nonexistent")).isFalse();
    }

    @Test
    void countByTeamId_returnsCorrectCount() {
        assertThat(secretRepository.countByTeamId(TEAM_ID)).isEqualTo(4);
        assertThat(secretRepository.countByTeamId(OTHER_TEAM_ID)).isEqualTo(1);
    }

    @Test
    void countByTeamIdAndSecretType_countsCorrectly() {
        assertThat(secretRepository.countByTeamIdAndSecretType(TEAM_ID, SecretType.STATIC)).isEqualTo(3);
        assertThat(secretRepository.countByTeamIdAndSecretType(TEAM_ID, SecretType.DYNAMIC)).isEqualTo(1);
    }

    @Test
    void findByExpiresAtBeforeAndIsActiveTrue_findsExpiredSecrets() {
        Secret expiring = secretRepository.findByTeamIdAndPath(TEAM_ID, "/services/app-a/db/password").orElseThrow();
        expiring.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        secretRepository.save(expiring);

        List<Secret> expired = secretRepository.findByExpiresAtBeforeAndIsActiveTrue(Instant.now());
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getPath()).isEqualTo("/services/app-a/db/password");
    }
}
