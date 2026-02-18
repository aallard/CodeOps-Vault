package com.codeops.vault.security;

import com.codeops.vault.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    private String buildValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("teamId", UUID.randomUUID().toString())
                .claim("roles", List.of("MEMBER"))
                .claim("permissions", List.of("vault:read"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void healthEndpoint_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("codeops-vault"));
    }

    @Test
    void protectedEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/vault/secrets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validToken_passesSecurityFilter() throws Exception {
        String token = buildValidToken();
        // Token with MEMBER role passes the JWT filter (not 401) but is denied
        // by @PreAuthorize("hasRole('ADMIN')") on SecretController (403)
        mockMvc.perform(get("/api/v1/vault/secrets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpoint_returnsCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader("X-Correlation-ID")).isNotBlank());
    }
}
