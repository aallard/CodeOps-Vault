package com.codeops.vault.repository;

import com.codeops.vault.entity.AccessPolicy;
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
 * Repository tests for {@link AccessPolicyRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class AccessPolicyRepositoryTest {

    @Autowired
    private AccessPolicyRepository accessPolicyRepository;

    private static final UUID TEAM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accessPolicyRepository.deleteAll();

        accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(TEAM_ID)
                .name("full-access")
                .pathPattern("/services/*")
                .permissions("READ,WRITE,DELETE")
                .createdByUserId(UUID.randomUUID())
                .build());

        accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(TEAM_ID)
                .name("read-only")
                .pathPattern("/services/*")
                .permissions("READ,LIST")
                .createdByUserId(UUID.randomUUID())
                .build());

        accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(TEAM_ID)
                .name("inactive-policy")
                .pathPattern("/old/*")
                .permissions("READ")
                .isActive(false)
                .createdByUserId(UUID.randomUUID())
                .build());
    }

    @Test
    void findByTeamIdAndName_existingPolicy_returnsPolicy() {
        Optional<AccessPolicy> result = accessPolicyRepository.findByTeamIdAndName(TEAM_ID, "full-access");
        assertThat(result).isPresent();
        assertThat(result.get().getPermissions()).isEqualTo("READ,WRITE,DELETE");
    }

    @Test
    void findByTeamIdAndName_nonExistent_returnsEmpty() {
        Optional<AccessPolicy> result = accessPolicyRepository.findByTeamIdAndName(TEAM_ID, "nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void findByTeamId_returnsAllPolicies() {
        List<AccessPolicy> policies = accessPolicyRepository.findByTeamId(TEAM_ID);
        assertThat(policies).hasSize(3);
    }

    @Test
    void findByTeamId_page_returnsPage() {
        Page<AccessPolicy> page = accessPolicyRepository.findByTeamId(TEAM_ID, PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findByTeamIdAndIsActiveTrue_excludesInactive() {
        Page<AccessPolicy> page = accessPolicyRepository.findByTeamIdAndIsActiveTrue(TEAM_ID, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(p -> p.getIsActive());
    }

    @Test
    void findByTeamIdAndIsActiveTrue_list_excludesInactive() {
        List<AccessPolicy> active = accessPolicyRepository.findByTeamIdAndIsActiveTrue(TEAM_ID);
        assertThat(active).hasSize(2);
    }

    @Test
    void existsByTeamIdAndName_existingName_returnsTrue() {
        assertThat(accessPolicyRepository.existsByTeamIdAndName(TEAM_ID, "full-access")).isTrue();
    }

    @Test
    void existsByTeamIdAndName_nonExistent_returnsFalse() {
        assertThat(accessPolicyRepository.existsByTeamIdAndName(TEAM_ID, "nonexistent")).isFalse();
    }

    @Test
    void countByTeamId_returnsCorrectCount() {
        assertThat(accessPolicyRepository.countByTeamId(TEAM_ID)).isEqualTo(3);
    }
}
