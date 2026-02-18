package com.codeops.vault.service;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HkdfUtil} — HKDF-SHA256 per RFC 5869.
 *
 * <p>Includes RFC 5869 Appendix A test vectors to verify cryptographic correctness.</p>
 */
class HkdfUtilTest {

    private static final HexFormat HEX = HexFormat.of();

    // --- RFC 5869 Appendix A Test Vectors ---

    @Test
    void testVector1_rfc5869() {
        // Test Case 1 from RFC 5869 Appendix A
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
        byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");
        int length = 42;

        byte[] okm = HkdfUtil.derive(ikm, salt, info, length);

        String expected = "3cb25f25faacd57a90434f64d0362f2a"
                + "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                + "34007208d5b887185865";
        assertThat(HEX.formatHex(okm)).isEqualTo(expected);
    }

    @Test
    void testVector2_rfc5869() {
        // Test Case 2 from RFC 5869 Appendix A (longer inputs)
        byte[] ikm = HEX.parseHex(
                "000102030405060708090a0b0c0d0e0f"
                        + "101112131415161718191a1b1c1d1e1f"
                        + "202122232425262728292a2b2c2d2e2f"
                        + "303132333435363738393a3b3c3d3e3f"
                        + "404142434445464748494a4b4c4d4e4f");
        byte[] salt = HEX.parseHex(
                "606162636465666768696a6b6c6d6e6f"
                        + "707172737475767778797a7b7c7d7e7f"
                        + "808182838485868788898a8b8c8d8e8f"
                        + "909192939495969798999a9b9c9d9e9f"
                        + "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
        byte[] info = HEX.parseHex(
                "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
                        + "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf"
                        + "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
                        + "e0e1e2e3e4e5e6e7e8e9eaebecedeeef"
                        + "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");
        int length = 82;

        byte[] okm = HkdfUtil.derive(ikm, salt, info, length);

        String expected = "b11e398dc80327a1c8e7f78c596a4934"
                + "4f012eda2d4efad8a050cc4c19afa97c"
                + "59045a99cac7827271cb41c65e590e09"
                + "da3275600c2f09b8367793a9aca3db71"
                + "cc30c58179ec3e87c14c01d5c1f3434f"
                + "1d87";
        assertThat(HEX.formatHex(okm)).isEqualTo(expected);
    }

    @Test
    void testVector3_rfc5869() {
        // Test Case 3 from RFC 5869 Appendix A (zero-length salt and info)
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = new byte[0];
        byte[] info = new byte[0];
        int length = 42;

        byte[] okm = HkdfUtil.derive(ikm, salt, info, length);

        String expected = "8da4e775a563c18f715f802a063c5a31"
                + "b8a11f5c5ee1879ec3454e5f3c738d2d"
                + "9d201395faa4b61a96c8";
        assertThat(HEX.formatHex(okm)).isEqualTo(expected);
    }

    // --- Edge Case Tests ---

    @Test
    void derive_nullSalt_usesZeroSalt() {
        byte[] ikm = "test-key-material".getBytes();
        byte[] info = "test-info".getBytes();

        byte[] result = HkdfUtil.derive(ikm, null, info, 32);

        assertThat(result).hasSize(32);
        // Verify deterministic
        byte[] result2 = HkdfUtil.derive(ikm, null, info, 32);
        assertThat(result).isEqualTo(result2);
    }

    @Test
    void derive_emptyInfo_succeeds() {
        byte[] ikm = "test-key-material".getBytes();

        byte[] result = HkdfUtil.derive(ikm, null, new byte[0], 32);

        assertThat(result).hasSize(32);
    }

    @Test
    void derive_outputLengthZero_throwsException() {
        byte[] ikm = "test".getBytes();

        assertThatThrownBy(() -> HkdfUtil.derive(ikm, null, new byte[0], 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void derive_outputLengthTooLarge_throwsException() {
        byte[] ikm = "test".getBytes();
        int tooLarge = 255 * 32 + 1;

        assertThatThrownBy(() -> HkdfUtil.derive(ikm, null, new byte[0], tooLarge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void derive_deterministicOutput() {
        byte[] ikm = "deterministic-key".getBytes();
        byte[] salt = "deterministic-salt".getBytes();
        byte[] info = "deterministic-info".getBytes();

        byte[] result1 = HkdfUtil.derive(ikm, salt, info, 64);
        byte[] result2 = HkdfUtil.derive(ikm, salt, info, 64);

        assertThat(result1).isEqualTo(result2);
    }
}
