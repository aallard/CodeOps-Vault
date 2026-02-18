package com.codeops.vault.service;

import com.codeops.vault.config.VaultProperties;
import com.codeops.vault.dto.response.SealStatusResponse;
import com.codeops.vault.entity.enums.SealStatus;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Vault seal/unseal lifecycle.
 *
 * <p>The Vault must be unsealed before any secret operations can proceed.
 * In production, unsealing requires M of N key shares (Shamir's Secret
 * Sharing). In development, auto-unseal uses the configured master key.</p>
 *
 * <h3>Seal States</h3>
 * <ul>
 *   <li>{@link SealStatus#SEALED} — No operations allowed. Initial state.</li>
 *   <li>{@link SealStatus#UNSEALING} — Shares are being collected. Partial unseal.</li>
 *   <li>{@link SealStatus#UNSEALED} — Fully operational.</li>
 * </ul>
 *
 * <h3>Shamir's Secret Sharing</h3>
 * <p>The master key is split into N shares such that any M shares can
 * reconstruct the original key. This uses polynomial interpolation over
 * a finite field (GF(256)).</p>
 *
 * <h3>Auto-Unseal (Development)</h3>
 * <p>When {@code codeops.vault.seal.auto-unseal=true} (default), the Vault
 * automatically unseals at startup using the configured master key.
 * No key shares are needed.</p>
 */
@Service
@Slf4j
public class SealService {

    private final VaultProperties vaultProperties;
    private final AuditService auditService;

    /** Current seal status — volatile for visibility across threads. */
    private volatile SealStatus status = SealStatus.SEALED;

    /** Collected key shares during unsealing. */
    private final List<byte[]> collectedShares = new ArrayList<>();

    /** The 1-based indices of the collected shares (for Lagrange interpolation). */
    private final List<Integer> collectedShareIndices = new ArrayList<>();

    /** Shamir configuration. */
    private int totalShares = 5;
    private int threshold = 3;

    /** Timestamps. */
    private Instant sealedAt = Instant.now();
    private Instant unsealedAt;

    /** Whether auto-unseal is enabled. */
    @Value("${codeops.vault.seal.auto-unseal:true}")
    private boolean autoUnseal;

    /** Configured total number of Shamir key shares. */
    @Value("${codeops.vault.seal.total-shares:5}")
    private int configuredTotalShares;

    /** Configured threshold of shares required to unseal. */
    @Value("${codeops.vault.seal.threshold:3}")
    private int configuredThreshold;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ─── GF(256) Lookup Tables ──────────────────────────────

    /**
     * Logarithm table for GF(256) using irreducible polynomial 0x11D (AES polynomial).
     * LOG[x] = discrete log base 3 of x in GF(256) with polynomial 0x11B. LOG[0] is unused.
     */
    private static final int[] LOG = new int[256];

    /**
     * Exponent (anti-log) table for GF(256).
     * EXP[i] = 3^i mod polynomial in GF(256). Extended to 512 entries to avoid modular indexing.
     */
    private static final int[] EXP = new int[512];

    static {
        // Build log/exp tables using generator 3 with AES irreducible polynomial 0x11B
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x = gf256MulNoTable(x, 3);
        }
        // Extend EXP table for convenience (avoids mod 255 during multiplication)
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    /**
     * GF(256) multiplication without tables (used only for building tables).
     *
     * @param a First operand (0-255).
     * @param b Second operand (0-255).
     * @return Product in GF(256) using AES polynomial 0x11B.
     */
    private static int gf256MulNoTable(int a, int b) {
        int result = 0;
        int aa = a;
        int bb = b;
        while (bb > 0) {
            if ((bb & 1) != 0) {
                result ^= aa;
            }
            aa <<= 1;
            if ((aa & 0x100) != 0) {
                aa ^= 0x11B;
            }
            bb >>= 1;
        }
        return result;
    }

    /**
     * Creates a new SealService with the given Vault properties and audit service.
     *
     * @param vaultProperties Configuration containing the master key.
     * @param auditService    Audit logging service (nullable for testing).
     */
    public SealService(VaultProperties vaultProperties, AuditService auditService) {
        this.vaultProperties = vaultProperties;
        this.auditService = auditService;
    }

    /**
     * Initializes the seal service. If auto-unseal is enabled, unseals immediately.
     */
    @PostConstruct
    public void initialize() {
        this.totalShares = configuredTotalShares;
        this.threshold = configuredThreshold;
        if (autoUnseal) {
            log.info("Auto-unseal enabled — Vault is unsealing automatically");
            this.status = SealStatus.UNSEALED;
            this.unsealedAt = Instant.now();
            log.info("Vault unsealed successfully (auto-unseal)");
        } else {
            log.warn("Vault is SEALED — provide {} of {} key shares to unseal", threshold, totalShares);
        }
    }

    // ─── Status ─────────────────────────────────────────────

    /**
     * Gets the current seal status.
     *
     * @return SealStatusResponse with status, share counts, and timestamps.
     */
    public SealStatusResponse getStatus() {
        return new SealStatusResponse(
                status,
                totalShares,
                threshold,
                collectedShares.size(),
                autoUnseal,
                sealedAt,
                unsealedAt
        );
    }

    /**
     * Checks if the Vault is unsealed (operational).
     *
     * @return true if status is UNSEALED.
     */
    public boolean isUnsealed() {
        return status == SealStatus.UNSEALED;
    }

    /**
     * Asserts the Vault is unsealed. Throws if sealed.
     *
     * <p>Called by other services before performing operations.</p>
     *
     * @throws CodeOpsVaultException if the Vault is sealed or unsealing.
     */
    public void requireUnsealed() {
        if (status != SealStatus.UNSEALED) {
            throw new CodeOpsVaultException("Vault is sealed");
        }
    }

    // ─── Seal Operation ─────────────────────────────────────

    /**
     * Seals the Vault — blocks all secret operations.
     *
     * <p>Clears collected shares, sets status to SEALED,
     * records sealedAt timestamp.</p>
     *
     * @throws ValidationException if already sealed.
     */
    public synchronized void seal() {
        if (status == SealStatus.SEALED) {
            throw new ValidationException("Vault is already sealed");
        }
        collectedShares.clear();
        collectedShareIndices.clear();
        this.status = SealStatus.SEALED;
        this.sealedAt = Instant.now();
        this.unsealedAt = null;
        log.info("Vault sealed");

        try { if (auditService != null) auditService.logSuccess(null, null, "SEAL", null, "VAULT", null, null); }
        catch (Exception e) { log.warn("Audit log failed for seal: {}", e.getMessage()); }
    }

    // ─── Unseal Operation ───────────────────────────────────

    /**
     * Submits a key share for unsealing.
     *
     * <p>If this is the first share, transitions to UNSEALING state.
     * If enough shares have been collected (&gt;= threshold), reconstructs
     * the master key and transitions to UNSEALED.</p>
     *
     * @param keyShareBase64 Base64-encoded key share. Format: "index:shareData" where
     *                       index is the 1-based share index and shareData is the
     *                       Base64-encoded share bytes.
     * @return SealStatusResponse with updated share count and status.
     * @throws ValidationException if already unsealed.
     * @throws CodeOpsVaultException if the reconstructed key is invalid.
     */
    public synchronized SealStatusResponse submitKeyShare(String keyShareBase64) {
        if (status == SealStatus.UNSEALED) {
            throw new ValidationException("Vault is already unsealed");
        }

        // Parse the share: format is "index:base64ShareData"
        byte[] shareWithIndex = Base64.getDecoder().decode(keyShareBase64);
        // First byte is the 1-based share index
        int shareIndex = shareWithIndex[0] & 0xFF;
        byte[] shareData = new byte[shareWithIndex.length - 1];
        System.arraycopy(shareWithIndex, 1, shareData, 0, shareData.length);

        collectedShares.add(shareData);
        collectedShareIndices.add(shareIndex);

        if (status == SealStatus.SEALED) {
            this.status = SealStatus.UNSEALING;
            log.info("Vault transitioning to UNSEALING — {}/{} shares collected", collectedShares.size(), threshold);
        } else {
            log.info("Key share accepted — {}/{} shares collected", collectedShares.size(), threshold);
        }

        if (collectedShares.size() >= threshold) {
            // Attempt reconstruction
            byte[][] sharesArray = collectedShares.toArray(new byte[0][]);
            int[] indicesArray = collectedShareIndices.stream().mapToInt(Integer::intValue).toArray();

            byte[] reconstructedKey = reconstructSecret(sharesArray, indicesArray);
            String reconstructedKeyStr = new String(reconstructedKey, StandardCharsets.UTF_8);

            // Verify reconstructed key matches the configured master key
            if (!reconstructedKeyStr.equals(vaultProperties.getMasterKey())) {
                // Clear shares and reset to sealed on bad key
                collectedShares.clear();
                collectedShareIndices.clear();
                this.status = SealStatus.SEALED;
                throw new CodeOpsVaultException("Reconstructed key does not match master key — unseal failed");
            }

            this.status = SealStatus.UNSEALED;
            this.unsealedAt = Instant.now();
            collectedShares.clear();
            collectedShareIndices.clear();
            log.info("Vault unsealed successfully with {} key shares", threshold);

            try { if (auditService != null) auditService.logSuccess(null, null, "UNSEAL", null, "VAULT", null, null); }
            catch (Exception ex) { log.warn("Audit log failed for unseal: {}", ex.getMessage()); }
        }

        return getStatus();
    }

    // ─── Shamir's Secret Sharing ────────────────────────────

    /**
     * Splits a secret into N shares where M are required to reconstruct.
     *
     * <p>Uses polynomial interpolation over GF(256). Each share is the
     * same length as the input secret. For each byte of the secret, a
     * random polynomial of degree (thresholdM - 1) is generated with the
     * secret byte as the constant term, and evaluated at points 1..totalN.</p>
     *
     * <p>Package-private for testability.</p>
     *
     * @param secret     The secret bytes to split.
     * @param totalN     Total number of shares to generate.
     * @param thresholdM Minimum shares required for reconstruction.
     * @return Array of N shares, each the same length as the secret.
     * @throws IllegalArgumentException if thresholdM &gt; totalN or either &lt; 2.
     */
    byte[][] splitSecret(byte[] secret, int totalN, int thresholdM) {
        if (thresholdM < 2) {
            throw new IllegalArgumentException("Threshold must be at least 2");
        }
        if (totalN < thresholdM) {
            throw new IllegalArgumentException("Total shares must be >= threshold");
        }
        if (totalN > 255) {
            throw new IllegalArgumentException("Total shares must be <= 255");
        }

        byte[][] shares = new byte[totalN][secret.length];

        for (int byteIdx = 0; byteIdx < secret.length; byteIdx++) {
            // Generate random polynomial coefficients: a[0] = secret byte, a[1..threshold-1] = random
            int[] coefficients = new int[thresholdM];
            coefficients[0] = secret[byteIdx] & 0xFF;
            for (int k = 1; k < thresholdM; k++) {
                coefficients[k] = SECURE_RANDOM.nextInt(256);
            }

            // Evaluate polynomial at x = 1, 2, ..., totalN
            for (int shareIdx = 0; shareIdx < totalN; shareIdx++) {
                int x = shareIdx + 1; // 1-based
                shares[shareIdx][byteIdx] = (byte) evaluatePolynomial(coefficients, x);
            }
        }

        return shares;
    }

    /**
     * Reconstructs a secret from M or more shares.
     *
     * <p>Uses Lagrange interpolation over GF(256) to recover the secret
     * polynomial's constant term (the original secret).</p>
     *
     * <p>Package-private for testability.</p>
     *
     * @param shares       Array of shares (at least thresholdM).
     * @param shareIndices The x-coordinates (1-based indices) of the shares.
     * @return The reconstructed secret bytes.
     * @throws IllegalArgumentException if insufficient shares provided.
     */
    byte[] reconstructSecret(byte[][] shares, int[] shareIndices) {
        if (shares == null || shares.length == 0) {
            throw new IllegalArgumentException("At least one share is required");
        }
        if (shares.length != shareIndices.length) {
            throw new IllegalArgumentException("Number of shares must match number of indices");
        }

        int secretLength = shares[0].length;
        byte[] secret = new byte[secretLength];

        for (int byteIdx = 0; byteIdx < secretLength; byteIdx++) {
            // Lagrange interpolation at x=0 to recover the constant term
            int result = 0;
            for (int i = 0; i < shares.length; i++) {
                int xi = shareIndices[i];
                int yi = shares[i][byteIdx] & 0xFF;

                // Compute Lagrange basis polynomial L_i(0)
                int numerator = 1;
                int denominator = 1;
                for (int j = 0; j < shares.length; j++) {
                    if (i == j) continue;
                    int xj = shareIndices[j];
                    // L_i(0) = product of (0 - xj) / (xi - xj) for j != i
                    // In GF(256): subtraction is XOR, so 0 - xj = xj, and xi - xj = xi ^ xj
                    numerator = gf256Mul(numerator, xj);
                    denominator = gf256Mul(denominator, xi ^ xj);
                }

                // L_i(0) = numerator / denominator
                int basis = gf256Div(numerator, denominator);
                // Accumulate: result += yi * basis (XOR in GF(256))
                result ^= gf256Mul(yi, basis);
            }

            secret[byteIdx] = (byte) result;
        }

        return secret;
    }

    // ─── Key Share Generation (for initial setup) ───────────

    /**
     * Generates key shares from the current master key.
     *
     * <p>Used during initial Vault setup to distribute shares to operators.
     * Only callable when the Vault is unsealed. Each share is encoded as
     * Base64 with the share index prepended as the first byte.</p>
     *
     * @return Array of Base64-encoded key shares.
     * @throws ValidationException if the Vault is not unsealed.
     */
    public String[] generateKeyShares() {
        if (status != SealStatus.UNSEALED) {
            throw new ValidationException("Vault must be unsealed to generate key shares");
        }

        byte[] masterKeyBytes = vaultProperties.getMasterKey().getBytes(StandardCharsets.UTF_8);
        byte[][] rawShares = splitSecret(masterKeyBytes, totalShares, threshold);

        String[] encodedShares = new String[totalShares];
        for (int i = 0; i < totalShares; i++) {
            // Prepend 1-based index as first byte
            byte[] shareWithIndex = new byte[rawShares[i].length + 1];
            shareWithIndex[0] = (byte) (i + 1);
            System.arraycopy(rawShares[i], 0, shareWithIndex, 1, rawShares[i].length);
            encodedShares[i] = Base64.getEncoder().encodeToString(shareWithIndex);
        }

        log.info("Generated {} key shares with threshold {}", totalShares, threshold);

        try { if (auditService != null) auditService.logSuccess(null, null, "WRITE", null, "VAULT_SHARES", null, null); }
        catch (Exception e) { log.warn("Audit log failed for generateKeyShares: {}", e.getMessage()); }

        return encodedShares;
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets seal state information for monitoring.
     *
     * @return Map with "status", "totalShares", "threshold", "sharesProvided",
     *         "autoUnsealEnabled".
     */
    public Map<String, Object> getSealInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", status.name());
        info.put("totalShares", totalShares);
        info.put("threshold", threshold);
        info.put("sharesProvided", collectedShares.size());
        info.put("autoUnsealEnabled", autoUnseal);
        return info;
    }

    // ─── GF(256) Arithmetic ─────────────────────────────────

    /**
     * Multiplies two elements in GF(256) using log/exp tables.
     *
     * @param a First operand (0-255).
     * @param b Second operand (0-255).
     * @return Product in GF(256).
     */
    private static int gf256Mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    /**
     * Divides two elements in GF(256) using log/exp tables.
     *
     * @param a Dividend (0-255).
     * @param b Divisor (0-255, must not be zero).
     * @return Quotient in GF(256).
     * @throws ArithmeticException if b is zero.
     */
    private static int gf256Div(int a, int b) {
        if (b == 0) throw new ArithmeticException("Division by zero in GF(256)");
        if (a == 0) return 0;
        return EXP[(LOG[a] - LOG[b] + 255) % 255];
    }

    /**
     * Evaluates a polynomial at a given point in GF(256).
     *
     * <p>Uses Horner's method for efficient evaluation:
     * p(x) = a[0] + a[1]*x + a[2]*x^2 + ... = a[0] + x*(a[1] + x*(a[2] + ...))</p>
     *
     * @param coefficients Polynomial coefficients (constant term first).
     * @param x            The point to evaluate at (1-255).
     * @return The polynomial value at x in GF(256).
     */
    private static int evaluatePolynomial(int[] coefficients, int x) {
        int result = 0;
        // Horner's method: start from highest degree
        for (int i = coefficients.length - 1; i >= 0; i--) {
            result = gf256Mul(result, x) ^ coefficients[i];
        }
        return result;
    }
}
