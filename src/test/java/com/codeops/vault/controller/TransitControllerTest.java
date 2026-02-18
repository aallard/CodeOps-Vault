package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateTransitKeyRequest;
import com.codeops.vault.dto.request.TransitDecryptRequest;
import com.codeops.vault.dto.request.TransitEncryptRequest;
import com.codeops.vault.dto.request.TransitRewrapRequest;
import com.codeops.vault.dto.request.UpdateTransitKeyRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.TransitDecryptResponse;
import com.codeops.vault.dto.response.TransitEncryptResponse;
import com.codeops.vault.dto.response.TransitKeyResponse;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.SealService;
import com.codeops.vault.service.TransitService;
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
 * Controller tests for {@link TransitController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. Service beans are mocked via {@code @MockBean}.
 * SecurityUtils static methods are mocked via mockStatic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransitService transitService;

    @MockBean
    private SealService sealService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEST_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TEST_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final String BASE_PATH = "/api/v1/vault/transit";

    private TransitKeyResponse testKeyResponse;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(TEST_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentTeamId).thenReturn(TEST_TEAM_ID);

        testKeyResponse = new TransitKeyResponse(
                TEST_KEY_ID, TEST_TEAM_ID, "my-key", "Test transit key",
                1, 1, "AES-256-GCM", false, false, true,
                TEST_USER_ID, Instant.now(), Instant.now());
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── Key Management Tests ───────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createKey_valid_returns201() throws Exception {
        CreateTransitKeyRequest request = new CreateTransitKeyRequest(
                "my-key", "Test transit key", "AES-256-GCM", false, false);

        when(transitService.createKey(any(), eq(TEST_TEAM_ID), eq(TEST_USER_ID)))
                .thenReturn(testKeyResponse);

        mockMvc.perform(post(BASE_PATH + "/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEST_KEY_ID.toString()))
                .andExpect(jsonPath("$.name").value("my-key"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getKeyById_returns200() throws Exception {
        when(transitService.getKeyById(TEST_KEY_ID)).thenReturn(testKeyResponse);

        mockMvc.perform(get(BASE_PATH + "/keys/{id}", TEST_KEY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("my-key"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getKeyByName_returns200() throws Exception {
        when(transitService.getKeyByName(TEST_TEAM_ID, "my-key")).thenReturn(testKeyResponse);

        mockMvc.perform(get(BASE_PATH + "/keys/by-name")
                        .param("name", "my-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("my-key"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listKeys_returnsPaginated() throws Exception {
        PageResponse<TransitKeyResponse> pageResponse = new PageResponse<>(
                List.of(testKeyResponse), 0, 20, 1, 1, true);

        when(transitService.listKeys(eq(TEST_TEAM_ID), eq(true), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_PATH + "/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("my-key"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateKey_returns200() throws Exception {
        UpdateTransitKeyRequest request = new UpdateTransitKeyRequest(
                "Updated description", null, null, null, null);

        when(transitService.updateKey(eq(TEST_KEY_ID), any())).thenReturn(testKeyResponse);

        mockMvc.perform(put(BASE_PATH + "/keys/{id}", TEST_KEY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rotateKey_returns200() throws Exception {
        TransitKeyResponse rotatedResponse = new TransitKeyResponse(
                TEST_KEY_ID, TEST_TEAM_ID, "my-key", "Test transit key",
                2, 1, "AES-256-GCM", false, false, true,
                TEST_USER_ID, Instant.now(), Instant.now());

        when(transitService.rotateKey(TEST_KEY_ID)).thenReturn(rotatedResponse);

        mockMvc.perform(post(BASE_PATH + "/keys/{id}/rotate", TEST_KEY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteKey_returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/keys/{id}", TEST_KEY_ID))
                .andExpect(status().isNoContent());

        verify(transitService).deleteKey(TEST_KEY_ID);
    }

    // ─── Encrypt / Decrypt Tests ────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void encrypt_valid_returns200() throws Exception {
        TransitEncryptRequest request = new TransitEncryptRequest("my-key", "SGVsbG8gV29ybGQ=");
        TransitEncryptResponse encryptResponse = new TransitEncryptResponse("my-key", 1, "encrypted-data");

        when(transitService.encrypt(any(), eq(TEST_TEAM_ID))).thenReturn(encryptResponse);

        mockMvc.perform(post(BASE_PATH + "/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciphertext").value("encrypted-data"))
                .andExpect(jsonPath("$.keyVersion").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void decrypt_valid_returns200() throws Exception {
        TransitDecryptRequest request = new TransitDecryptRequest("my-key", "encrypted-data");
        TransitDecryptResponse decryptResponse = new TransitDecryptResponse("my-key", "SGVsbG8gV29ybGQ=");

        when(transitService.decrypt(any(), eq(TEST_TEAM_ID))).thenReturn(decryptResponse);

        mockMvc.perform(post(BASE_PATH + "/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintext").value("SGVsbG8gV29ybGQ="));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rewrap_valid_returns200() throws Exception {
        TransitRewrapRequest request = new TransitRewrapRequest("my-key", "old-encrypted-data");
        TransitEncryptResponse rewrapResponse = new TransitEncryptResponse("my-key", 2, "new-encrypted-data");

        when(transitService.rewrap(any(), eq(TEST_TEAM_ID))).thenReturn(rewrapResponse);

        mockMvc.perform(post(BASE_PATH + "/rewrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyVersion").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateDataKey_returns200() throws Exception {
        when(transitService.generateDataKey("my-key", TEST_TEAM_ID))
                .thenReturn(Map.of("plaintextKey", "base64-key", "ciphertextKey", "wrapped-key"));

        mockMvc.perform(post(BASE_PATH + "/datakey")
                        .param("keyName", "my-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintextKey").value("base64-key"))
                .andExpect(jsonPath("$.ciphertextKey").value("wrapped-key"));
    }

    // ─── Security Tests ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void noAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/keys/{id}", TEST_KEY_ID))
                .andExpect(status().isForbidden());
    }

    // ─── Statistics Tests ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getKeyStats_returns200() throws Exception {
        when(transitService.getKeyStats(TEST_TEAM_ID))
                .thenReturn(Map.of("total", 5L, "active", 4L));

        mockMvc.perform(get(BASE_PATH + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.active").value(4));
    }
}
