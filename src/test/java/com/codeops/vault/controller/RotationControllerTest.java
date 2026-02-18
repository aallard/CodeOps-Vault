package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateRotationPolicyRequest;
import com.codeops.vault.dto.request.UpdateRotationPolicyRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.RotationHistoryResponse;
import com.codeops.vault.dto.response.RotationPolicyResponse;
import com.codeops.vault.entity.enums.RotationStrategy;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.RotationService;
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
 * Controller tests for {@link RotationController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. Service beans are mocked via {@code @MockBean}.
 * SecurityUtils static methods are mocked via mockStatic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RotationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RotationService rotationService;

    @MockBean
    private SealService sealService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_SECRET_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID TEST_POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final String BASE_PATH = "/api/v1/vault/rotation";

    private RotationPolicyResponse testPolicyResponse;
    private RotationHistoryResponse testHistoryResponse;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentTeamId).thenReturn(TEST_TEAM_ID);

        testPolicyResponse = new RotationPolicyResponse(
                TEST_POLICY_ID, TEST_SECRET_ID, "/services/app/db",
                RotationStrategy.RANDOM_GENERATE, 24, 32, "alphanumeric",
                null, true, 0, 5, null,
                Instant.now().plusSeconds(86400), Instant.now(), Instant.now());

        testHistoryResponse = new RotationHistoryResponse(
                UUID.randomUUID(), TEST_SECRET_ID, "/services/app/db",
                RotationStrategy.RANDOM_GENERATE, 1, 2, true,
                null, 150L, TEST_USER_ID, Instant.now());
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── Policy CRUD Tests ──────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createOrUpdatePolicy_valid_returns201() throws Exception {
        CreateRotationPolicyRequest request = new CreateRotationPolicyRequest(
                TEST_SECRET_ID, RotationStrategy.RANDOM_GENERATE, 24,
                32, "alphanumeric", null, null, null, 5);

        when(rotationService.createOrUpdatePolicy(any(), eq(TEST_USER_ID)))
                .thenReturn(testPolicyResponse);

        mockMvc.perform(post(BASE_PATH + "/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEST_POLICY_ID.toString()))
                .andExpect(jsonPath("$.strategy").value("RANDOM_GENERATE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPolicy_exists_returns200() throws Exception {
        when(rotationService.getPolicy(TEST_SECRET_ID)).thenReturn(testPolicyResponse);

        mockMvc.perform(get(BASE_PATH + "/policies/{secretId}", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretId").value(TEST_SECRET_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePolicy_returns200() throws Exception {
        UpdateRotationPolicyRequest request = new UpdateRotationPolicyRequest(
                null, 48, null, null, null, null, null, null, null);

        when(rotationService.updatePolicy(eq(TEST_POLICY_ID), any()))
                .thenReturn(testPolicyResponse);

        mockMvc.perform(put(BASE_PATH + "/policies/{policyId}", TEST_POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletePolicy_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/policies/{policyId}", TEST_POLICY_ID))
                .andExpect(status().isNoContent());

        verify(rotationService).deletePolicy(TEST_POLICY_ID);
    }

    // ─── Rotation Execution Tests ───────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void rotateSecret_returns200() throws Exception {
        when(rotationService.rotateSecret(TEST_SECRET_ID, TEST_USER_ID))
                .thenReturn(testHistoryResponse);

        mockMvc.perform(post(BASE_PATH + "/rotate/{secretId}", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.previousVersion").value(1))
                .andExpect(jsonPath("$.newVersion").value(2));
    }

    // ─── History Tests ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getHistory_returnsPaginated() throws Exception {
        PageResponse<RotationHistoryResponse> pageResponse = new PageResponse<>(
                List.of(testHistoryResponse), 0, 20, 1, 1, true);

        when(rotationService.getRotationHistory(eq(TEST_SECRET_ID), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/history/{secretId}", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLastSuccessful_found_returns200() throws Exception {
        when(rotationService.getLastSuccessfulRotation(TEST_SECRET_ID))
                .thenReturn(testHistoryResponse);

        mockMvc.perform(get(BASE_PATH + "/history/{secretId}/last", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLastSuccessful_notFound_returns404() throws Exception {
        when(rotationService.getLastSuccessfulRotation(TEST_SECRET_ID))
                .thenReturn(null);

        mockMvc.perform(get(BASE_PATH + "/history/{secretId}/last", TEST_SECRET_ID))
                .andExpect(status().isNotFound());
    }

    // ─── Security Tests ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void noAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/policies/{secretId}", TEST_SECRET_ID))
                .andExpect(status().isForbidden());
    }

    // ─── Statistics Tests ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStats_returns200() throws Exception {
        when(rotationService.getRotationStats(TEST_SECRET_ID))
                .thenReturn(Map.of("activePolicies", 1L, "totalRotations", 10L, "failedRotations", 2L));

        mockMvc.perform(get(BASE_PATH + "/stats/{secretId}", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRotations").value(10));
    }
}
