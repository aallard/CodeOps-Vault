package com.codeops.vault.controller;

import com.codeops.vault.dto.request.SealActionRequest;
import com.codeops.vault.dto.response.SealStatusResponse;
import com.codeops.vault.entity.enums.SealStatus;
import com.codeops.vault.service.SealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link SealController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} for full
 * context testing. SealService is mocked via {@code @MockBean}.</p>
 *
 * <p>Note: SecurityUtils is NOT mocked here because SealController
 * endpoints do not use SecurityUtils. Authentication is handled entirely
 * by {@code @PreAuthorize} and {@code @WithMockUser}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SealService sealService;

    private static final String BASE_PATH = "/api/v1/vault/seal";

    private SealStatusResponse unsealedStatus;
    private SealStatusResponse sealedStatus;

    @BeforeEach
    void setUp() {
        unsealedStatus = new SealStatusResponse(
                SealStatus.UNSEALED, 5, 3, 0, true,
                Instant.now().minusSeconds(3600), Instant.now());

        sealedStatus = new SealStatusResponse(
                SealStatus.SEALED, 5, 3, 0, false,
                Instant.now(), null);
    }

    // ─── Public Status Endpoint ─────────────────────────────

    @Test
    void getStatus_noAuth_returns200() throws Exception {
        when(sealService.getStatus()).thenReturn(unsealedStatus);

        mockMvc.perform(get(BASE_PATH + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSEALED"))
                .andExpect(jsonPath("$.totalShares").value(5))
                .andExpect(jsonPath("$.threshold").value(3));
    }

    // ─── Seal / Unseal Tests ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void seal_withAdmin_returns200() throws Exception {
        when(sealService.getStatus()).thenReturn(sealedStatus);

        mockMvc.perform(post(BASE_PATH + "/seal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEALED"));

        verify(sealService).seal();
    }

    @Test
    @WithMockUser(roles = "USER")
    void seal_noAdmin_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/seal"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unseal_withAdmin_returns200() throws Exception {
        SealStatusResponse unsealingStatus = new SealStatusResponse(
                SealStatus.UNSEALING, 5, 3, 1, false,
                Instant.now(), null);

        when(sealService.submitKeyShare("test-key-share")).thenReturn(unsealingStatus);

        SealActionRequest request = new SealActionRequest("unseal", "test-key-share");

        mockMvc.perform(post(BASE_PATH + "/unseal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSEALING"))
                .andExpect(jsonPath("$.sharesProvided").value(1));
    }

    // ─── Generate Shares Tests ──────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateShares_withAdmin_returns200() throws Exception {
        String[] shares = new String[]{"share1", "share2", "share3", "share4", "share5"};
        when(sealService.generateKeyShares()).thenReturn(shares);
        when(sealService.getStatus()).thenReturn(unsealedStatus);

        mockMvc.perform(post(BASE_PATH + "/generate-shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares").isArray())
                .andExpect(jsonPath("$.shares.length()").value(5))
                .andExpect(jsonPath("$.totalShares").value(5))
                .andExpect(jsonPath("$.threshold").value(3));
    }

    @Test
    @WithMockUser(roles = "USER")
    void generateShares_noAdmin_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/generate-shares"))
                .andExpect(status().isForbidden());
    }

    // ─── Seal Info Tests ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSealInfo_withAdmin_returns200() throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UNSEALED");
        info.put("totalShares", 5);
        info.put("threshold", 3);
        info.put("sharesProvided", 0);
        info.put("autoUnsealEnabled", true);

        when(sealService.getSealInfo()).thenReturn(info);

        mockMvc.perform(get(BASE_PATH + "/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSEALED"))
                .andExpect(jsonPath("$.totalShares").value(5));
    }
}
