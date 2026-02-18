package com.codeops.vault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT token validation, bound to the
 * {@code codeops.jwt} prefix in application properties.
 *
 * <p>The Vault service only validates tokens — it never issues them.
 * The {@code secret} must match the signing secret used by CodeOps-Server.</p>
 *
 * @see com.codeops.vault.security.JwtTokenValidator
 */
@ConfigurationProperties(prefix = "codeops.jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private int expirationHours;
    private int refreshExpirationDays;
}
