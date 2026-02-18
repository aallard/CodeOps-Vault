package com.codeops.vault.service;

import com.codeops.vault.dto.mapper.PolicyMapper;
import com.codeops.vault.dto.request.CreateBindingRequest;
import com.codeops.vault.dto.request.CreatePolicyRequest;
import com.codeops.vault.dto.request.UpdatePolicyRequest;
import com.codeops.vault.dto.response.AccessPolicyResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.PolicyBindingResponse;
import com.codeops.vault.entity.AccessPolicy;
import com.codeops.vault.entity.PolicyBinding;
import com.codeops.vault.entity.enums.BindingType;
import com.codeops.vault.entity.enums.PolicyPermission;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import com.codeops.vault.repository.AccessPolicyRepository;
import com.codeops.vault.repository.PolicyBindingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PolicyService}.
 *
 * <p>Uses Mockito to mock repositories and PolicyMapper. Covers policy CRUD,
 * binding CRUD, wildcard path matching, deny-overrides-allow evaluation,
 * AccessDecision factories, and statistics.</p>
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private AccessPolicyRepository policyRepository;

    @Mock
    private PolicyBindingRepository bindingRepository;

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PolicyService policyService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();
    private static final UUID BINDING_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    // ═══════════════════════════════════════════
    //  Policy CRUD Tests
    // ═══════════════════════════════════════════

    @Test
    void createPolicy_success_returnsResponse() {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "talent-app-readonly", "Read access for talent app",
                "/services/talent-app/*", List.of(PolicyPermission.READ, PolicyPermission.LIST), false);

        when(policyRepository.existsByTeamIdAndName(TEAM_ID, "talent-app-readonly")).thenReturn(false);
        when(policyRepository.save(any(AccessPolicy.class))).thenAnswer(i -> {
            AccessPolicy p = i.getArgument(0);
            p.setId(POLICY_ID);
            p.setCreatedAt(NOW);
            p.setUpdatedAt(NOW);
            return p;
        });
        AccessPolicyResponse expectedResponse = buildPolicyResponse(false);
        when(policyMapper.toResponse(any(AccessPolicy.class), eq(0))).thenReturn(expectedResponse);

        AccessPolicyResponse result = policyService.createPolicy(request, TEAM_ID, USER_ID);

        assertThat(result).isNotNull();
        verify(policyRepository).save(any(AccessPolicy.class));

        ArgumentCaptor<AccessPolicy> captor = ArgumentCaptor.forClass(AccessPolicy.class);
        verify(policyRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).isEqualTo("READ,LIST");
    }

    @Test
    void createPolicy_duplicateName_throwsValidation() {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "existing-policy", null, "/services/*",
                List.of(PolicyPermission.READ), false);

        when(policyRepository.existsByTeamIdAndName(TEAM_ID, "existing-policy")).thenReturn(true);

        assertThatThrownBy(() -> policyService.createPolicy(request, TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createPolicy_denyPolicy_flagSetCorrectly() {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "deny-sensitive", null, "/admin/*",
                List.of(PolicyPermission.READ, PolicyPermission.WRITE), true);

        when(policyRepository.existsByTeamIdAndName(TEAM_ID, "deny-sensitive")).thenReturn(false);
        when(policyRepository.save(any(AccessPolicy.class))).thenAnswer(i -> {
            AccessPolicy p = i.getArgument(0);
            p.setId(POLICY_ID);
            return p;
        });
        when(policyMapper.toResponse(any(AccessPolicy.class), eq(0)))
                .thenReturn(buildPolicyResponse(true));

        policyService.createPolicy(request, TEAM_ID, USER_ID);

        ArgumentCaptor<AccessPolicy> captor = ArgumentCaptor.forClass(AccessPolicy.class);
        verify(policyRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDenyPolicy()).isTrue();
    }

    @Test
    void getPolicyById_exists_returnsWithBindingCount() {
        AccessPolicy policy = buildPolicy("test-policy", "/services/*", "READ", false);
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(bindingRepository.countByPolicyId(POLICY_ID)).thenReturn(3L);

        AccessPolicyResponse expectedResponse = buildPolicyResponse(false);
        when(policyMapper.toResponse(policy, 3)).thenReturn(expectedResponse);

        AccessPolicyResponse result = policyService.getPolicyById(POLICY_ID);

        assertThat(result).isNotNull();
        verify(bindingRepository).countByPolicyId(POLICY_ID);
    }

    @Test
    void getPolicyById_notFound_throwsNotFound() {
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.getPolicyById(POLICY_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("AccessPolicy");
    }

    @Test
    void listPolicies_activeOnly_excludesInactive() {
        AccessPolicy policy = buildPolicy("active-policy", "/services/*", "READ", false);
        Pageable pageable = PageRequest.of(0, 20);
        Page<AccessPolicy> page = new PageImpl<>(List.of(policy), pageable, 1);

        when(policyRepository.findByTeamIdAndIsActiveTrue(TEAM_ID, pageable)).thenReturn(page);
        when(bindingRepository.countByPolicyId(POLICY_ID)).thenReturn(0L);
        when(policyMapper.toResponse(any(AccessPolicy.class), anyInt()))
                .thenReturn(buildPolicyResponse(false));

        PageResponse<AccessPolicyResponse> result = policyService.listPolicies(TEAM_ID, true, pageable);

        assertThat(result.content()).hasSize(1);
        verify(policyRepository).findByTeamIdAndIsActiveTrue(TEAM_ID, pageable);
    }

    @Test
    void updatePolicy_partialFields_onlyUpdatesProvided() {
        AccessPolicy policy = buildPolicy("original-name", "/services/*", "READ", false);
        policy.setTeamId(TEAM_ID);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(policyRepository.save(any(AccessPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(bindingRepository.countByPolicyId(POLICY_ID)).thenReturn(0L);
        when(policyMapper.toResponse(any(AccessPolicy.class), anyInt()))
                .thenReturn(buildPolicyResponse(false));

        UpdatePolicyRequest request = new UpdatePolicyRequest(
                null, "new description", null, null, null, null);

        policyService.updatePolicy(POLICY_ID, request);

        ArgumentCaptor<AccessPolicy> captor = ArgumentCaptor.forClass(AccessPolicy.class);
        verify(policyRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("original-name");
        assertThat(captor.getValue().getDescription()).isEqualTo("new description");
        assertThat(captor.getValue().getPathPattern()).isEqualTo("/services/*");
    }

    @Test
    void deletePolicy_cascadesBindings() {
        when(policyRepository.existsById(POLICY_ID)).thenReturn(true);

        policyService.deletePolicy(POLICY_ID);

        verify(bindingRepository).deleteByPolicyId(POLICY_ID);
        verify(policyRepository).deleteById(POLICY_ID);
    }

    // ═══════════════════════════════════════════
    //  Binding CRUD Tests
    // ═══════════════════════════════════════════

    @Test
    void createBinding_success_returnsResponse() {
        AccessPolicy policy = buildPolicy("test-policy", "/services/*", "READ", false);
        CreateBindingRequest request = new CreateBindingRequest(POLICY_ID, BindingType.USER, USER_ID);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(bindingRepository.existsByPolicyIdAndBindingTypeAndBindingTargetId(
                POLICY_ID, BindingType.USER, USER_ID)).thenReturn(false);
        when(bindingRepository.save(any(PolicyBinding.class))).thenAnswer(i -> {
            PolicyBinding b = i.getArgument(0);
            b.setId(BINDING_ID);
            b.setCreatedAt(NOW);
            return b;
        });
        PolicyBindingResponse expectedResponse = buildBindingResponse();
        when(policyMapper.toBindingResponse(any(PolicyBinding.class), eq("test-policy")))
                .thenReturn(expectedResponse);

        PolicyBindingResponse result = policyService.createBinding(request, USER_ID);

        assertThat(result).isNotNull();
        verify(bindingRepository).save(any(PolicyBinding.class));
    }

    @Test
    void createBinding_duplicateBinding_throwsValidation() {
        AccessPolicy policy = buildPolicy("test-policy", "/services/*", "READ", false);
        CreateBindingRequest request = new CreateBindingRequest(POLICY_ID, BindingType.USER, USER_ID);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(bindingRepository.existsByPolicyIdAndBindingTypeAndBindingTargetId(
                POLICY_ID, BindingType.USER, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> policyService.createBinding(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createBinding_policyNotFound_throwsNotFound() {
        CreateBindingRequest request = new CreateBindingRequest(POLICY_ID, BindingType.USER, USER_ID);
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.createBinding(request, USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listBindingsForPolicy_returnsAll() {
        AccessPolicy policy = buildPolicy("test-policy", "/services/*", "READ", false);
        PolicyBinding binding = buildBinding(policy, BindingType.USER, USER_ID);

        when(policyRepository.existsById(POLICY_ID)).thenReturn(true);
        when(bindingRepository.findByPolicyId(POLICY_ID)).thenReturn(List.of(binding));
        when(policyMapper.toBindingResponse(any(PolicyBinding.class), anyString()))
                .thenReturn(buildBindingResponse());

        List<PolicyBindingResponse> result = policyService.listBindingsForPolicy(POLICY_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void listBindingsForTarget_returnsMatchingBindings() {
        AccessPolicy policy = buildPolicy("test-policy", "/services/*", "READ", false);
        PolicyBinding binding = buildBinding(policy, BindingType.TEAM, TEAM_ID);

        when(bindingRepository.findByBindingTypeAndBindingTargetId(BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of(binding));
        when(policyMapper.toBindingResponse(any(PolicyBinding.class), anyString()))
                .thenReturn(buildBindingResponse());

        List<PolicyBindingResponse> result = policyService.listBindingsForTarget(
                BindingType.TEAM, TEAM_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void deleteBinding_success() {
        when(bindingRepository.existsById(BINDING_ID)).thenReturn(true);

        policyService.deleteBinding(BINDING_ID);

        verify(bindingRepository).deleteById(BINDING_ID);
    }

    // ═══════════════════════════════════════════
    //  Path Matching Tests
    // ═══════════════════════════════════════════

    @Test
    void matchesPath_exactMatch_returnsTrue() {
        assertThat(policyService.matchesPath("/services/app/db", "/services/app/db")).isTrue();
    }

    @Test
    void matchesPath_exactMismatch_returnsFalse() {
        assertThat(policyService.matchesPath("/services/app/db", "/services/app/cache")).isFalse();
    }

    @Test
    void matchesPath_wildcardLastSegment_matchesSingleLevel() {
        assertThat(policyService.matchesPath("/services/app/password", "/services/app/*")).isTrue();
    }

    @Test
    void matchesPath_wildcardLastSegment_doesNotMatchMultiLevel() {
        assertThat(policyService.matchesPath("/services/app/db/password", "/services/app/*")).isFalse();
    }

    @Test
    void matchesPath_wildcardMiddle_matchesSingleSegment() {
        assertThat(policyService.matchesPath("/services/talent-app/db", "/services/*/db")).isTrue();
    }

    @Test
    void matchesPath_wildcardMiddle_doesNotMatchEmpty() {
        assertThat(policyService.matchesPath("/services/db", "/services/*/db")).isFalse();
    }

    @Test
    void matchesPath_rootWildcard_matchesSingleSegment() {
        assertThat(policyService.matchesPath("/anything", "/*")).isTrue();
    }

    @Test
    void matchesPath_rootWildcard_doesNotMatchDeep() {
        assertThat(policyService.matchesPath("/a/b", "/*")).isFalse();
    }

    @Test
    void matchesPath_trailingSlashHandled() {
        assertThat(policyService.matchesPath("/services/app/", "/services/app/")).isTrue();
    }

    @Test
    void matchesPath_casePreserved() {
        assertThat(policyService.matchesPath("/Services/App", "/services/app")).isFalse();
    }

    @Test
    void matchesPath_emptyPattern_returnsFalse() {
        assertThat(policyService.matchesPath("/services/app", "")).isFalse();
        assertThat(policyService.matchesPath("/services/app", null)).isFalse();
    }

    @Test
    void matchesPath_emptyPath_returnsFalse() {
        assertThat(policyService.matchesPath("", "/services/*")).isFalse();
        assertThat(policyService.matchesPath(null, "/services/*")).isFalse();
    }

    // ═══════════════════════════════════════════
    //  Access Evaluation Tests
    // ═══════════════════════════════════════════

    @Test
    void evaluateAccess_allowPolicyMatches_returnsAllowed() {
        AccessPolicy allowPolicy = buildPolicy("allow-read", "/services/app/*", "READ", false);
        PolicyBinding binding = buildBinding(allowPolicy, BindingType.USER, USER_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of(binding));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app/password", PolicyPermission.READ);

        assertThat(result.allowed()).isTrue();
        assertThat(result.decidingPolicyName()).isEqualTo("allow-read");
    }

    @Test
    void evaluateAccess_denyOverridesAllow_returnsDenied() {
        AccessPolicy allowPolicy = buildPolicy("allow-all", "/services/*", "READ,WRITE", false);
        AccessPolicy denyPolicy = buildPolicy("deny-read", "/services/*", "READ", true);

        PolicyBinding allowBinding = buildBinding(allowPolicy, BindingType.USER, USER_ID);
        PolicyBinding denyBinding = buildBinding(denyPolicy, BindingType.TEAM, TEAM_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of(allowBinding));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of(denyBinding));

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Denied");
        assertThat(result.decidingPolicyName()).isEqualTo("deny-read");
    }

    @Test
    void evaluateAccess_noMatchingPolicy_returnsDefaultDenied() {
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of());
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app/password", PolicyPermission.READ);

        assertThat(result.allowed()).isFalse();
        assertThat(result.decidingPolicyId()).isNull();
        assertThat(result.reason()).contains("No matching allow policy");
    }

    @Test
    void evaluateAccess_allowPolicyWrongPermission_returnsDenied() {
        AccessPolicy allowPolicy = buildPolicy("allow-read", "/services/*", "READ", false);
        PolicyBinding binding = buildBinding(allowPolicy, BindingType.USER, USER_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of(binding));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.WRITE);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void evaluateAccess_teamBinding_appliedToUser() {
        AccessPolicy teamPolicy = buildPolicy("team-read", "/services/*", "READ,LIST", false);
        PolicyBinding teamBinding = buildBinding(teamPolicy, BindingType.TEAM, TEAM_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of());
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of(teamBinding));

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isTrue();
        assertThat(result.decidingPolicyName()).isEqualTo("team-read");
    }

    @Test
    void evaluateAccess_userBinding_onlyAppliesToThatUser() {
        UUID otherUserId = UUID.randomUUID();
        AccessPolicy userPolicy = buildPolicy("user-read", "/services/*", "READ", false);
        PolicyBinding userBinding = buildBinding(userPolicy, BindingType.USER, USER_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, otherUserId))
                .thenReturn(List.of());
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                otherUserId, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void evaluateAccess_inactivePolicy_ignored() {
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of());
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isFalse();
        assertThat(result.decidingPolicyId()).isNull();
    }

    @Test
    void evaluateAccess_multiplePolicies_anyAllowSuffices() {
        AccessPolicy noMatchPolicy = buildPolicy("no-match", "/admin/*", "READ", false);
        AccessPolicy matchPolicy = buildPolicy("match-read", "/services/*", "READ", false);

        PolicyBinding binding1 = buildBinding(noMatchPolicy, BindingType.USER, USER_ID);
        PolicyBinding binding2 = buildBinding(matchPolicy, BindingType.USER, USER_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of(binding1, binding2));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isTrue();
        assertThat(result.decidingPolicyName()).isEqualTo("match-read");
    }

    @Test
    void evaluateAccess_denyPolicy_specificPath_allowsOther() {
        AccessPolicy denyPolicy = buildPolicy("deny-admin", "/admin/*", "READ", true);
        AccessPolicy allowPolicy = buildPolicy("allow-services", "/services/*", "READ", false);

        PolicyBinding denyBinding = buildBinding(denyPolicy, BindingType.USER, USER_ID);
        PolicyBinding allowBinding = buildBinding(allowPolicy, BindingType.USER, USER_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of(denyBinding, allowBinding));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void evaluateAccess_multiplePermissions_checkedIndividually() {
        AccessPolicy allowPolicy = buildPolicy("allow-read-only", "/services/*", "READ", false);
        PolicyBinding binding = buildBinding(allowPolicy, BindingType.USER, USER_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.USER, USER_ID))
                .thenReturn(List.of(binding));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision readResult = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.READ);
        AccessDecision writeResult = policyService.evaluateAccess(
                USER_ID, TEAM_ID, "/services/app", PolicyPermission.WRITE);

        assertThat(readResult.allowed()).isTrue();
        assertThat(writeResult.allowed()).isFalse();
    }

    @Test
    void evaluateServiceAccess_serviceBinding_works() {
        AccessPolicy servicePolicy = buildPolicy("service-read", "/services/*", "READ", false);
        PolicyBinding serviceBinding = buildBinding(servicePolicy, BindingType.SERVICE, SERVICE_ID);

        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.SERVICE, SERVICE_ID))
                .thenReturn(List.of(serviceBinding));
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateServiceAccess(
                SERVICE_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isTrue();
        assertThat(result.decidingPolicyName()).isEqualTo("service-read");
    }

    @Test
    void evaluateServiceAccess_noBinding_defaultDeny() {
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.SERVICE, SERVICE_ID))
                .thenReturn(List.of());
        when(bindingRepository.findActiveBindingsForTarget(TEAM_ID, BindingType.TEAM, TEAM_ID))
                .thenReturn(List.of());

        AccessDecision result = policyService.evaluateServiceAccess(
                SERVICE_ID, TEAM_ID, "/services/app", PolicyPermission.READ);

        assertThat(result.allowed()).isFalse();
        assertThat(result.decidingPolicyId()).isNull();
    }

    // ═══════════════════════════════════════════
    //  AccessDecision Tests
    // ═══════════════════════════════════════════

    @Test
    void allowed_hasCorrectFields() {
        AccessDecision decision = AccessDecision.allowed(POLICY_ID, "my-policy");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).contains("Allowed");
        assertThat(decision.decidingPolicyId()).isEqualTo(POLICY_ID);
        assertThat(decision.decidingPolicyName()).isEqualTo("my-policy");
    }

    @Test
    void denied_hasCorrectFields() {
        AccessDecision decision = AccessDecision.denied(POLICY_ID, "deny-policy");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("Denied");
        assertThat(decision.decidingPolicyId()).isEqualTo(POLICY_ID);
        assertThat(decision.decidingPolicyName()).isEqualTo("deny-policy");
    }

    @Test
    void defaultDenied_hasNullPolicy() {
        AccessDecision decision = AccessDecision.defaultDenied();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decidingPolicyId()).isNull();
        assertThat(decision.decidingPolicyName()).isNull();
        assertThat(decision.reason()).contains("No matching allow policy");
    }

    // ═══════════════════════════════════════════
    //  Statistics Tests
    // ═══════════════════════════════════════════

    @Test
    void getPolicyCounts_correctCounts() {
        AccessPolicy active = buildPolicy("active", "/a/*", "READ", false);
        active.setIsActive(true);
        active.setIsDenyPolicy(false);

        AccessPolicy deny = buildPolicy("deny", "/b/*", "WRITE", true);
        deny.setIsActive(true);
        deny.setIsDenyPolicy(true);

        AccessPolicy inactive = buildPolicy("inactive", "/c/*", "DELETE", false);
        inactive.setIsActive(false);
        inactive.setIsDenyPolicy(false);

        when(policyRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(active, deny, inactive));

        Map<String, Long> counts = policyService.getPolicyCounts(TEAM_ID);

        assertThat(counts.get("total")).isEqualTo(3L);
        assertThat(counts.get("active")).isEqualTo(2L);
        assertThat(counts.get("deny")).isEqualTo(1L);
    }

    @Test
    void getPolicyCounts_emptyTeam_returnsZeros() {
        when(policyRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        Map<String, Long> counts = policyService.getPolicyCounts(TEAM_ID);

        assertThat(counts.get("total")).isEqualTo(0L);
        assertThat(counts.get("active")).isEqualTo(0L);
        assertThat(counts.get("deny")).isEqualTo(0L);
    }

    // ═══════════════════════════════════════════
    //  Test Helpers
    // ═══════════════════════════════════════════

    private AccessPolicy buildPolicy(String name, String pathPattern,
                                      String permissions, boolean isDenyPolicy) {
        AccessPolicy policy = AccessPolicy.builder()
                .teamId(TEAM_ID)
                .name(name)
                .pathPattern(pathPattern)
                .permissions(permissions)
                .isDenyPolicy(isDenyPolicy)
                .isActive(true)
                .createdByUserId(USER_ID)
                .build();
        policy.setId(POLICY_ID);
        policy.setCreatedAt(NOW);
        policy.setUpdatedAt(NOW);
        return policy;
    }

    private PolicyBinding buildBinding(AccessPolicy policy, BindingType type, UUID targetId) {
        PolicyBinding binding = PolicyBinding.builder()
                .policy(policy)
                .bindingType(type)
                .bindingTargetId(targetId)
                .createdByUserId(USER_ID)
                .build();
        binding.setId(BINDING_ID);
        binding.setCreatedAt(NOW);
        return binding;
    }

    private AccessPolicyResponse buildPolicyResponse(boolean isDenyPolicy) {
        return new AccessPolicyResponse(POLICY_ID, TEAM_ID, "test-policy", "desc",
                "/services/*", List.of(PolicyPermission.READ), isDenyPolicy, true,
                USER_ID, 0, NOW, NOW);
    }

    private PolicyBindingResponse buildBindingResponse() {
        return new PolicyBindingResponse(BINDING_ID, POLICY_ID, "test-policy",
                BindingType.USER, USER_ID, USER_ID, NOW);
    }
}
