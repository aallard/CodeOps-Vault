package com.codeops.vault.repository;

import com.codeops.vault.entity.RotationHistory;
import com.codeops.vault.entity.enums.RotationStrategy;
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
 * Repository tests for {@link RotationHistoryRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class RotationHistoryRepositoryTest {

    @Autowired
    private RotationHistoryRepository rotationHistoryRepository;

    private static final UUID SECRET_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rotationHistoryRepository.deleteAll();

        rotationHistoryRepository.save(RotationHistory.builder()
                .secretId(SECRET_ID)
                .secretPath("/services/app/db/password")
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .previousVersion(1)
                .newVersion(2)
                .success(true)
                .durationMs(150L)
                .build());

        rotationHistoryRepository.save(RotationHistory.builder()
                .secretId(SECRET_ID)
                .secretPath("/services/app/db/password")
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .previousVersion(2)
                .success(false)
                .errorMessage("Connection timeout")
                .durationMs(5000L)
                .build());

        rotationHistoryRepository.save(RotationHistory.builder()
                .secretId(SECRET_ID)
                .secretPath("/services/app/db/password")
                .strategy(RotationStrategy.RANDOM_GENERATE)
                .previousVersion(2)
                .newVersion(3)
                .success(true)
                .durationMs(200L)
                .build());
    }

    @Test
    void findBySecretId_page_returnsAll() {
        Page<RotationHistory> page = rotationHistoryRepository.findBySecretId(SECRET_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(3);
    }

    @Test
    void findBySecretIdOrderByCreatedAtDesc_ordersCorrectly() {
        List<RotationHistory> history = rotationHistoryRepository.findBySecretIdOrderByCreatedAtDesc(SECRET_ID);
        assertThat(history).hasSize(3);
    }

    @Test
    void findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc_returnsLatestSuccess() {
        Optional<RotationHistory> result = rotationHistoryRepository
                .findTopBySecretIdAndSuccessTrueOrderByCreatedAtDesc(SECRET_ID);
        assertThat(result).isPresent();
        assertThat(result.get().getSuccess()).isTrue();
    }

    @Test
    void countBySecretId_returnsCorrectCount() {
        assertThat(rotationHistoryRepository.countBySecretId(SECRET_ID)).isEqualTo(3);
    }

    @Test
    void countBySecretIdAndSuccessFalse_countsFailures() {
        assertThat(rotationHistoryRepository.countBySecretIdAndSuccessFalse(SECRET_ID)).isEqualTo(1);
    }

    @Test
    void findBySecretId_nonExistent_returnsEmpty() {
        Page<RotationHistory> page = rotationHistoryRepository.findBySecretId(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }
}
