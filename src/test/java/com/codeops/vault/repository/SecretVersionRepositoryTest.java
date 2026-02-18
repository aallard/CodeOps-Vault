package com.codeops.vault.repository;

import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.SecretVersion;
import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link SecretVersionRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class SecretVersionRepositoryTest {

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private SecretVersionRepository secretVersionRepository;

    private Secret secret;

    @BeforeEach
    void setUp() {
        secretVersionRepository.deleteAll();
        secretRepository.deleteAll();

        secret = secretRepository.save(Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/test/secret")
                .name("Test Secret")
                .secretType(SecretType.STATIC)
                .currentVersion(3)
                .build());

        secretVersionRepository.save(SecretVersion.builder()
                .secret(secret)
                .versionNumber(1)
                .encryptedValue("SEED:djE=")
                .build());

        secretVersionRepository.save(SecretVersion.builder()
                .secret(secret)
                .versionNumber(2)
                .encryptedValue("SEED:djI=")
                .build());

        SecretVersion destroyed = SecretVersion.builder()
                .secret(secret)
                .versionNumber(3)
                .encryptedValue("SEED:djM=")
                .isDestroyed(true)
                .build();
        secretVersionRepository.save(destroyed);
    }

    @Test
    void findBySecretIdOrderByVersionNumberDesc_ordersCorrectly() {
        List<SecretVersion> versions = secretVersionRepository.findBySecretIdOrderByVersionNumberDesc(secret.getId());
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(3);
        assertThat(versions.get(1).getVersionNumber()).isEqualTo(2);
        assertThat(versions.get(2).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void findBySecretIdAndVersionNumber_existingVersion_returnsVersion() {
        Optional<SecretVersion> result = secretVersionRepository.findBySecretIdAndVersionNumber(secret.getId(), 2);
        assertThat(result).isPresent();
        assertThat(result.get().getEncryptedValue()).isEqualTo("SEED:djI=");
    }

    @Test
    void findBySecretIdAndVersionNumber_nonExistent_returnsEmpty() {
        Optional<SecretVersion> result = secretVersionRepository.findBySecretIdAndVersionNumber(secret.getId(), 99);
        assertThat(result).isEmpty();
    }

    @Test
    void findTopBySecretIdOrderByVersionNumberDesc_returnsLatest() {
        Optional<SecretVersion> result = secretVersionRepository.findTopBySecretIdOrderByVersionNumberDesc(secret.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getVersionNumber()).isEqualTo(3);
    }

    @Test
    void countBySecretId_returnsCorrectCount() {
        assertThat(secretVersionRepository.countBySecretId(secret.getId())).isEqualTo(3);
    }

    @Test
    void findBySecretIdAndVersionNumberLessThan_findsOlderVersions() {
        List<SecretVersion> older = secretVersionRepository.findBySecretIdAndVersionNumberLessThan(secret.getId(), 3);
        assertThat(older).hasSize(2);
    }

    @Test
    void findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc_excludesDestroyed() {
        List<SecretVersion> nonDestroyed = secretVersionRepository
                .findBySecretIdAndIsDestroyedFalseOrderByVersionNumberDesc(secret.getId());
        assertThat(nonDestroyed).hasSize(2);
        assertThat(nonDestroyed.get(0).getVersionNumber()).isEqualTo(2);
        assertThat(nonDestroyed.get(1).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void findBySecretId_page_returnsPage() {
        var page = secretVersionRepository.findBySecretId(secret.getId(), org.springframework.data.domain.PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
