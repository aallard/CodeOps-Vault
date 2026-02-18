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
 * Validation tests for {@link TransitEncryptRequest}.
 */
class TransitEncryptRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest_noViolations() {
        var request = new TransitEncryptRequest("payment-key", "SGVsbG8gV29ybGQ=");
        Set<ConstraintViolation<TransitEncryptRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void keyNameMissing_hasViolation() {
        var request = new TransitEncryptRequest(null, "SGVsbG8gV29ybGQ=");
        Set<ConstraintViolation<TransitEncryptRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("keyName"));
    }

    @Test
    void plaintextMissing_hasViolation() {
        var request = new TransitEncryptRequest("payment-key", null);
        Set<ConstraintViolation<TransitEncryptRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("plaintext"));
    }

    @Test
    void keyNameExceedsMaxSize_hasViolation() {
        var request = new TransitEncryptRequest("x".repeat(201), "SGVsbG8gV29ybGQ=");
        Set<ConstraintViolation<TransitEncryptRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("keyName"));
    }
}
