package com.codeops.vault.controller;

import com.codeops.vault.dto.response.AuditEntryResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.AuditService;
import com.codeops.vault.service.SealService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link AuditController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. Service beans are mocked via {@code @MockBean}.
 * SecurityUtils static methods are mocked via mockStatic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SealService sealService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final UUID TEST_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_RESOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String BASE_PATH = "/api/v1/vault/audit";

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentTeamId).thenReturn(TEST_TEAM_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── Query Endpoint Tests ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void queryAuditLog_withAdmin_returns200() throws Exception {
        AuditEntryResponse entry = new AuditEntryResponse(
                1L, TEST_TEAM_ID, TEST_USER_ID, "WRITE", "/secrets/db/password",
                "SECRET", TEST_RESOURCE_ID, true, null, "127.0.0.1",
                "corr-123", Instant.now());

        PageResponse<AuditEntryResponse> pageResponse = new PageResponse<>(
                List.of(entry), 0, 20, 1, 1, true);

        when(auditService.queryAuditLog(eq(TEST_TEAM_ID), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].operation").value("WRITE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void queryAuditLog_noAdmin_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/query"))
                .andExpect(status().isForbidden());
    }

    @Test
    void queryAuditLog_noAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/query"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Resource Endpoint Tests ─────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditForResource_withAdmin_returns200() throws Exception {
        AuditEntryResponse entry = new AuditEntryResponse(
                2L, TEST_TEAM_ID, TEST_USER_ID, "READ", "/secrets/db/password",
                "SECRET", TEST_RESOURCE_ID, true, null, "127.0.0.1",
                "corr-456", Instant.now());

        PageResponse<AuditEntryResponse> pageResponse = new PageResponse<>(
                List.of(entry), 0, 20, 1, 1, true);

        when(auditService.getAuditForResource(eq(TEST_TEAM_ID), eq("SECRET"), eq(TEST_RESOURCE_ID), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/resource/SECRET/" + TEST_RESOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].resourceType").value("SECRET"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAuditForResource_noAdmin_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/resource/SECRET/" + TEST_RESOURCE_ID))
                .andExpect(status().isForbidden());
    }

    // ─── Stats Endpoint Tests ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditStats_withAdmin_returns200() throws Exception {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("totalEntries", 100L);
        stats.put("failedEntries", 5L);
        stats.put("readOperations", 50L);
        stats.put("writeOperations", 30L);
        stats.put("deleteOperations", 10L);

        when(auditService.getAuditStats(TEST_TEAM_ID)).thenReturn(stats);

        mockMvc.perform(get(BASE_PATH + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(100))
                .andExpect(jsonPath("$.failedEntries").value(5))
                .andExpect(jsonPath("$.readOperations").value(50))
                .andExpect(jsonPath("$.writeOperations").value(30))
                .andExpect(jsonPath("$.deleteOperations").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAuditStats_noAdmin_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/stats"))
                .andExpect(status().isForbidden());
    }
}
