package com.codeops.vault.service;

import com.codeops.vault.config.VaultProperties;
import com.codeops.vault.dto.response.SealStatusResponse;
import com.codeops.vault.entity.enums.SealStatus;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SealService}.
 *
 * <p>Tests cover seal status management, seal/unseal operations,
 * Shamir's Secret Sharing cryptographic correctness, key share
 * generation, and full lifecycle scenarios.</p>
 */
class SealServiceTest {

    private static final String MASTER_KEY = "test-vault-master-key-minimum-32-characters-for-aes256-testing";

    private VaultProperties vaultProperties;

    @BeforeEach
    void setUp() {
        vaultProperties = new VaultProperties();
        vaultProperties.setMasterKey(MASTER_KEY);
    }

    /**
     * Creates a SealService with auto-unseal enabled and initializes it.
     */
    private SealService createAutoUnsealService() {
        SealService service = new SealService(vaultProperties, null);
        setField(service, "autoUnseal", true);
        setField(service, "configuredTotalShares", 5);
        setField(service, "configuredThreshold", 3);
        service.initialize();
        return service;
    }

    /**
     * Creates a SealService with auto-unseal disabled (manual mode).
     */
    private SealService createManualSealService() {
        SealService service = new SealService(vaultProperties, null);
        setField(service, "autoUnseal", false);
        setField(service, "configuredTotalShares", 5);
        setField(service, "configuredThreshold", 3);
        service.initialize();
        return service;
    }

    /**
     * Sets a field value via reflection (to simulate @Value injection in tests).
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    // ─── Status Tests ───────────────────────────────────────

    @Test
    void initialize_autoUnseal_startsUnsealed() {
        SealService service = createAutoUnsealService();

        assertThat(service.isUnsealed()).isTrue();
        SealStatusResponse status = service.getStatus();
        assertThat(status.status()).isEqualTo(SealStatus.UNSEALED);
        assertThat(status.unsealedAt()).isNotNull();
    }

    @Test
    void isUnsealed_whenUnsealed_returnsTrue() {
        SealService service = createAutoUnsealService();

        assertThat(service.isUnsealed()).isTrue();
    }

    @Test
    void requireUnsealed_whenSealed_throws() {
        SealService service = createManualSealService();

        assertThatThrownBy(service::requireUnsealed)
                .isInstanceOf(CodeOpsVaultException.class)
                .hasMessage("Vault is sealed");
    }

    @Test
    void getStatus_returnsCorrectFields() {
        SealService service = createAutoUnsealService();

        SealStatusResponse status = service.getStatus();
        assertThat(status.status()).isEqualTo(SealStatus.UNSEALED);
        assertThat(status.totalShares()).isEqualTo(5);
        assertThat(status.threshold()).isEqualTo(3);
        assertThat(status.sharesProvided()).isZero();
        assertThat(status.autoUnsealEnabled()).isTrue();
        assertThat(status.sealedAt()).isNotNull();
        assertThat(status.unsealedAt()).isNotNull();
    }

    // ─── Seal Operation Tests ───────────────────────────────

    @Test
    void seal_fromUnsealed_becomesSealed() {
        SealService service = createAutoUnsealService();

        service.seal();

        assertThat(service.isUnsealed()).isFalse();
        SealStatusResponse status = service.getStatus();
        assertThat(status.status()).isEqualTo(SealStatus.SEALED);
        assertThat(status.sealedAt()).isNotNull();
        assertThat(status.unsealedAt()).isNull();
    }

    @Test
    void seal_alreadySealed_throwsValidation() {
        SealService service = createManualSealService();

        assertThatThrownBy(service::seal)
                .isInstanceOf(ValidationException.class)
                .hasMessage("Vault is already sealed");
    }

    @Test
    void seal_clearsCollectedShares() {
        SealService service = createAutoUnsealService();

        // Generate shares, seal, then verify unseal requires fresh shares
        String[] shares = service.generateKeyShares();
        service.seal();

        // Submit one share to move to UNSEALING
        service.submitKeyShare(shares[0]);
        SealStatusResponse status = service.getStatus();
        assertThat(status.status()).isEqualTo(SealStatus.UNSEALING);
        assertThat(status.sharesProvided()).isEqualTo(1);

        // Seal again — should clear partial shares
        service.seal();
        status = service.getStatus();
        assertThat(status.status()).isEqualTo(SealStatus.SEALED);
        assertThat(status.sharesProvided()).isZero();
    }

    // ─── Unseal Operation Tests ─────────────────────────────

    @Test
    void submitKeyShare_firstShare_transitionsToUnsealing() {
        SealService service = createAutoUnsealService();
        String[] shares = service.generateKeyShares();
        service.seal();

        SealStatusResponse result = service.submitKeyShare(shares[0]);

        assertThat(result.status()).isEqualTo(SealStatus.UNSEALING);
        assertThat(result.sharesProvided()).isEqualTo(1);
    }

    @Test
    void submitKeyShare_thresholdReached_transitionsToUnsealed() {
        SealService service = createAutoUnsealService();
        String[] shares = service.generateKeyShares();
        service.seal();

        // Submit threshold (3) shares
        service.submitKeyShare(shares[0]);
        service.submitKeyShare(shares[1]);
        SealStatusResponse result = service.submitKeyShare(shares[2]);

        assertThat(result.status()).isEqualTo(SealStatus.UNSEALED);
        assertThat(result.unsealedAt()).isNotNull();
    }

    @Test
    void submitKeyShare_belowThreshold_remainsUnsealing() {
        SealService service = createAutoUnsealService();
        String[] shares = service.generateKeyShares();
        service.seal();

        service.submitKeyShare(shares[0]);
        SealStatusResponse result = service.submitKeyShare(shares[1]);

        assertThat(result.status()).isEqualTo(SealStatus.UNSEALING);
        assertThat(result.sharesProvided()).isEqualTo(2);
    }

    @Test
    void submitKeyShare_alreadyUnsealed_throwsValidation() {
        SealService service = createAutoUnsealService();
        String[] shares = service.generateKeyShares();

        // Vault is already unsealed — submitting a share should fail
        assertThatThrownBy(() -> service.submitKeyShare(shares[0]))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Vault is already unsealed");
    }

    @Test
    void submitKeyShare_invalidKey_throwsException() {
        SealService service = createAutoUnsealService();
        service.seal();

        // Create fake shares that won't reconstruct to the correct master key
        byte[] fakeShare1 = createFakeShare(1, MASTER_KEY.length());
        byte[] fakeShare2 = createFakeShare(2, MASTER_KEY.length());
        byte[] fakeShare3 = createFakeShare(3, MASTER_KEY.length());

        service.submitKeyShare(Base64.getEncoder().encodeToString(fakeShare1));
        service.submitKeyShare(Base64.getEncoder().encodeToString(fakeShare2));

        assertThatThrownBy(() -> service.submitKeyShare(Base64.getEncoder().encodeToString(fakeShare3)))
                .isInstanceOf(CodeOpsVaultException.class)
                .hasMessage("Reconstructed key does not match master key — unseal failed");
    }

    // ─── Shamir's Secret Sharing Tests ──────────────────────

    @Test
    void splitSecret_reconstructWithThreshold_recoversOriginal() {
        SealService service = createAutoUnsealService();
        byte[] secret = "hello-secret-data".getBytes();

        byte[][] shares = service.splitSecret(secret, 5, 3);

        // Reconstruct with exactly threshold shares (shares 0, 1, 2 → indices 1, 2, 3)
        byte[][] subset = {shares[0], shares[1], shares[2]};
        int[] indices = {1, 2, 3};
        byte[] recovered = service.reconstructSecret(subset, indices);

        assertThat(recovered).isEqualTo(secret);
    }

    @Test
    void splitSecret_reconstructWithAllShares_recoversOriginal() {
        SealService service = createAutoUnsealService();
        byte[] secret = "all-shares-test".getBytes();

        byte[][] shares = service.splitSecret(secret, 5, 3);

        // Reconstruct with all N shares
        int[] indices = {1, 2, 3, 4, 5};
        byte[] recovered = service.reconstructSecret(shares, indices);

        assertThat(recovered).isEqualTo(secret);
    }

    @Test
    void splitSecret_fewerThanThreshold_cannotRecover() {
        SealService service = createAutoUnsealService();
        byte[] secret = "cannot-recover-with-fewer".getBytes();

        byte[][] shares = service.splitSecret(secret, 5, 3);

        // Reconstruct with only 2 shares (below threshold of 3)
        byte[][] subset = {shares[0], shares[1]};
        int[] indices = {1, 2};
        byte[] recovered = service.reconstructSecret(subset, indices);

        // With fewer than threshold shares, the result should NOT match the original
        // (statistically, it's extremely unlikely to match by chance for multi-byte secrets)
        assertThat(recovered).isNotEqualTo(secret);
    }

    @Test
    void splitSecret_differentShareCombinations_allRecover() {
        SealService service = createAutoUnsealService();
        byte[] secret = "combination-test-secret-value".getBytes();

        byte[][] shares = service.splitSecret(secret, 5, 3);

        // Test all C(5,3) = 10 combinations of 3 shares from 5
        int[][] combinations = {
                {0, 1, 2}, {0, 1, 3}, {0, 1, 4}, {0, 2, 3}, {0, 2, 4},
                {0, 3, 4}, {1, 2, 3}, {1, 2, 4}, {1, 3, 4}, {2, 3, 4}
        };

        for (int[] combo : combinations) {
            byte[][] subset = {shares[combo[0]], shares[combo[1]], shares[combo[2]]};
            int[] indices = {combo[0] + 1, combo[1] + 1, combo[2] + 1};
            byte[] recovered = service.reconstructSecret(subset, indices);
            assertThat(recovered)
                    .as("Combination [%d,%d,%d] should recover secret", combo[0], combo[1], combo[2])
                    .isEqualTo(secret);
        }
    }

    @Test
    void splitSecret_singleByte_works() {
        SealService service = createAutoUnsealService();
        byte[] secret = {42};

        byte[][] shares = service.splitSecret(secret, 5, 3);

        byte[][] subset = {shares[1], shares[3], shares[4]};
        int[] indices = {2, 4, 5};
        byte[] recovered = service.reconstructSecret(subset, indices);

        assertThat(recovered).isEqualTo(secret);
    }

    @Test
    void splitSecret_largeSecret_works() {
        SealService service = createAutoUnsealService();
        byte[] secret = new byte[256];
        for (int i = 0; i < 256; i++) {
            secret[i] = (byte) i;
        }

        byte[][] shares = service.splitSecret(secret, 5, 3);

        byte[][] subset = {shares[0], shares[2], shares[4]};
        int[] indices = {1, 3, 5};
        byte[] recovered = service.reconstructSecret(subset, indices);

        assertThat(recovered).isEqualTo(secret);
    }

    @Test
    void splitSecret_invalidParams_throwsException() {
        SealService service = createAutoUnsealService();
        byte[] secret = "test".getBytes();

        // threshold > total
        assertThatThrownBy(() -> service.splitSecret(secret, 3, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Total shares must be >= threshold");

        // threshold < 2
        assertThatThrownBy(() -> service.splitSecret(secret, 5, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Threshold must be at least 2");
    }

    @Test
    void reconstructSecret_deterministicForSameShares() {
        SealService service = createAutoUnsealService();
        byte[] secret = "deterministic-test".getBytes();

        byte[][] shares = service.splitSecret(secret, 5, 3);

        // Reconstruct twice with same shares — should give identical results
        byte[][] subset = {shares[0], shares[2], shares[4]};
        int[] indices = {1, 3, 5};

        byte[] result1 = service.reconstructSecret(subset, indices);
        byte[] result2 = service.reconstructSecret(subset, indices);

        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo(secret);
    }

    // ─── generateKeyShares Tests ────────────────────────────

    @Test
    void generateKeyShares_whenUnsealed_returnsShares() {
        SealService service = createAutoUnsealService();

        String[] shares = service.generateKeyShares();

        assertThat(shares).hasSize(5);
        // Each share should be valid Base64
        for (String share : shares) {
            assertThat(Base64.getDecoder().decode(share)).isNotEmpty();
        }
    }

    @Test
    void generateKeyShares_whenSealed_throwsValidation() {
        SealService service = createManualSealService();

        assertThatThrownBy(service::generateKeyShares)
                .isInstanceOf(ValidationException.class)
                .hasMessage("Vault must be unsealed to generate key shares");
    }

    // ─── Full Lifecycle Tests ───────────────────────────────

    @Test
    void fullCycle_unseal_seal_unseal() {
        SealService service = createAutoUnsealService();
        assertThat(service.isUnsealed()).isTrue();

        // Generate shares while unsealed
        String[] shares = service.generateKeyShares();

        // Seal
        service.seal();
        assertThat(service.isUnsealed()).isFalse();

        // Unseal with 3 of 5 shares
        service.submitKeyShare(shares[0]);
        service.submitKeyShare(shares[2]);
        SealStatusResponse result = service.submitKeyShare(shares[4]);

        assertThat(result.status()).isEqualTo(SealStatus.UNSEALED);
        assertThat(service.isUnsealed()).isTrue();

        // Seal again
        service.seal();
        assertThat(service.isUnsealed()).isFalse();

        // Unseal again with different combination
        service.submitKeyShare(shares[1]);
        service.submitKeyShare(shares[3]);
        result = service.submitKeyShare(shares[4]);

        assertThat(result.status()).isEqualTo(SealStatus.UNSEALED);
        assertThat(service.isUnsealed()).isTrue();
    }

    @Test
    void fullCycle_autoUnsealOff_requiresShares() {
        // Start with auto-unseal enabled to generate shares
        SealService setupService = createAutoUnsealService();
        String[] shares = setupService.generateKeyShares();

        // Create a new service with auto-unseal OFF
        SealService service = new SealService(vaultProperties, null);
        setField(service, "autoUnseal", false);
        setField(service, "configuredTotalShares", 5);
        setField(service, "configuredThreshold", 3);
        service.initialize();

        // Should start sealed
        assertThat(service.isUnsealed()).isFalse();
        assertThat(service.getStatus().status()).isEqualTo(SealStatus.SEALED);

        // requireUnsealed should throw
        assertThatThrownBy(service::requireUnsealed)
                .isInstanceOf(CodeOpsVaultException.class);

        // Unseal with shares
        service.submitKeyShare(shares[0]);
        service.submitKeyShare(shares[1]);
        SealStatusResponse result = service.submitKeyShare(shares[2]);

        assertThat(result.status()).isEqualTo(SealStatus.UNSEALED);
        assertThat(service.isUnsealed()).isTrue();
    }

    // ─── getSealInfo Test ───────────────────────────────────

    @Test
    void getSealInfo_returnsExpectedKeys() {
        SealService service = createAutoUnsealService();

        Map<String, Object> info = service.getSealInfo();

        assertThat(info).containsEntry("status", "UNSEALED");
        assertThat(info).containsEntry("totalShares", 5);
        assertThat(info).containsEntry("threshold", 3);
        assertThat(info).containsEntry("sharesProvided", 0);
        assertThat(info).containsEntry("autoUnsealEnabled", true);
    }

    // ─── Helper ─────────────────────────────────────────────

    /**
     * Creates a fake share with random data that won't reconstruct to the master key.
     */
    private byte[] createFakeShare(int index, int secretLength) {
        byte[] share = new byte[secretLength + 1];
        share[0] = (byte) index;
        new java.security.SecureRandom().nextBytes(share);
        share[0] = (byte) index; // Restore index after random fill
        return share;
    }
}
