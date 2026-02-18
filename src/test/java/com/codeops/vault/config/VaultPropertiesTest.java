package com.codeops.vault.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultPropertiesTest {

    @Test
    void validateMasterKey_validKey_doesNotThrow() {
        VaultProperties props = new VaultProperties();
        props.setMasterKey("valid-vault-master-key-minimum-32-characters-for-aes256");
        assertThatCode(props::validateMasterKey).doesNotThrowAnyException();
    }

    @Test
    void validateMasterKey_shortKey_throwsException() {
        VaultProperties props = new VaultProperties();
        props.setMasterKey("short");
        assertThatThrownBy(props::validateMasterKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void validateMasterKey_nullKey_throwsException() {
        VaultProperties props = new VaultProperties();
        props.setMasterKey(null);
        assertThatThrownBy(props::validateMasterKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
