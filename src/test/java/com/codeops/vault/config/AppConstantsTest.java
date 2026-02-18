package com.codeops.vault.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppConstantsTest {

    @Test
    void vaultApiPrefix_hasExpectedValue() {
        assertThat(AppConstants.VAULT_API_PREFIX).isEqualTo("/api/v1/vault");
    }

    @Test
    void defaultPageSize_hasExpectedValue() {
        assertThat(AppConstants.DEFAULT_PAGE_SIZE).isEqualTo(20);
    }

    @Test
    void maxPageSize_hasExpectedValue() {
        assertThat(AppConstants.MAX_PAGE_SIZE).isEqualTo(100);
    }

    @Test
    void maxSecretPathLength_hasExpectedValue() {
        assertThat(AppConstants.MAX_SECRET_PATH_LENGTH).isEqualTo(500);
    }

    @Test
    void maxSecretValueSize_isOneMegabyte() {
        assertThat(AppConstants.MAX_SECRET_VALUE_SIZE).isEqualTo(1_048_576);
    }

    @Test
    void maxPolicyNameLength_hasExpectedValue() {
        assertThat(AppConstants.MAX_POLICY_NAME_LENGTH).isEqualTo(200);
    }

    @Test
    void rateLimitRequests_hasExpectedValue() {
        assertThat(AppConstants.RATE_LIMIT_REQUESTS).isEqualTo(100);
    }

    @Test
    void rateLimitWindowSeconds_hasExpectedValue() {
        assertThat(AppConstants.RATE_LIMIT_WINDOW_SECONDS).isEqualTo(60);
    }
}
