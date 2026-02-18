package com.codeops.vault.repository;

import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.SecretMetadata;
import com.codeops.vault.entity.enums.SecretType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link SecretMetadataRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class SecretMetadataRepositoryTest {

    @Autowired
    private SecretMetadataRepository secretMetadataRepository;

    @Autowired
    private SecretRepository secretRepository;

    private Secret secret;

    @BeforeEach
    void setUp() {
        secretMetadataRepository.deleteAll();
        secretRepository.deleteAll();

        secret = secretRepository.save(Secret.builder()
                .teamId(UUID.randomUUID())
                .path("/test/secret")
                .name("Test Secret")
                .secretType(SecretType.STATIC)
                .build());

        secretMetadataRepository.save(SecretMetadata.builder()
                .secret(secret)
                .metadataKey("environment")
                .metadataValue("production")
                .build());

        secretMetadataRepository.save(SecretMetadata.builder()
                .secret(secret)
                .metadataKey("owner")
                .metadataValue("platform-team")
                .build());
    }

    @Test
    void findBySecretId_returnsAllMetadata() {
        List<SecretMetadata> metadata = secretMetadataRepository.findBySecretId(secret.getId());
        assertThat(metadata).hasSize(2);
    }

    @Test
    void findBySecretIdAndMetadataKey_existingKey_returnsMetadata() {
        Optional<SecretMetadata> result = secretMetadataRepository.findBySecretIdAndMetadataKey(secret.getId(), "environment");
        assertThat(result).isPresent();
        assertThat(result.get().getMetadataValue()).isEqualTo("production");
    }

    @Test
    void findBySecretIdAndMetadataKey_nonExistent_returnsEmpty() {
        Optional<SecretMetadata> result = secretMetadataRepository.findBySecretIdAndMetadataKey(secret.getId(), "nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void existsBySecretIdAndMetadataKey_existingKey_returnsTrue() {
        assertThat(secretMetadataRepository.existsBySecretIdAndMetadataKey(secret.getId(), "environment")).isTrue();
    }

    @Test
    void existsBySecretIdAndMetadataKey_nonExistent_returnsFalse() {
        assertThat(secretMetadataRepository.existsBySecretIdAndMetadataKey(secret.getId(), "nonexistent")).isFalse();
    }

    @Test
    @Transactional
    void deleteBySecretIdAndMetadataKey_removesEntry() {
        secretMetadataRepository.deleteBySecretIdAndMetadataKey(secret.getId(), "owner");
        List<SecretMetadata> remaining = secretMetadataRepository.findBySecretId(secret.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getMetadataKey()).isEqualTo("environment");
    }

    @Test
    @Transactional
    void deleteBySecretId_removesAllEntries() {
        secretMetadataRepository.deleteBySecretId(secret.getId());
        List<SecretMetadata> remaining = secretMetadataRepository.findBySecretId(secret.getId());
        assertThat(remaining).isEmpty();
    }
}
