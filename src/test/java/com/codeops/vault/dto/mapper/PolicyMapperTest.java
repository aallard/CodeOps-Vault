package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.AccessPolicyResponse;
import com.codeops.vault.dto.response.PolicyBindingResponse;
import com.codeops.vault.entity.AccessPolicy;
import com.codeops.vault.entity.PolicyBinding;
import com.codeops.vault.entity.enums.BindingType;
import com.codeops.vault.entity.enums.PolicyPermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PolicyMapper} MapStruct implementation.
 */
@SpringBootTest
@ActiveProfiles("test")
class PolicyMapperTest {

    @Autowired
    private PolicyMapper policyMapper;

    @Test
    void toResponse_mapsAllFieldsWithPermissionsParsing() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID createdByUserId = UUID.randomUUID();
        Instant now = Instant.now();

        AccessPolicy policy = AccessPolicy.builder()
                .teamId(teamId)
                .name("readonly-policy")
                .description("Read-only access")
                .pathPattern("/services/app/*")
                .permissions("READ,LIST")
                .isDenyPolicy(false)
                .isActive(true)
                .createdByUserId(createdByUserId)
                .build();
        policy.setId(id);
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);

        AccessPolicyResponse response = policyMapper.toResponse(policy, 5);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.name()).isEqualTo("readonly-policy");
        assertThat(response.description()).isEqualTo("Read-only access");
        assertThat(response.pathPattern()).isEqualTo("/services/app/*");
        assertThat(response.permissions()).containsExactly(PolicyPermission.READ, PolicyPermission.LIST);
        assertThat(response.isDenyPolicy()).isFalse();
        assertThat(response.isActive()).isTrue();
        assertThat(response.createdByUserId()).isEqualTo(createdByUserId);
        assertThat(response.bindingCount()).isEqualTo(5);
    }

    @Test
    void toResponse_allPermissions_parsedCorrectly() {
        AccessPolicy policy = AccessPolicy.builder()
                .teamId(UUID.randomUUID())
                .name("admin")
                .pathPattern("/**")
                .permissions("READ,WRITE,DELETE,LIST,ROTATE")
                .isDenyPolicy(false)
                .isActive(true)
                .build();
        policy.setId(UUID.randomUUID());
        policy.setCreatedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());

        AccessPolicyResponse response = policyMapper.toResponse(policy, 0);

        assertThat(response.permissions()).containsExactly(
                PolicyPermission.READ, PolicyPermission.WRITE, PolicyPermission.DELETE,
                PolicyPermission.LIST, PolicyPermission.ROTATE);
    }

    @Test
    void toResponse_emptyPermissions_returnsEmptyList() {
        AccessPolicy policy = AccessPolicy.builder()
                .teamId(UUID.randomUUID())
                .name("empty")
                .pathPattern("/*")
                .permissions("")
                .isDenyPolicy(false)
                .isActive(true)
                .build();
        policy.setId(UUID.randomUUID());
        policy.setCreatedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());

        AccessPolicyResponse response = policyMapper.toResponse(policy, 0);

        assertThat(response.permissions()).isEmpty();
    }

    @Test
    void toBindingResponse_mapsAllFields() {
        UUID bindingId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID createdByUserId = UUID.randomUUID();
        Instant now = Instant.now();

        AccessPolicy policy = AccessPolicy.builder().build();
        policy.setId(policyId);

        PolicyBinding binding = PolicyBinding.builder()
                .policy(policy)
                .bindingType(BindingType.USER)
                .bindingTargetId(targetId)
                .createdByUserId(createdByUserId)
                .build();
        binding.setId(bindingId);
        binding.setCreatedAt(now);

        PolicyBindingResponse response = policyMapper.toBindingResponse(binding, "my-policy");

        assertThat(response.id()).isEqualTo(bindingId);
        assertThat(response.policyId()).isEqualTo(policyId);
        assertThat(response.policyName()).isEqualTo("my-policy");
        assertThat(response.bindingType()).isEqualTo(BindingType.USER);
        assertThat(response.bindingTargetId()).isEqualTo(targetId);
        assertThat(response.createdByUserId()).isEqualTo(createdByUserId);
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void permissionsStringToList_nullInput_returnsEmptyList() {
        List<PolicyPermission> result = policyMapper.permissionsStringToList(null);
        assertThat(result).isEmpty();
    }
}
