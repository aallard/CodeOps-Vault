package com.codeops.vault.config;

/**
 * Application-wide constants for the CodeOps Vault service.
 *
 * <p>Defines the Vault API prefix, pagination defaults, secret storage limits,
 * rate limiting parameters, and policy constraints. These values are used across
 * services and controllers to enforce consistent business rules.</p>
 *
 * <p>This class cannot be instantiated.</p>
 */
public final class AppConstants {
    private AppConstants() {}

    /** Base API path prefix for all Vault endpoints. */
    public static final String VAULT_API_PREFIX = "/api/v1/vault";

    // Pagination
    /** Default number of items per page when not specified by client. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Maximum allowed page size to prevent excessive queries. */
    public static final int MAX_PAGE_SIZE = 100;

    // Secret storage limits
    /** Maximum length of a secret path (e.g., "app/database/password"). */
    public static final int MAX_SECRET_PATH_LENGTH = 500;

    /** Maximum size of a secret value in bytes (1 MB). */
    public static final int MAX_SECRET_VALUE_SIZE = 1_048_576;

    /** Maximum length of a policy name. */
    public static final int MAX_POLICY_NAME_LENGTH = 200;

    // Rate limiting
    /** Maximum number of API requests allowed per rate-limit window. */
    public static final int RATE_LIMIT_REQUESTS = 100;

    /** Duration of the rate-limit window in seconds. */
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    // Encryption
    /** AES key size in bytes (256 bits). */
    public static final int AES_KEY_SIZE_BYTES = 32;

    /** GCM initialization vector size in bytes. */
    public static final int GCM_IV_SIZE_BYTES = 12;

    /** GCM authentication tag size in bits. */
    public static final int GCM_TAG_SIZE_BITS = 128;

    /** Current encryption format version. */
    public static final byte ENCRYPTION_FORMAT_VERSION = 1;

    /** Default key ID for master-derived keys. */
    public static final String DEFAULT_ENCRYPTION_KEY_ID = "master-v1";

    /** Minimum master key length in characters. */
    public static final int MIN_MASTER_KEY_LENGTH = 32;

    /** Maximum plaintext size for encryption (1 MB). */
    public static final int MAX_PLAINTEXT_SIZE = 1_048_576;

    /** Minimum random string length. */
    public static final int MIN_RANDOM_LENGTH = 1;

    /** Maximum random string length. */
    public static final int MAX_RANDOM_LENGTH = 4096;

    /** HKDF info prefix for all Vault key derivations. */
    public static final String HKDF_INFO_PREFIX = "codeops-vault-";
}
