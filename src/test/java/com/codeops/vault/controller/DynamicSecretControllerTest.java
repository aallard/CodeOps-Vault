package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateDynamicLeaseRequest;
import com.codeops.vault.dto.response.DynamicLeaseResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.entity.enums.LeaseStatus;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.DynamicSecretService;
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
 * Controller tests for {@link DynamicSecretController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. Service beans are mocked via {@code @MockBean}.
 * SecurityUtils static methods are mocked via mockStatic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DynamicSecretControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DynamicSecretService dynamicSecretService;

    @MockBean
    private SealService sealService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_SECRET_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String TEST_LEASE_ID = "lease-00000000-0000-0000-0000-000000000007";
    private static final String BASE_PATH = "/api/v1/vault/dynamic";

    private DynamicLeaseResponse testLeaseResponse;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentTeamId).thenReturn(TEST_TEAM_ID);

        testLeaseResponse = new DynamicLeaseResponse(
                UUID.randomUUID(), TEST_LEASE_ID, TEST_SECRET_ID,
                "/services/app/db", "postgresql", LeaseStatus.ACTIVE,
                3600, Instant.now().plusSeconds(3600), null,
                TEST_USER_ID, Map.of("host", "localhost", "port", 5432),
                Instant.now());
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── Lease CRUD Tests ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createLease_valid_returns201() throws Exception {
        CreateDynamicLeaseRequest request = new CreateDynamicLeaseRequest(TEST_SECRET_ID, 3600);

        when(dynamicSecretService.createLease(any(), eq(TEST_USER_ID)))
                .thenReturn(testLeaseResponse);

        mockMvc.perform(post(BASE_PATH + "/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaseId").value(TEST_LEASE_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createLease_invalid_returns400() throws Exception {
        // TTL below minimum (60)
        String invalidJson = "{\"secretId\":\"" + TEST_SECRET_ID + "\",\"ttlSeconds\":10}";

        mockMvc.perform(post(BASE_PATH + "/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLease_exists_returns200() throws Exception {
        when(dynamicSecretService.getLease(TEST_LEASE_ID)).thenReturn(testLeaseResponse);

        mockMvc.perform(get(BASE_PATH + "/leases/{leaseId}", TEST_LEASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaseId").value(TEST_LEASE_ID));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listLeases_returnsPaginated() throws Exception {
        PageResponse<DynamicLeaseResponse> pageResponse = new PageResponse<>(
                List.of(testLeaseResponse), 0, 20, 1, 1, true);

        when(dynamicSecretService.listLeases(eq(TEST_SECRET_ID), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/leases")
                        .param("secretId", TEST_SECRET_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].leaseId").value(TEST_LEASE_ID));
    }

    // ─── Revocation Tests ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void revokeLease_returns204() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/leases/{leaseId}/revoke", TEST_LEASE_ID))
                .andExpect(status().isNoContent());

        verify(dynamicSecretService).revokeLease(TEST_LEASE_ID, TEST_USER_ID);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void revokeAllLeases_returnsCount() throws Exception {
        when(dynamicSecretService.revokeAllLeases(TEST_SECRET_ID, TEST_USER_ID)).thenReturn(3);

        mockMvc.perform(post(BASE_PATH + "/leases/revoke-all")
                        .param("secretId", TEST_SECRET_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(3));
    }

    // ─── Statistics Tests ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLeaseStats_returns200() throws Exception {
        when(dynamicSecretService.getLeaseStats(TEST_SECRET_ID))
                .thenReturn(Map.of("active", 5L, "expired", 10L, "revoked", 2L));

        mockMvc.perform(get(BASE_PATH + "/stats")
                        .param("secretId", TEST_SECRET_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(5));
    }

    // ─── Security Tests ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void noAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/leases/{leaseId}", TEST_LEASE_ID))
                .andExpect(status().isForbidden());
    }
}
