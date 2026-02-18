package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateSecretRequest;
import com.codeops.vault.dto.request.UpdateSecretRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.SecretResponse;
import com.codeops.vault.dto.response.SecretValueResponse;
import com.codeops.vault.dto.response.SecretVersionResponse;
import com.codeops.vault.entity.enums.SecretType;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.SealService;
import com.codeops.vault.service.SecretService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link SecretController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. Service beans are mocked via {@link MockBean}.
 * SecurityUtils static methods are mocked via mockStatic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecretControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SecretService secretService;

    @MockBean
    private SealService sealService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_SECRET_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String BASE_PATH = "/api/v1/vault/secrets";

    private SecretResponse testSecretResponse;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentTeamId).thenReturn(TEST_TEAM_ID);

        testSecretResponse = new SecretResponse(
                TEST_SECRET_ID, TEST_TEAM_ID, "/services/app/db", "db-password",
                "Database password", SecretType.STATIC, 1, 10, 30,
                null, null, null, TEST_USER_ID, null, true,
                Map.of("env", "prod"), Instant.now(), Instant.now());
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── CRUD Endpoint Tests ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSecret_valid_returns201() throws Exception {
        CreateSecretRequest request = new CreateSecretRequest(
                "/services/app/db", "db-password", "super-secret",
                "Database password", SecretType.STATIC, null, 10, 30, null, null);

        when(secretService.createSecret(any(), eq(TEST_TEAM_ID), eq(TEST_USER_ID)))
                .thenReturn(testSecretResponse);

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEST_SECRET_ID.toString()))
                .andExpect(jsonPath("$.name").value("db-password"))
                .andExpect(jsonPath("$.path").value("/services/app/db"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSecret_invalidBody_returns400() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSecret_noAuth_returns401() throws Exception {
        CreateSecretRequest request = new CreateSecretRequest(
                "/services/app/db", "db-password", "secret",
                null, SecretType.STATIC, null, null, null, null, null);

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSecret_exists_returns200() throws Exception {
        when(secretService.getSecretById(TEST_SECRET_ID)).thenReturn(testSecretResponse);

        mockMvc.perform(get(BASE_PATH + "/{id}", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_SECRET_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSecret_notFound_returns404() throws Exception {
        when(secretService.getSecretById(TEST_SECRET_ID))
                .thenThrow(new NotFoundException("Secret", TEST_SECRET_ID));

        mockMvc.perform(get(BASE_PATH + "/{id}", TEST_SECRET_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSecretByPath_exists_returns200() throws Exception {
        when(secretService.getSecretByPath(TEST_TEAM_ID, "/services/app/db"))
                .thenReturn(testSecretResponse);

        mockMvc.perform(get(BASE_PATH + "/by-path")
                        .param("path", "/services/app/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/services/app/db"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void readSecretValue_returns200() throws Exception {
        SecretValueResponse valueResponse = new SecretValueResponse(
                TEST_SECRET_ID, "/services/app/db", "db-password",
                1, "super-secret", Instant.now());

        when(secretService.readSecretValue(TEST_SECRET_ID)).thenReturn(valueResponse);

        mockMvc.perform(get(BASE_PATH + "/{id}/value", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("super-secret"))
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSecret_valid_returns200() throws Exception {
        UpdateSecretRequest request = new UpdateSecretRequest(
                "new-value", "Updated password", null, null, null, null, null);

        when(secretService.updateSecret(eq(TEST_SECRET_ID), any(), eq(TEST_USER_ID)))
                .thenReturn(testSecretResponse);

        mockMvc.perform(put(BASE_PATH + "/{id}", TEST_SECRET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_SECRET_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void softDeleteSecret_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{id}", TEST_SECRET_ID))
                .andExpect(status().isNoContent());

        verify(secretService).softDeleteSecret(TEST_SECRET_ID);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void hardDeleteSecret_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{id}/permanent", TEST_SECRET_ID))
                .andExpect(status().isNoContent());

        verify(secretService).hardDeleteSecret(TEST_SECRET_ID);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listSecrets_returns200() throws Exception {
        PageResponse<SecretResponse> pageResponse = new PageResponse<>(
                List.of(testSecretResponse), 0, 20, 1, 1, true);

        when(secretService.listSecrets(eq(TEST_TEAM_ID), isNull(), isNull(), eq(true), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(TEST_SECRET_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchSecrets_returns200() throws Exception {
        PageResponse<SecretResponse> pageResponse = new PageResponse<>(
                List.of(testSecretResponse), 0, 20, 1, 1, true);

        when(secretService.searchSecrets(eq(TEST_TEAM_ID), eq("password"), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/search")
                        .param("q", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ─── Version Endpoint Tests ─────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void listVersions_returns200() throws Exception {
        SecretVersionResponse versionResponse = new SecretVersionResponse(
                UUID.randomUUID(), TEST_SECRET_ID, 1, "master-v1",
                null, TEST_USER_ID, false, Instant.now());

        PageResponse<SecretVersionResponse> pageResponse = new PageResponse<>(
                List.of(versionResponse), 0, 20, 1, 1, true);

        when(secretService.listVersions(eq(TEST_SECRET_ID), any())).thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/{id}/versions", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].versionNumber").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getVersion_returns200() throws Exception {
        SecretVersionResponse versionResponse = new SecretVersionResponse(
                UUID.randomUUID(), TEST_SECRET_ID, 2, "master-v1",
                "Updated value", TEST_USER_ID, false, Instant.now());

        when(secretService.getVersion(TEST_SECRET_ID, 2)).thenReturn(versionResponse);

        mockMvc.perform(get(BASE_PATH + "/{id}/versions/{version}", TEST_SECRET_ID, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void readVersionValue_returns200() throws Exception {
        SecretValueResponse valueResponse = new SecretValueResponse(
                TEST_SECRET_ID, "/services/app/db", "db-password",
                2, "old-value", Instant.now());

        when(secretService.readSecretVersionValue(TEST_SECRET_ID, 2)).thenReturn(valueResponse);

        mockMvc.perform(get(BASE_PATH + "/{id}/versions/{version}/value", TEST_SECRET_ID, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.value").value("old-value"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void destroyVersion_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{id}/versions/{version}", TEST_SECRET_ID, 1))
                .andExpect(status().isNoContent());

        verify(secretService).destroyVersion(TEST_SECRET_ID, 1);
    }

    // ─── Metadata Endpoint Tests ────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMetadata_returns200() throws Exception {
        when(secretService.getMetadata(TEST_SECRET_ID))
                .thenReturn(Map.of("env", "prod", "team", "backend"));

        mockMvc.perform(get(BASE_PATH + "/{id}/metadata", TEST_SECRET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.env").value("prod"))
                .andExpect(jsonPath("$.team").value("backend"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setMetadata_returns204() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{id}/metadata/{key}", TEST_SECRET_ID, "env")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("staging"))
                .andExpect(status().isNoContent());

        verify(secretService).setMetadata(eq(TEST_SECRET_ID), eq("env"), eq("staging"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void removeMetadata_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{id}/metadata/{key}", TEST_SECRET_ID, "env"))
                .andExpect(status().isNoContent());

        verify(secretService).removeMetadata(TEST_SECRET_ID, "env");
    }

    // ─── Statistics Endpoint Tests ──────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStats_returns200() throws Exception {
        when(secretService.getSecretCounts(TEST_TEAM_ID))
                .thenReturn(Map.of("total", 10L, "static", 5L, "dynamic", 3L, "reference", 2L));

        mockMvc.perform(get(BASE_PATH + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpiringSecrets_returns200() throws Exception {
        when(secretService.getExpiringSecrets(TEST_TEAM_ID, 24))
                .thenReturn(List.of(testSecretResponse));

        mockMvc.perform(get(BASE_PATH + "/expiring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(TEST_SECRET_ID.toString()));
    }

    // ─── Security Tests ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void noAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}", TEST_SECRET_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void paginationDefaults_applied() throws Exception {
        PageResponse<SecretResponse> pageResponse = new PageResponse<>(
                Collections.emptyList(), 0, 20, 0, 0, true);

        when(secretService.listSecrets(eq(TEST_TEAM_ID), isNull(), isNull(), eq(true), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }
}
