package com.codeops.vault.entity.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for all Vault enum types.
 *
 * <p>Verifies that each enum has the expected values, correct count,
 * and that valueOf resolves all values.</p>
 */
class EnumTest {

    // ──────────────────────────────────────────────
    // SecretType
    // ──────────────────────────────────────────────

    @Test
    void secretType_hasCorrectValues() {
        assertThat(SecretType.values()).containsExactly(
                SecretType.STATIC, SecretType.DYNAMIC, SecretType.REFERENCE);
    }

    @Test
    void secretType_hasCorrectCount() {
        assertThat(SecretType.values()).hasSize(3);
    }

    @Test
    void secretType_valueOfResolvesAllValues() {
        for (SecretType value : SecretType.values()) {
            assertThat(SecretType.valueOf(value.name())).isEqualTo(value);
        }
    }

    // ──────────────────────────────────────────────
    // PolicyPermission
    // ──────────────────────────────────────────────

    @Test
    void policyPermission_hasCorrectValues() {
        assertThat(PolicyPermission.values()).containsExactly(
                PolicyPermission.READ, PolicyPermission.WRITE, PolicyPermission.DELETE,
                PolicyPermission.LIST, PolicyPermission.ROTATE);
    }

    @Test
    void policyPermission_hasCorrectCount() {
        assertThat(PolicyPermission.values()).hasSize(5);
    }

    @Test
    void policyPermission_valueOfResolvesAllValues() {
        for (PolicyPermission value : PolicyPermission.values()) {
            assertThat(PolicyPermission.valueOf(value.name())).isEqualTo(value);
        }
    }

    // ──────────────────────────────────────────────
    // BindingType
    // ──────────────────────────────────────────────

    @Test
    void bindingType_hasCorrectValues() {
        assertThat(BindingType.values()).containsExactly(
                BindingType.USER, BindingType.TEAM, BindingType.SERVICE);
    }

    @Test
    void bindingType_hasCorrectCount() {
        assertThat(BindingType.values()).hasSize(3);
    }

    @Test
    void bindingType_valueOfResolvesAllValues() {
        for (BindingType value : BindingType.values()) {
            assertThat(BindingType.valueOf(value.name())).isEqualTo(value);
        }
    }

    // ──────────────────────────────────────────────
    // RotationStrategy
    // ──────────────────────────────────────────────

    @Test
    void rotationStrategy_hasCorrectValues() {
        assertThat(RotationStrategy.values()).containsExactly(
                RotationStrategy.RANDOM_GENERATE, RotationStrategy.EXTERNAL_API,
                RotationStrategy.CUSTOM_SCRIPT);
    }

    @Test
    void rotationStrategy_hasCorrectCount() {
        assertThat(RotationStrategy.values()).hasSize(3);
    }

    @Test
    void rotationStrategy_valueOfResolvesAllValues() {
        for (RotationStrategy value : RotationStrategy.values()) {
            assertThat(RotationStrategy.valueOf(value.name())).isEqualTo(value);
        }
    }

    // ──────────────────────────────────────────────
    // LeaseStatus
    // ──────────────────────────────────────────────

    @Test
    void leaseStatus_hasCorrectValues() {
        assertThat(LeaseStatus.values()).containsExactly(
                LeaseStatus.ACTIVE, LeaseStatus.EXPIRED, LeaseStatus.REVOKED);
    }

    @Test
    void leaseStatus_hasCorrectCount() {
        assertThat(LeaseStatus.values()).hasSize(3);
    }

    @Test
    void leaseStatus_valueOfResolvesAllValues() {
        for (LeaseStatus value : LeaseStatus.values()) {
            assertThat(LeaseStatus.valueOf(value.name())).isEqualTo(value);
        }
    }

    // ──────────────────────────────────────────────
    // SealStatus
    // ──────────────────────────────────────────────

    @Test
    void sealStatus_hasCorrectValues() {
        assertThat(SealStatus.values()).containsExactly(
                SealStatus.SEALED, SealStatus.UNSEALED, SealStatus.UNSEALING);
    }

    @Test
    void sealStatus_hasCorrectCount() {
        assertThat(SealStatus.values()).hasSize(3);
    }

    @Test
    void sealStatus_valueOfResolvesAllValues() {
        for (SealStatus value : SealStatus.values()) {
            assertThat(SealStatus.valueOf(value.name())).isEqualTo(value);
        }
    }
}
