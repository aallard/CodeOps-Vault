package com.codeops.vault.service;

import com.codeops.vault.config.AppConstants;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EncryptionService}.
 *
 * <p>Covers core encryption/decryption, key management, envelope operations,
 * utility functions, and startup validation.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class EncryptionServiceTest {

    @Autowired
    private EncryptionService encryptionService;

    // ═══════════════════════════════════════════
    //  Core Encryption / Decryption
    // ═══════════════════════════════════════════

    @Test
    void encrypt_decrypt_roundTrip() {
        String plaintext = "my-secret-password-123";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_differentOutputsForSamePlaintext() {
        String plaintext = "same-input-different-output";

        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        // Both should still decrypt to the same plaintext
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_nullPlaintext_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.encrypt(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("null");
    }

    @Test
    void encrypt_emptyPlaintext_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.encrypt(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void decrypt_nullEnvelope_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.decrypt(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("null");
    }

    @Test
    void decrypt_malformedEnvelope_throwsException() {
        String garbage = Base64.getEncoder().encodeToString("not-an-envelope".getBytes());

        assertThatThrownBy(() -> encryptionService.decrypt(garbage))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void decrypt_tamperedCiphertext_throwsException() {
        String encrypted = encryptionService.encrypt("tamper-test");

        // Decode, tamper with last bytes (GCM tag area), re-encode
        byte[] envelopeBytes = Base64.getDecoder().decode(encrypted);
        envelopeBytes[envelopeBytes.length - 1] ^= 0xFF; // flip bits
        envelopeBytes[envelopeBytes.length - 2] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(envelopeBytes);

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(CodeOpsVaultException.class);
    }

    @Test
    void decrypt_wrongKey_throwsException() {
        // Encrypt with custom key
        byte[] key1 = encryptionService.generateDataKey();
        byte[] key2 = encryptionService.generateDataKey();
        String encrypted = encryptionService.encryptWithKey("wrong-key-test", "key-1", key1);

        // Try to decrypt with a different key
        assertThatThrownBy(() -> encryptionService.decryptWithKey(encrypted, key2))
                .isInstanceOf(CodeOpsVaultException.class);
    }

    @Test
    void encrypt_largePayload_succeeds() {
        String largePlaintext = "A".repeat(100_000); // 100KB

        String encrypted = encryptionService.encrypt(largePlaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(largePlaintext);
    }

    @Test
    void encrypt_unicodeContent_roundTrip() {
        String unicode = "Hello \uD83D\uDE80 World \u4F60\u597D \u0645\u0631\u062D\u0628\u0627 \u00E9\u00E8\u00EA \u00FC\u00F6\u00E4";

        String encrypted = encryptionService.encrypt(unicode);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(unicode);
    }

    @Test
    void encrypt_specialCharacters_roundTrip() {
        String special = "line1\nline2\ttab\r\nwindows\0null-byte\u0001control";

        String encrypted = encryptionService.encrypt(special);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(special);
    }

    // ═══════════════════════════════════════════
    //  Key-Specific Encryption
    // ═══════════════════════════════════════════

    @Test
    void encryptWithKey_decryptWithKey_roundTrip() {
        byte[] key = encryptionService.generateDataKey();
        String plaintext = "transit-encryption-test";

        String encrypted = encryptionService.encryptWithKey(plaintext, "transit-v1", key);
        String decrypted = encryptionService.decryptWithKey(encrypted, key);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encryptWithKey_decryptWithDifferentKey_fails() {
        byte[] encryptKey = encryptionService.generateDataKey();
        byte[] decryptKey = encryptionService.generateDataKey();

        String encrypted = encryptionService.encryptWithKey("test", "key-1", encryptKey);

        assertThatThrownBy(() -> encryptionService.decryptWithKey(encrypted, decryptKey))
                .isInstanceOf(CodeOpsVaultException.class);
    }

    @Test
    void encryptWithKey_keyIdEmbeddedInEnvelope() {
        byte[] key = encryptionService.generateDataKey();
        String keyId = "my-custom-key-v3";

        String encrypted = encryptionService.encryptWithKey("test", keyId, key);
        String extractedKeyId = encryptionService.extractKeyId(encrypted);

        assertThat(extractedKeyId).isEqualTo(keyId);
    }

    @Test
    void extractKeyId_returnsCorrectId() {
        String encrypted = encryptionService.encrypt("test-data");
        String keyId = encryptionService.extractKeyId(encrypted);

        assertThat(keyId).isEqualTo(AppConstants.DEFAULT_ENCRYPTION_KEY_ID);
    }

    @Test
    void extractKeyId_malformedEnvelope_throwsException() {
        assertThatThrownBy(() -> encryptionService.extractKeyId("not-valid-base64!@#"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void encryptWithKey_nullKeyId_throwsValidation() {
        byte[] key = encryptionService.generateDataKey();

        assertThatThrownBy(() -> encryptionService.encryptWithKey("test", null, key))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Key ID");
    }

    // ═══════════════════════════════════════════
    //  Key Management
    // ═══════════════════════════════════════════

    @Test
    void deriveKey_sameInput_sameOutput() {
        byte[] key1 = encryptionService.deriveKey("secret-storage");
        byte[] key2 = encryptionService.deriveKey("secret-storage");

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void deriveKey_differentPurpose_differentOutput() {
        byte[] key1 = encryptionService.deriveKey("secret-storage");
        byte[] key2 = encryptionService.deriveKey("transit");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void deriveKey_produces32Bytes() {
        byte[] key = encryptionService.deriveKey("test-purpose");

        assertThat(key).hasSize(AppConstants.AES_KEY_SIZE_BYTES);
    }

    @Test
    void deriveKey_nullPurpose_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.deriveKey(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("purpose");
    }

    @Test
    void generateDataKey_produces32Bytes() {
        byte[] key = encryptionService.generateDataKey();

        assertThat(key).hasSize(AppConstants.AES_KEY_SIZE_BYTES);
    }

    @Test
    void generateAndWrapDataKey_canDecryptWrappedKey() {
        GeneratedDataKey generated = encryptionService.generateAndWrapDataKey();

        assertThat(generated.plaintextKey()).isNotBlank();
        assertThat(generated.encryptedKey()).isNotBlank();

        // Decrypt the wrapped key — should return the same plaintext key
        String decryptedKey = encryptionService.decrypt(generated.encryptedKey());
        assertThat(decryptedKey).isEqualTo(generated.plaintextKey());
    }

    // ═══════════════════════════════════════════
    //  Envelope Operations
    // ═══════════════════════════════════════════

    @Test
    void rewrap_producesNewEnvelopeSamePlaintext() {
        byte[] oldKey = encryptionService.generateDataKey();
        byte[] newKey = encryptionService.generateDataKey();
        String plaintext = "rewrap-test-data";

        String original = encryptionService.encryptWithKey(plaintext, "old-v1", oldKey);
        String rewrapped = encryptionService.rewrap(original, oldKey, newKey, "new-v2");

        // Decrypt with new key should return original plaintext
        String decrypted = encryptionService.decryptWithKey(rewrapped, newKey);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void rewrap_oldKeyNoLongerWorks() {
        byte[] oldKey = encryptionService.generateDataKey();
        byte[] newKey = encryptionService.generateDataKey();

        String original = encryptionService.encryptWithKey("data", "old-v1", oldKey);
        String rewrapped = encryptionService.rewrap(original, oldKey, newKey, "new-v2");

        // Old key should NOT decrypt the rewrapped envelope
        assertThatThrownBy(() -> encryptionService.decryptWithKey(rewrapped, oldKey))
                .isInstanceOf(CodeOpsVaultException.class);
    }

    @Test
    void rewrap_newKeyIdEmbedded() {
        byte[] oldKey = encryptionService.generateDataKey();
        byte[] newKey = encryptionService.generateDataKey();

        String original = encryptionService.encryptWithKey("data", "old-v1", oldKey);
        String rewrapped = encryptionService.rewrap(original, oldKey, newKey, "new-v2");

        assertThat(encryptionService.extractKeyId(rewrapped)).isEqualTo("new-v2");
    }

    @Test
    void rewrap_nullInputs_throwsValidation() {
        byte[] key = encryptionService.generateDataKey();

        assertThatThrownBy(() -> encryptionService.rewrap(null, key, key, "id"))
                .isInstanceOf(ValidationException.class);
    }

    // ═══════════════════════════════════════════
    //  Utility — Random String Generation
    // ═══════════════════════════════════════════

    @Test
    void generateRandomString_alphanumeric_correctLength() {
        String result = encryptionService.generateRandomString(64, "alphanumeric");

        assertThat(result).hasSize(64);
    }

    @Test
    void generateRandomString_alphanumeric_onlyAllowedChars() {
        String result = encryptionService.generateRandomString(1000, "alphanumeric");

        assertThat(result).matches("[A-Za-z0-9]+");
    }

    @Test
    void generateRandomString_numeric_onlyDigits() {
        String result = encryptionService.generateRandomString(100, "numeric");

        assertThat(result).matches("[0-9]+");
    }

    @Test
    void generateRandomString_hex_onlyHex() {
        String result = encryptionService.generateRandomString(100, "hex");

        assertThat(result).matches("[0-9a-f]+");
    }

    @Test
    void generateRandomString_customCharset_usesOnlyThoseChars() {
        String customChars = "ABC123";
        String result = encryptionService.generateRandomString(200, customChars);

        assertThat(result).hasSize(200);
        for (char c : result.toCharArray()) {
            assertThat(customChars).contains(String.valueOf(c));
        }
    }

    @Test
    void generateRandomString_invalidLength_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.generateRandomString(0, "alphanumeric"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void generateRandomString_nullCharset_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.generateRandomString(10, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void generateRandomString_asciiPrintable_correctRange() {
        String result = encryptionService.generateRandomString(500, "ascii-printable");

        for (char c : result.toCharArray()) {
            assertThat((int) c).isBetween(33, 126);
        }
    }

    @Test
    void generateRandomString_alpha_onlyLetters() {
        String result = encryptionService.generateRandomString(200, "alpha");

        assertThat(result).matches("[A-Za-z]+");
    }

    // ═══════════════════════════════════════════
    //  Utility — Hash
    // ═══════════════════════════════════════════

    @Test
    void hash_producesConsistentOutput() {
        String data = "hello world";

        String hash1 = encryptionService.hash(data);
        String hash2 = encryptionService.hash(data);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_differentInput_differentOutput() {
        String hash1 = encryptionService.hash("input1");
        String hash2 = encryptionService.hash("input2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_outputIs64HexChars() {
        String hash = encryptionService.hash("test data");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void hash_nullInput_throwsValidation() {
        assertThatThrownBy(() -> encryptionService.hash(null))
                .isInstanceOf(ValidationException.class);
    }

    // ═══════════════════════════════════════════
    //  Startup Validation
    // ═══════════════════════════════════════════

    @Test
    void validateMasterKey_validKey_noException() {
        // The test profile has a valid master key, and @PostConstruct already ran.
        // Just verify the service is operational by doing a round trip.
        String encrypted = encryptionService.encrypt("startup-test");
        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo("startup-test");
    }

    @Test
    void generateDataKey_uniqueKeys() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            keys.add(Base64.getEncoder().encodeToString(encryptionService.generateDataKey()));
        }
        // All 100 generated keys should be unique
        assertThat(keys).hasSize(100);
    }
}
