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
 * Validation tests for {@link CreateTransitKeyRequest}.
 */
class CreateTransitKeyRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest_noViolations() {
        var request = new CreateTransitKeyRequest("payment-key", "Encrypts payment data",
                "AES-256-GCM", false, false);
        Set<ConstraintViolation<CreateTransitKeyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validMinimalRequest_noViolations() {
        var request = new CreateTransitKeyRequest("my-key", null, null, false, false);
        Set<ConstraintViolation<CreateTransitKeyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void nameMissing_hasViolation() {
        var request = new CreateTransitKeyRequest(null, null, null, false, false);
        Set<ConstraintViolation<CreateTransitKeyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void nameBlank_hasViolation() {
        var request = new CreateTransitKeyRequest("  ", null, null, false, false);
        Set<ConstraintViolation<CreateTransitKeyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void nameExceedsMaxSize_hasViolation() {
        var request = new CreateTransitKeyRequest("x".repeat(201), null, null, false, false);
        Set<ConstraintViolation<CreateTransitKeyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }
}
