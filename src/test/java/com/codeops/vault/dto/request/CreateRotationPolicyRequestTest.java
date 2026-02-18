package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.RotationStrategy;
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
 * Validation tests for {@link CreateRotationPolicyRequest}.
 */
class CreateRotationPolicyRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRandomGenerateRequest_noViolations() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), RotationStrategy.RANDOM_GENERATE,
                24, 32, "alphanumeric", null, null, null, 5);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validExternalApiRequest_noViolations() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), RotationStrategy.EXTERNAL_API,
                72, null, null, "https://api.example.com/rotate", "{\"Authorization\":\"Bearer token\"}", null, null);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validCustomScriptRequest_noViolations() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), RotationStrategy.CUSTOM_SCRIPT,
                168, null, null, null, null, "/opt/scripts/rotate.sh", 3);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void secretIdMissing_hasViolation() {
        var request = new CreateRotationPolicyRequest(null, RotationStrategy.RANDOM_GENERATE,
                24, 32, null, null, null, null, null);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("secretId"));
    }

    @Test
    void strategyMissing_hasViolation() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), null,
                24, null, null, null, null, null, null);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("strategy"));
    }

    @Test
    void rotationIntervalMissing_hasViolation() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), RotationStrategy.RANDOM_GENERATE,
                null, null, null, null, null, null, null);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rotationIntervalHours"));
    }

    @Test
    void randomLengthBelowMinimum_hasViolation() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), RotationStrategy.RANDOM_GENERATE,
                24, 7, null, null, null, null, null);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("randomLength"));
    }

    @Test
    void randomLengthAboveMaximum_hasViolation() {
        var request = new CreateRotationPolicyRequest(UUID.randomUUID(), RotationStrategy.RANDOM_GENERATE,
                24, 1025, null, null, null, null, null);
        Set<ConstraintViolation<CreateRotationPolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("randomLength"));
    }
}
