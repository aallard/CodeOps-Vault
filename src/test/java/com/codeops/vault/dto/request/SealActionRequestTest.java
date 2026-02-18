package com.codeops.vault.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation tests for {@link SealActionRequest}.
 */
class SealActionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validSealRequest_noViolations() {
        var request = new SealActionRequest("seal", null);
        Set<ConstraintViolation<SealActionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validUnsealRequest_noViolations() {
        var request = new SealActionRequest("unseal", "key-share-1");
        Set<ConstraintViolation<SealActionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void actionMissing_hasViolation() {
        var request = new SealActionRequest(null, null);
        Set<ConstraintViolation<SealActionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("action"));
    }

    @Test
    void actionInvalid_hasViolation() {
        var request = new SealActionRequest("lock", null);
        Set<ConstraintViolation<SealActionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("action"));
    }
}
