package com.codeops.vault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for dynamic secret generation.
 *
 * <p>Controls dynamic lease behavior including TTL defaults,
 * password generation, and whether to execute SQL against
 * target databases.</p>
 */
@ConfigurationProperties(prefix = "codeops.vault.dynamic-secrets")
@Getter
@Setter
public class DynamicSecretProperties {

    /** Whether to execute SQL against target databases. Default: false. */
    private boolean executeSql = false;

    /** Default TTL in seconds for dynamic leases. Default: 3600 (1 hour). */
    private int defaultTtlSeconds = 3600;

    /** Maximum TTL in seconds. Default: 86400 (24 hours). */
    private int maxTtlSeconds = 86400;

    /** Password length for generated credentials. Default: 32. */
    private int passwordLength = 32;

    /** Username prefix for generated usernames. Default: "v_". */
    private String usernamePrefix = "v_";
}
