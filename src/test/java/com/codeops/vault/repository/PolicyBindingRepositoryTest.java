package com.codeops.vault.repository;

import com.codeops.vault.entity.AccessPolicy;
import com.codeops.vault.entity.PolicyBinding;
import com.codeops.vault.entity.enums.BindingType;
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
 * Repository tests for {@link PolicyBindingRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
class PolicyBindingRepositoryTest {

    @Autowired
    private PolicyBindingRepository policyBindingRepository;

    @Autowired
    private AccessPolicyRepository accessPolicyRepository;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID TARGET_TEAM_ID = UUID.randomUUID();
    private AccessPolicy activePolicy;
    private AccessPolicy inactivePolicy;

    @BeforeEach
    void setUp() {
        policyBindingRepository.deleteAll();
        accessPolicyRepository.deleteAll();

        activePolicy = accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(TEAM_ID)
                .name("active-policy")
                .pathPattern("/services/*")
                .permissions("READ,WRITE")
                .createdByUserId(UUID.randomUUID())
                .build());

        inactivePolicy = accessPolicyRepository.save(AccessPolicy.builder()
                .teamId(TEAM_ID)
                .name("inactive-policy")
                .pathPattern("/old/*")
                .permissions("READ")
                .isActive(false)
                .createdByUserId(UUID.randomUUID())
                .build());

        policyBindingRepository.save(PolicyBinding.builder()
                .policy(activePolicy)
                .bindingType(BindingType.USER)
                .bindingTargetId(TARGET_USER_ID)
                .createdByUserId(UUID.randomUUID())
                .build());

        policyBindingRepository.save(PolicyBinding.builder()
                .policy(activePolicy)
                .bindingType(BindingType.TEAM)
                .bindingTargetId(TARGET_TEAM_ID)
                .createdByUserId(UUID.randomUUID())
                .build());

        policyBindingRepository.save(PolicyBinding.builder()
                .policy(inactivePolicy)
                .bindingType(BindingType.USER)
                .bindingTargetId(TARGET_USER_ID)
                .createdByUserId(UUID.randomUUID())
                .build());
    }

    @Test
    void findByPolicyId_returnsBindings() {
        List<PolicyBinding> bindings = policyBindingRepository.findByPolicyId(activePolicy.getId());
        assertThat(bindings).hasSize(2);
    }

    @Test
    void findByBindingTypeAndBindingTargetId_returnsMatching() {
        List<PolicyBinding> bindings = policyBindingRepository
                .findByBindingTypeAndBindingTargetId(BindingType.USER, TARGET_USER_ID);
        assertThat(bindings).hasSize(2);
    }

    @Test
    void findByPolicyIdAndBindingTypeAndBindingTargetId_existing_returnsBinding() {
        Optional<PolicyBinding> result = policyBindingRepository
                .findByPolicyIdAndBindingTypeAndBindingTargetId(activePolicy.getId(), BindingType.USER, TARGET_USER_ID);
        assertThat(result).isPresent();
    }

    @Test
    void findByPolicyIdAndBindingTypeAndBindingTargetId_nonExistent_returnsEmpty() {
        Optional<PolicyBinding> result = policyBindingRepository
                .findByPolicyIdAndBindingTypeAndBindingTargetId(activePolicy.getId(), BindingType.SERVICE, UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void existsByPolicyIdAndBindingTypeAndBindingTargetId_existing_returnsTrue() {
        assertThat(policyBindingRepository.existsByPolicyIdAndBindingTypeAndBindingTargetId(
                activePolicy.getId(), BindingType.TEAM, TARGET_TEAM_ID)).isTrue();
    }

    @Test
    void existsByPolicyIdAndBindingTypeAndBindingTargetId_nonExistent_returnsFalse() {
        assertThat(policyBindingRepository.existsByPolicyIdAndBindingTypeAndBindingTargetId(
                activePolicy.getId(), BindingType.SERVICE, UUID.randomUUID())).isFalse();
    }

    @Test
    void countByPolicyId_returnsCorrectCount() {
        assertThat(policyBindingRepository.countByPolicyId(activePolicy.getId())).isEqualTo(2);
        assertThat(policyBindingRepository.countByPolicyId(inactivePolicy.getId())).isEqualTo(1);
    }

    @Test
    void findActiveBindingsForTarget_returnsOnlyActiveBindings() {
        List<PolicyBinding> active = policyBindingRepository
                .findActiveBindingsForTarget(TEAM_ID, BindingType.USER, TARGET_USER_ID);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getPolicy().getName()).isEqualTo("active-policy");
    }
}
