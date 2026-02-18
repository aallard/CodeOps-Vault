package com.codeops.vault.security;

import com.codeops.vault.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-minimum-32-characters-long-for-hs256-testing";

    private JwtAuthFilter jwtAuthFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        JwtTokenValidator validator = new JwtTokenValidator(props);
        validator.validateSecret();
        jwtAuthFilter = new JwtAuthFilter(validator);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    private String buildValidToken(UUID userId, UUID teamId,
                                   List<String> roles, List<String> permissions) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("teamId", teamId.toString())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void validToken_setsSecurityContext() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String token = buildValidToken(userId, teamId, List.of("ADMIN"), List.of("vault:read"));
        request.addHeader("Authorization", "Bearer " + token);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingAuthHeader_skipsAuthentication() throws ServletException, IOException {
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_doesNotSetSecurityContext() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid.token.here");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noBearerPrefix_skipsAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rolesMappedToRoleAuthorities() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String token = buildValidToken(userId, teamId, List.of("ADMIN", "MEMBER"), List.of("vault:read"));
        request.addHeader("Authorization", "Bearer " + token);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER"));
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("vault:read"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void teamIdStoredInDetails() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String token = buildValidToken(userId, teamId, List.of("MEMBER"), List.of());
        request.addHeader("Authorization", "Bearer " + token);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getDetails()).isInstanceOf(Map.class);
        Map<String, Object> details = (Map<String, Object>) auth.getDetails();
        assertThat(details.get("teamId")).isEqualTo(teamId);
    }
}
