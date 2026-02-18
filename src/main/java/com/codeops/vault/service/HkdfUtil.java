package com.codeops.vault.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * HMAC-based Key Derivation Function (HKDF) per RFC 5869.
 *
 * <p>Provides deterministic key derivation from a master key using
 * HMAC-SHA256. Given the same inputs, always produces the same output,
 * enabling purpose-specific key derivation without storing derived keys.</p>
 *
 * <p>This is a stateless utility class with only static methods.</p>
 */
public final class HkdfUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32; // SHA-256 output length in bytes

    private HkdfUtil() {} // prevent instantiation

    /**
     * Derives a key using HKDF-Extract then HKDF-Expand (RFC 5869).
     *
     * @param inputKeyMaterial The input keying material (master key).
     * @param salt             Optional salt (null uses zero-filled salt of hash length).
     * @param info             Context and application-specific info string.
     * @param outputLength     Desired output key length in bytes.
     * @return Derived key material of the specified length.
     * @throws IllegalArgumentException if outputLength is zero, negative, or exceeds 255 * hash length.
     */
    public static byte[] derive(byte[] inputKeyMaterial, byte[] salt,
                                byte[] info, int outputLength) {
        if (outputLength <= 0) {
            throw new IllegalArgumentException("Output length must be positive, got: " + outputLength);
        }
        if (outputLength > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "Output length " + outputLength + " exceeds maximum " + (255 * HASH_LENGTH) + " bytes");
        }

        byte[] prk = extract(salt, inputKeyMaterial);
        return expand(prk, info, outputLength);
    }

    /**
     * HKDF-Extract: produces a pseudorandom key (PRK) from input material and salt.
     *
     * <p>Per RFC 5869 Section 2.2: {@code PRK = HMAC-Hash(salt, IKM)}.
     * If salt is null, a zero-filled byte array of hash length is used.</p>
     *
     * @param salt             Optional salt value (null uses zero-filled default).
     * @param inputKeyMaterial The input keying material.
     * @return Pseudorandom key of hash length (32 bytes for SHA-256).
     */
    static byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        if (salt == null || salt.length == 0) {
            salt = new byte[HASH_LENGTH];
        }
        return hmacSha256(salt, inputKeyMaterial);
    }

    /**
     * HKDF-Expand: expands a PRK to the desired output length using info.
     *
     * <p>Per RFC 5869 Section 2.3: iteratively computes
     * {@code T(i) = HMAC-Hash(PRK, T(i-1) || info || i)} where {@code T(0) = empty}.</p>
     *
     * @param prk          Pseudorandom key (from extract step).
     * @param info         Context and application-specific info (may be empty).
     * @param outputLength Desired output key length in bytes.
     * @return Derived key material of the specified length.
     */
    static byte[] expand(byte[] prk, byte[] info, int outputLength) {
        if (info == null) {
            info = new byte[0];
        }

        int iterations = (int) Math.ceil((double) outputLength / HASH_LENGTH);
        byte[] okm = new byte[iterations * HASH_LENGTH];
        byte[] previousT = new byte[0];

        for (int i = 1; i <= iterations; i++) {
            // T(i) = HMAC-Hash(PRK, T(i-1) || info || i)
            byte[] input = new byte[previousT.length + info.length + 1];
            System.arraycopy(previousT, 0, input, 0, previousT.length);
            System.arraycopy(info, 0, input, previousT.length, info.length);
            input[input.length - 1] = (byte) i;

            previousT = hmacSha256(prk, input);
            System.arraycopy(previousT, 0, okm, (i - 1) * HASH_LENGTH, HASH_LENGTH);
        }

        return Arrays.copyOf(okm, outputLength);
    }

    /**
     * Computes HMAC-SHA256.
     *
     * @param key  The HMAC key.
     * @param data The data to authenticate.
     * @return The 32-byte HMAC value.
     */
    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }
}
