package com.codeops.vault.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation tests for {@link CreateDynamicLeaseRequest}.
 */
class CreateDynamicLeaseRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest_noViolations() {
        var request = new CreateDynamicLeaseRequest(UUID.randomUUID(), 3600);
        Set<ConstraintViolation<CreateDynamicLeaseRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void secretIdMissing_hasViolation() {
        var request = new CreateDynamicLeaseRequest(null, 3600);
        Set<ConstraintViolation<CreateDynamicLeaseRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("secretId"));
    }

    @Test
    void ttlMissing_hasViolation() {
        var request = new CreateDynamicLeaseRequest(UUID.randomUUID(), null);
        Set<ConstraintViolation<CreateDynamicLeaseRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ttlSeconds"));
    }

    @Test
    void ttlBelowMinimum_hasViolation() {
        var request = new CreateDynamicLeaseRequest(UUID.randomUUID(), 59);
        Set<ConstraintViolation<CreateDynamicLeaseRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ttlSeconds"));
    }

    @Test
    void ttlAboveMaximum_hasViolation() {
        var request = new CreateDynamicLeaseRequest(UUID.randomUUID(), 86401);
        Set<ConstraintViolation<CreateDynamicLeaseRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ttlSeconds"));
    }
}
