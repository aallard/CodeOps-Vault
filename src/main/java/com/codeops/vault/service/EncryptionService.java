package com.codeops.vault.service;

import com.codeops.vault.config.AppConstants;
import com.codeops.vault.config.VaultProperties;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Core cryptographic service for the CodeOps Vault.
 *
 * <p>Provides AES-256-GCM encryption and decryption using envelope encryption.
 * Data is encrypted with a randomly generated Data Encryption Key (DEK),
 * and the DEK is encrypted with a Key Encryption Key (KEK) derived from
 * the Vault master key via HKDF.</p>
 *
 * <h3>Encryption Format</h3>
 * <p>Encrypted values are stored as Base64-encoded concatenation of:</p>
 * <pre>
 * [1 byte: version] [4 bytes: key ID length] [key ID bytes]
 * [4 bytes: encrypted DEK length] [encrypted DEK bytes]
 * [12 bytes: IV] [N bytes: ciphertext + GCM tag]
 * </pre>
 *
 * <h3>Key Hierarchy</h3>
 * <ul>
 *   <li>Master Key — configured via {@code codeops.vault.master-key}</li>
 *   <li>KEK (Key Encryption Key) — derived from master key via HKDF with purpose-specific info</li>
 *   <li>DEK (Data Encryption Key) — randomly generated per encryption, encrypted by KEK</li>
 * </ul>
 *
 * <p>This service is stateless and thread-safe. All methods can be called
 * concurrently without synchronization.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

    private final VaultProperties vaultProperties;

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String AES_ALGORITHM = "AES";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMERIC = "0123456789";
    private static final String HEX_CHARS = "0123456789abcdef";

    // --- Core Encryption Operations ---

    /**
     * Encrypts plaintext using envelope encryption with AES-256-GCM.
     *
     * <p>Generates a random DEK, encrypts the plaintext with the DEK,
     * then encrypts the DEK with the master-derived KEK. Returns the
     * complete envelope as a Base64 string.</p>
     *
     * @param plaintext The data to encrypt (UTF-8 string).
     * @return Base64-encoded encrypted envelope containing encrypted DEK + ciphertext.
     * @throws ValidationException if plaintext is null or empty.
     */
    public String encrypt(String plaintext) {
        validatePlaintext(plaintext);
        byte[] kek = deriveKey("secret-storage");
        return encryptWithKey(plaintext, AppConstants.DEFAULT_ENCRYPTION_KEY_ID, kek);
    }

    /**
     * Encrypts plaintext using a specific key ID (for transit encryption use case).
     *
     * @param plaintext   The data to encrypt (UTF-8 string).
     * @param keyId       The key identifier to include in the envelope.
     * @param keyMaterial The raw key material (32 bytes for AES-256).
     * @return Base64-encoded encrypted envelope.
     * @throws ValidationException if any parameter is null or invalid.
     */
    public String encryptWithKey(String plaintext, String keyId, byte[] keyMaterial) {
        validatePlaintext(plaintext);
        if (keyId == null || keyId.isBlank()) {
            throw new ValidationException("Key ID must not be null or blank");
        }
        if (keyMaterial == null || keyMaterial.length != AppConstants.AES_KEY_SIZE_BYTES) {
            throw new ValidationException("Key material must be exactly " + AppConstants.AES_KEY_SIZE_BYTES + " bytes");
        }

        try {
            // Generate a random DEK
            byte[] dek = generateDataKey();

            // Encrypt the plaintext with the DEK
            byte[] iv = generateIv();
            byte[] ciphertext = aesGcmEncrypt(dek, iv, plaintext.getBytes(StandardCharsets.UTF_8));

            // Encrypt the DEK with the KEK (key material)
            byte[] dekIv = generateIv();
            byte[] encryptedDek = aesGcmEncrypt(keyMaterial, dekIv, dek);

            // Build envelope: version + keyId + encryptedDEK(with its IV) + IV + ciphertext
            byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);

            // Combine DEK IV + encrypted DEK into a single block
            byte[] dekBlock = new byte[AppConstants.GCM_IV_SIZE_BYTES + encryptedDek.length];
            System.arraycopy(dekIv, 0, dekBlock, 0, AppConstants.GCM_IV_SIZE_BYTES);
            System.arraycopy(encryptedDek, 0, dekBlock, AppConstants.GCM_IV_SIZE_BYTES, encryptedDek.length);

            ByteBuffer envelope = ByteBuffer.allocate(
                    1 + 4 + keyIdBytes.length + 4 + dekBlock.length + AppConstants.GCM_IV_SIZE_BYTES + ciphertext.length);

            envelope.put(AppConstants.ENCRYPTION_FORMAT_VERSION);
            envelope.putInt(keyIdBytes.length);
            envelope.put(keyIdBytes);
            envelope.putInt(dekBlock.length);
            envelope.put(dekBlock);
            envelope.put(iv);
            envelope.put(ciphertext);

            String result = Base64.getEncoder().encodeToString(envelope.array());
            log.debug("Encrypted data with key ID '{}', envelope size: {} bytes", keyId, envelope.capacity());
            return result;

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CodeOpsVaultException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded encrypted envelope back to plaintext.
     *
     * <p>Extracts the encrypted DEK and ciphertext from the envelope,
     * decrypts the DEK with the master-derived KEK, then decrypts
     * the ciphertext with the DEK.</p>
     *
     * @param encryptedEnvelope The Base64-encoded encrypted data.
     * @return The decrypted plaintext string.
     * @throws ValidationException     if the envelope is null, empty, or malformed.
     * @throws CodeOpsVaultException if decryption fails (wrong key, tampered data, etc.).
     */
    public String decrypt(String encryptedEnvelope) {
        byte[] kek = deriveKey("secret-storage");
        return decryptWithKey(encryptedEnvelope, kek);
    }

    /**
     * Decrypts using specific key material (for transit encryption use case).
     *
     * @param encryptedEnvelope The Base64-encoded encrypted data.
     * @param keyMaterial       The raw key material matching the key used for encryption.
     * @return The decrypted plaintext string.
     * @throws ValidationException     if any parameter is null or invalid.
     * @throws CodeOpsVaultException if decryption fails.
     */
    public String decryptWithKey(String encryptedEnvelope, byte[] keyMaterial) {
        validateEnvelope(encryptedEnvelope);
        if (keyMaterial == null || keyMaterial.length != AppConstants.AES_KEY_SIZE_BYTES) {
            throw new ValidationException("Key material must be exactly " + AppConstants.AES_KEY_SIZE_BYTES + " bytes");
        }

        try {
            byte[] envelopeBytes = Base64.getDecoder().decode(encryptedEnvelope);
            ByteBuffer buffer = ByteBuffer.wrap(envelopeBytes);

            // Read version
            byte version = buffer.get();
            if (version != AppConstants.ENCRYPTION_FORMAT_VERSION) {
                throw new ValidationException("Unsupported encryption format version: " + version);
            }

            // Read key ID
            int keyIdLength = buffer.getInt();
            if (keyIdLength < 0 || keyIdLength > 1000) {
                throw new ValidationException("Invalid key ID length: " + keyIdLength);
            }
            byte[] keyIdBytes = new byte[keyIdLength];
            buffer.get(keyIdBytes);

            // Read encrypted DEK block (contains DEK IV + encrypted DEK)
            int dekBlockLength = buffer.getInt();
            if (dekBlockLength < AppConstants.GCM_IV_SIZE_BYTES || dekBlockLength > 1000) {
                throw new ValidationException("Invalid encrypted DEK block length: " + dekBlockLength);
            }
            byte[] dekBlock = new byte[dekBlockLength];
            buffer.get(dekBlock);

            // Extract DEK IV and encrypted DEK from the block
            byte[] dekIv = new byte[AppConstants.GCM_IV_SIZE_BYTES];
            System.arraycopy(dekBlock, 0, dekIv, 0, AppConstants.GCM_IV_SIZE_BYTES);
            byte[] encryptedDek = new byte[dekBlockLength - AppConstants.GCM_IV_SIZE_BYTES];
            System.arraycopy(dekBlock, AppConstants.GCM_IV_SIZE_BYTES, encryptedDek, 0, encryptedDek.length);

            // Decrypt the DEK with the KEK
            byte[] dek = aesGcmDecrypt(keyMaterial, dekIv, encryptedDek);

            // Read data IV and ciphertext
            byte[] iv = new byte[AppConstants.GCM_IV_SIZE_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Decrypt the ciphertext with the DEK
            byte[] decrypted = aesGcmDecrypt(dek, iv, ciphertext);

            String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);
            log.debug("Decrypted data with key ID '{}'", keyId);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (ValidationException e) {
            throw e;
        } catch (AEADBadTagException e) {
            throw new CodeOpsVaultException("Decryption failed: authentication tag mismatch (wrong key or tampered data)", e);
        } catch (Exception e) {
            if (e instanceof CodeOpsVaultException) {
                throw (CodeOpsVaultException) e;
            }
            throw new CodeOpsVaultException("Decryption failed: " + e.getMessage(), e);
        }
    }

    // --- Key Management Operations ---

    /**
     * Derives a purpose-specific key from the master key using HKDF.
     *
     * <p>Uses HMAC-SHA256 based HKDF (RFC 5869) to derive keys for
     * specific purposes (e.g., "secret-storage", "transit", "dynamic-creds").</p>
     *
     * @param purpose A purpose string that differentiates derived keys
     *                (e.g., "secret-storage", "transit", "dynamic-credentials").
     * @return 32-byte derived key suitable for AES-256.
     * @throws ValidationException if purpose is null or empty.
     */
    public byte[] deriveKey(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            throw new ValidationException("Key derivation purpose must not be null or blank");
        }

        byte[] masterKeyBytes = vaultProperties.getMasterKey().getBytes(StandardCharsets.UTF_8);
        byte[] info = (AppConstants.HKDF_INFO_PREFIX + purpose).getBytes(StandardCharsets.UTF_8);

        return HkdfUtil.derive(masterKeyBytes, null, info, AppConstants.AES_KEY_SIZE_BYTES);
    }

    /**
     * Generates a new random AES-256 key (Data Encryption Key).
     *
     * <p>Uses {@link SecureRandom} for cryptographic randomness.</p>
     *
     * @return 32-byte random key.
     */
    public byte[] generateDataKey() {
        byte[] key = new byte[AppConstants.AES_KEY_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    /**
     * Generates a new random AES-256 key and returns it both in plaintext
     * and encrypted with the master key (for transit "generate data key" operation).
     *
     * @return A record containing the plaintext key (Base64) and the encrypted key envelope.
     */
    public GeneratedDataKey generateAndWrapDataKey() {
        byte[] dataKey = generateDataKey();
        String plaintextKey = Base64.getEncoder().encodeToString(dataKey);

        // Encrypt the data key with the master-derived KEK
        String encryptedKey = encrypt(plaintextKey);

        return new GeneratedDataKey(plaintextKey, encryptedKey);
    }

    // --- Envelope Operations ---

    /**
     * Re-encrypts an envelope with new key material without exposing plaintext.
     *
     * <p>Used for key rotation: decrypts the DEK with the old key,
     * re-encrypts the DEK with the new key, rebuilds the envelope.</p>
     *
     * @param encryptedEnvelope The existing encrypted envelope.
     * @param oldKeyMaterial    The key material used for original encryption.
     * @param newKeyMaterial    The new key material to encrypt with.
     * @param newKeyId          The new key ID to embed in the envelope.
     * @return New Base64-encoded encrypted envelope with the same plaintext but new key.
     * @throws ValidationException     if any parameter is null or invalid.
     * @throws CodeOpsVaultException if rewrap fails.
     */
    public String rewrap(String encryptedEnvelope, byte[] oldKeyMaterial,
                         byte[] newKeyMaterial, String newKeyId) {
        validateEnvelope(encryptedEnvelope);
        if (oldKeyMaterial == null || oldKeyMaterial.length != AppConstants.AES_KEY_SIZE_BYTES) {
            throw new ValidationException("Old key material must be exactly " + AppConstants.AES_KEY_SIZE_BYTES + " bytes");
        }
        if (newKeyMaterial == null || newKeyMaterial.length != AppConstants.AES_KEY_SIZE_BYTES) {
            throw new ValidationException("New key material must be exactly " + AppConstants.AES_KEY_SIZE_BYTES + " bytes");
        }
        if (newKeyId == null || newKeyId.isBlank()) {
            throw new ValidationException("New key ID must not be null or blank");
        }

        // Decrypt with old key, re-encrypt with new key
        String plaintext = decryptWithKey(encryptedEnvelope, oldKeyMaterial);
        return encryptWithKey(plaintext, newKeyId, newKeyMaterial);
    }

    /**
     * Extracts the key ID from an encrypted envelope without decrypting.
     *
     * <p>Useful for determining which key version was used to encrypt data,
     * needed for transit key rotation decisions.</p>
     *
     * @param encryptedEnvelope The Base64-encoded encrypted envelope.
     * @return The key ID string embedded in the envelope.
     * @throws ValidationException if the envelope is malformed.
     */
    public String extractKeyId(String encryptedEnvelope) {
        validateEnvelope(encryptedEnvelope);

        try {
            byte[] envelopeBytes = Base64.getDecoder().decode(encryptedEnvelope);
            ByteBuffer buffer = ByteBuffer.wrap(envelopeBytes);

            byte version = buffer.get();
            if (version != AppConstants.ENCRYPTION_FORMAT_VERSION) {
                throw new ValidationException("Unsupported encryption format version: " + version);
            }

            int keyIdLength = buffer.getInt();
            if (keyIdLength < 0 || keyIdLength > 1000) {
                throw new ValidationException("Invalid key ID length: " + keyIdLength);
            }

            byte[] keyIdBytes = new byte[keyIdLength];
            buffer.get(keyIdBytes);

            return new String(keyIdBytes, StandardCharsets.UTF_8);

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Malformed encrypted envelope: " + e.getMessage());
        }
    }

    // --- Utility Operations ---

    /**
     * Generates a cryptographically random string of the specified length.
     *
     * <p>Used by RotationService for RANDOM_GENERATE rotation strategy
     * and by DynamicSecretService for credential generation.</p>
     *
     * @param length  Character length of the output.
     * @param charset Allowed character set. Supported values:
     *                "alphanumeric" (a-zA-Z0-9),
     *                "alpha" (a-zA-Z),
     *                "numeric" (0-9),
     *                "hex" (0-9a-f),
     *                "ascii-printable" (printable ASCII 33-126),
     *                or a custom string of allowed characters.
     * @return Random string of the specified length using the specified charset.
     * @throws ValidationException if length &lt; 1 or charset is null/empty.
     */
    public String generateRandomString(int length, String charset) {
        if (length < AppConstants.MIN_RANDOM_LENGTH) {
            throw new ValidationException("Random string length must be at least " + AppConstants.MIN_RANDOM_LENGTH);
        }
        if (charset == null || charset.isBlank()) {
            throw new ValidationException("Charset must not be null or blank");
        }

        String chars = resolveCharset(charset);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Computes SHA-256 hash of input data.
     *
     * <p>Used for integrity checks and deduplication.</p>
     *
     * @param data The data to hash.
     * @return Hex-encoded SHA-256 hash string.
     * @throws ValidationException if data is null.
     */
    public String hash(String data) {
        if (data == null) {
            throw new ValidationException("Data to hash must not be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Validates that the master key is properly configured and usable.
     *
     * <p>Called during application startup to fail fast if encryption
     * cannot work. Performs a test encrypt/decrypt cycle to verify
     * the master key produces valid AES-256-GCM operations.</p>
     *
     * @throws IllegalStateException if the master key is invalid or encryption fails.
     */
    @PostConstruct
    public void validateMasterKey() {
        String testPlaintext = "vault-encryption-test";
        try {
            String encrypted = encrypt(testPlaintext);
            String decrypted = decrypt(encrypted);

            if (!testPlaintext.equals(decrypted)) {
                throw new IllegalStateException("Master key validation failed: decrypt did not match encrypt");
            }

            log.info("Vault master key validated — encryption/decryption operational");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Vault master key validation failed: " + e.getMessage(), e);
        }
    }

    // --- Private Helpers ---

    /**
     * Performs AES-256-GCM encryption.
     *
     * @param key       The 32-byte AES key.
     * @param iv        The 12-byte initialization vector.
     * @param plaintext The data to encrypt.
     * @return Ciphertext with GCM authentication tag appended.
     * @throws Exception if encryption fails.
     */
    private byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(AppConstants.GCM_TAG_SIZE_BITS, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Performs AES-256-GCM decryption.
     *
     * @param key        The 32-byte AES key.
     * @param iv         The 12-byte initialization vector.
     * @param ciphertext The data to decrypt (including GCM tag).
     * @return Decrypted plaintext bytes.
     * @throws Exception if decryption fails (includes AEADBadTagException for auth failure).
     */
    private byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(AppConstants.GCM_TAG_SIZE_BITS, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Generates a random 12-byte IV for GCM.
     *
     * @return 12-byte random initialization vector.
     */
    private byte[] generateIv() {
        byte[] iv = new byte[AppConstants.GCM_IV_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * Validates that plaintext input is suitable for encryption.
     *
     * @param plaintext The plaintext to validate.
     * @throws ValidationException if plaintext is null or empty.
     */
    private void validatePlaintext(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new ValidationException("Plaintext must not be null or empty");
        }
    }

    /**
     * Validates that an encrypted envelope is suitable for decryption.
     *
     * @param encryptedEnvelope The envelope to validate.
     * @throws ValidationException if the envelope is null or empty.
     */
    private void validateEnvelope(String encryptedEnvelope) {
        if (encryptedEnvelope == null || encryptedEnvelope.isBlank()) {
            throw new ValidationException("Encrypted envelope must not be null or blank");
        }
    }

    /**
     * Resolves a charset name to the actual character string.
     *
     * @param charset The charset name or custom character string.
     * @return The resolved character set string.
     */
    private String resolveCharset(String charset) {
        return switch (charset.toLowerCase()) {
            case "alphanumeric" -> ALPHANUMERIC;
            case "alpha" -> ALPHA;
            case "numeric" -> NUMERIC;
            case "hex" -> HEX_CHARS;
            case "ascii-printable" -> buildAsciiPrintable();
            default -> charset; // treat as custom charset string
        };
    }

    /**
     * Builds a string of printable ASCII characters (codes 33-126).
     *
     * @return String containing all printable ASCII characters.
     */
    private String buildAsciiPrintable() {
        StringBuilder sb = new StringBuilder();
        for (char c = 33; c <= 126; c++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
