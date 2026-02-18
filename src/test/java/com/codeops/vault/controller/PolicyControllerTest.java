package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateBindingRequest;
import com.codeops.vault.dto.request.CreatePolicyRequest;
import com.codeops.vault.dto.request.UpdatePolicyRequest;
import com.codeops.vault.dto.response.AccessPolicyResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.PolicyBindingResponse;
import com.codeops.vault.entity.enums.BindingType;
import com.codeops.vault.entity.enums.PolicyPermission;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.AccessDecision;
import com.codeops.vault.service.PolicyService;
import com.codeops.vault.service.SealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link PolicyController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. Service beans are mocked via {@link MockBean}.
 * SecurityUtils static methods are mocked via mockStatic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PolicyService policyService;

    @MockBean
    private SealService sealService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID TEST_BINDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final String BASE_PATH = "/api/v1/vault/policies";

    private AccessPolicyResponse testPolicyResponse;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentTeamId).thenReturn(TEST_TEAM_ID);

        testPolicyResponse = new AccessPolicyResponse(
                TEST_POLICY_ID, TEST_TEAM_ID, "read-all", "Read access to all secrets",
                "/services/*", List.of(PolicyPermission.READ, PolicyPermission.LIST),
                false, true, TEST_USER_ID, 0, Instant.now(), Instant.now());
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── Policy CRUD Tests ──────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPolicy_valid_returns201() throws Exception {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "read-all", "Read access to all secrets", "/services/*",
                List.of(PolicyPermission.READ, PolicyPermission.LIST), false);

        when(policyService.createPolicy(any(), eq(TEST_TEAM_ID), eq(TEST_USER_ID)))
                .thenReturn(testPolicyResponse);

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEST_POLICY_ID.toString()))
                .andExpect(jsonPath("$.name").value("read-all"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPolicy_invalidBody_returns400() throws Exception {
        // Missing required fields (name, pathPattern, permissions)
        String invalidJson = "{}";

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPolicy_noAuth_returns401() throws Exception {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "read-all", null, "/services/*",
                List.of(PolicyPermission.READ), false);

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPolicy_returns200() throws Exception {
        when(policyService.getPolicyById(TEST_POLICY_ID)).thenReturn(testPolicyResponse);

        mockMvc.perform(get(BASE_PATH + "/{id}", TEST_POLICY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_POLICY_ID.toString()))
                .andExpect(jsonPath("$.name").value("read-all"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listPolicies_returns200() throws Exception {
        PageResponse<AccessPolicyResponse> pageResponse = new PageResponse<>(
                List.of(testPolicyResponse), 0, 20, 1, 1, true);

        when(policyService.listPolicies(eq(TEST_TEAM_ID), eq(true), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("read-all"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePolicy_returns200() throws Exception {
        UpdatePolicyRequest request = new UpdatePolicyRequest(
                "updated-name", null, null, null, null, null);

        when(policyService.updatePolicy(eq(TEST_POLICY_ID), any()))
                .thenReturn(testPolicyResponse);

        mockMvc.perform(put(BASE_PATH + "/{id}", TEST_POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletePolicy_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{id}", TEST_POLICY_ID))
                .andExpect(status().isNoContent());

        verify(policyService).deletePolicy(TEST_POLICY_ID);
    }

    // ─── Binding Tests ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBinding_valid_returns201() throws Exception {
        CreateBindingRequest request = new CreateBindingRequest(
                TEST_POLICY_ID, BindingType.USER, TEST_USER_ID);

        PolicyBindingResponse bindingResponse = new PolicyBindingResponse(
                TEST_BINDING_ID, TEST_POLICY_ID, "read-all",
                BindingType.USER, TEST_USER_ID, TEST_USER_ID, Instant.now());

        when(policyService.createBinding(any(), eq(TEST_USER_ID)))
                .thenReturn(bindingResponse);

        mockMvc.perform(post(BASE_PATH + "/bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEST_BINDING_ID.toString()))
                .andExpect(jsonPath("$.policyName").value("read-all"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listBindingsForPolicy_returns200() throws Exception {
        PolicyBindingResponse bindingResponse = new PolicyBindingResponse(
                TEST_BINDING_ID, TEST_POLICY_ID, "read-all",
                BindingType.USER, TEST_USER_ID, TEST_USER_ID, Instant.now());

        when(policyService.listBindingsForPolicy(TEST_POLICY_ID))
                .thenReturn(List.of(bindingResponse));

        mockMvc.perform(get(BASE_PATH + "/{id}/bindings", TEST_POLICY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].policyName").value("read-all"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listBindingsForTarget_returns200() throws Exception {
        PolicyBindingResponse bindingResponse = new PolicyBindingResponse(
                TEST_BINDING_ID, TEST_POLICY_ID, "read-all",
                BindingType.USER, TEST_USER_ID, TEST_USER_ID, Instant.now());

        when(policyService.listBindingsForTarget(BindingType.USER, TEST_USER_ID))
                .thenReturn(List.of(bindingResponse));

        mockMvc.perform(get(BASE_PATH + "/bindings/target")
                        .param("type", "USER")
                        .param("targetId", TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bindingType").value("USER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteBinding_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/bindings/{id}", TEST_BINDING_ID))
                .andExpect(status().isNoContent());

        verify(policyService).deleteBinding(TEST_BINDING_ID);
    }

    // ─── Evaluation Tests ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void evaluateAccess_returnsDecision() throws Exception {
        AccessDecision decision = AccessDecision.allowed(TEST_POLICY_ID, "read-all");

        when(policyService.evaluateAccess(eq(TEST_USER_ID), eq(TEST_TEAM_ID),
                eq("/services/app/db"), eq(PolicyPermission.READ)))
                .thenReturn(decision);

        mockMvc.perform(post(BASE_PATH + "/evaluate")
                        .param("userId", TEST_USER_ID.toString())
                        .param("path", "/services/app/db")
                        .param("permission", "READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.decidingPolicyName").value("read-all"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void evaluateServiceAccess_returnsDecision() throws Exception {
        UUID serviceId = UUID.randomUUID();
        AccessDecision decision = AccessDecision.defaultDenied();

        when(policyService.evaluateServiceAccess(eq(serviceId), eq(TEST_TEAM_ID),
                eq("/services/app/db"), eq(PolicyPermission.WRITE)))
                .thenReturn(decision);

        mockMvc.perform(post(BASE_PATH + "/evaluate/service")
                        .param("serviceId", serviceId.toString())
                        .param("path", "/services/app/db")
                        .param("permission", "WRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    // ─── Security & Statistics Tests ────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void noAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}", TEST_POLICY_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_returns200() throws Exception {
        when(policyService.getPolicyCounts(TEST_TEAM_ID))
                .thenReturn(Map.of("total", 5L, "active", 4L, "deny", 1L));

        mockMvc.perform(get(BASE_PATH + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.active").value(4));
    }
}
