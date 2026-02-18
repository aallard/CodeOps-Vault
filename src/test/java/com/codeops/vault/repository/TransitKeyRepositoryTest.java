package com.codeops.vault.repository;

import com.codeops.vault.entity.TransitKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link TransitKeyRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class TransitKeyRepositoryTest {

    @Autowired
    private TransitKeyRepository transitKeyRepository;

    private static final UUID TEAM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transitKeyRepository.deleteAll();

        transitKeyRepository.save(TransitKey.builder()
                .teamId(TEAM_ID)
                .name("payment-key")
                .keyMaterial("SEED:a2V5MQ==")
                .algorithm("AES-256-GCM")
                .build());

        transitKeyRepository.save(TransitKey.builder()
                .teamId(TEAM_ID)
                .name("data-key")
                .keyMaterial("SEED:a2V5Mg==")
                .algorithm("AES-256-GCM")
                .build());

        transitKeyRepository.save(TransitKey.builder()
                .teamId(TEAM_ID)
                .name("old-key")
                .keyMaterial("SEED:a2V5Mw==")
                .algorithm("AES-256-GCM")
                .isActive(false)
                .build());
    }

    @Test
    void findByTeamIdAndName_existing_returnsKey() {
        Optional<TransitKey> result = transitKeyRepository.findByTeamIdAndName(TEAM_ID, "payment-key");
        assertThat(result).isPresent();
        assertThat(result.get().getAlgorithm()).isEqualTo("AES-256-GCM");
    }

    @Test
    void findByTeamIdAndName_nonExistent_returnsEmpty() {
        Optional<TransitKey> result = transitKeyRepository.findByTeamIdAndName(TEAM_ID, "nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void findByTeamId_returnsAllKeys() {
        List<TransitKey> keys = transitKeyRepository.findByTeamId(TEAM_ID);
        assertThat(keys).hasSize(3);
    }

    @Test
    void findByTeamId_page_returnsPage() {
        Page<TransitKey> page = transitKeyRepository.findByTeamId(TEAM_ID, PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findByTeamIdAndIsActiveTrue_excludesInactive() {
        Page<TransitKey> page = transitKeyRepository.findByTeamIdAndIsActiveTrue(TEAM_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(k -> k.getIsActive());
    }

    @Test
    void existsByTeamIdAndName_existing_returnsTrue() {
        assertThat(transitKeyRepository.existsByTeamIdAndName(TEAM_ID, "payment-key")).isTrue();
    }

    @Test
    void existsByTeamIdAndName_nonExistent_returnsFalse() {
        assertThat(transitKeyRepository.existsByTeamIdAndName(TEAM_ID, "nonexistent")).isFalse();
    }

    @Test
    void countByTeamId_returnsCorrectCount() {
        assertThat(transitKeyRepository.countByTeamId(TEAM_ID)).isEqualTo(3);
    }
}
