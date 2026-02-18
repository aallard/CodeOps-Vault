package com.codeops.vault.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GeneratedDataKey} record.
 */
class GeneratedDataKeyTest {

    @Test
    void record_fieldsAccessible() {
        var key = new GeneratedDataKey("plainKey123", "encKey456");

        assertThat(key.plaintextKey()).isEqualTo("plainKey123");
        assertThat(key.encryptedKey()).isEqualTo("encKey456");
    }

    @Test
    void record_equality() {
        var key1 = new GeneratedDataKey("abc", "def");
        var key2 = new GeneratedDataKey("abc", "def");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    void record_nonNull() {
        var key = new GeneratedDataKey("plain", "encrypted");

        assertThat(key.plaintextKey()).isNotNull();
        assertThat(key.encryptedKey()).isNotNull();
    }
}
