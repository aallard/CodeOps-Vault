package com.codeops.vault.security;

import com.codeops.vault.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private static final String VALID_SECRET = "test-secret-key-minimum-32-characters-long-for-hs256-testing";
    private static final String WRONG_SECRET = "wrong-secret-key-minimum-32-characters-long-for-hs256-wrong";

    private JwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(VALID_SECRET);
        validator = new JwtTokenValidator(props);
        validator.validateSecret();
    }

    private String buildToken(UUID userId, UUID teamId, List<String> roles,
                              List<String> permissions, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes());
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256);
        if (teamId != null) {
            builder.claim("teamId", teamId.toString());
        }
        return builder.compact();
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, UUID.randomUUID(),
                List.of("ADMIN"), List.of("vault:read"),
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, UUID.randomUUID(),
                List.of("MEMBER"), List.of(),
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(validator.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_invalidSignature_returnsFalse() {
        UUID userId = UUID.randomUUID();
        SecretKey wrongKey = Keys.hmacShaKeyFor(WRONG_SECRET.getBytes());
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(wrongKey, Jwts.SIG.HS256)
                .compact();
        assertThat(validator.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(validator.validateToken("not.a.valid.jwt")).isFalse();
    }

    @Test
    void validateToken_emptyToken_returnsFalse() {
        assertThat(validator.validateToken("")).isFalse();
    }

    @Test
    void getUserIdFromToken_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, UUID.randomUUID(),
                List.of("MEMBER"), List.of(),
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getUserIdFromToken(token)).isEqualTo(userId);
    }

    @Test
    void getTeamIdFromToken_returnsCorrectUUID() {
        UUID teamId = UUID.randomUUID();
        String token = buildToken(UUID.randomUUID(), teamId,
                List.of("MEMBER"), List.of(),
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getTeamIdFromToken(token)).isEqualTo(teamId);
    }

    @Test
    void getRolesAndPermissions_returnsCorrectValues() {
        List<String> roles = List.of("ADMIN", "MEMBER");
        List<String> permissions = List.of("vault:read", "vault:write");
        String token = buildToken(UUID.randomUUID(), UUID.randomUUID(),
                roles, permissions,
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getRolesFromToken(token)).containsExactly("ADMIN", "MEMBER");
        assertThat(validator.getPermissionsFromToken(token)).containsExactly("vault:read", "vault:write");
    }

    @Test
    void validateSecret_secretTooShort_throwsException() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("short");
        JwtTokenValidator shortValidator = new JwtTokenValidator(shortProps);
        assertThatThrownBy(shortValidator::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void validateSecret_nullSecret_throwsException() {
        JwtProperties nullProps = new JwtProperties();
        nullProps.setSecret(null);
        JwtTokenValidator nullValidator = new JwtTokenValidator(nullProps);
        assertThatThrownBy(nullValidator::validateSecret)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getTeamIdFromToken_missingClaim_returnsNull() {
        String token = buildToken(UUID.randomUUID(), null,
                List.of("MEMBER"), List.of(),
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getTeamIdFromToken(token)).isNull();
    }

    @Test
    void getPermissionsFromToken_missingClaim_returnsEmptyList() {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes());
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of("MEMBER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        assertThat(validator.getPermissionsFromToken(token)).isEmpty();
    }
}
