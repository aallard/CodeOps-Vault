package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.SecretType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation tests for {@link CreateSecretRequest}.
 */
class CreateSecretRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest_noViolations() {
        var request = new CreateSecretRequest("/services/app/db", "DB Password", "secret123",
                null, SecretType.STATIC, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validRequest_allFieldsPopulated_noViolations() {
        var request = new CreateSecretRequest("/services/app/db", "DB Password", "secret123",
                "A database password", SecretType.REFERENCE, "arn:aws:secretsmanager:us-east-1:123456:secret:prod/db",
                100, 30, Instant.now().plusSeconds(86400), Map.of("env", "prod", "team", "platform"));
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void pathMissing_hasViolation() {
        var request = new CreateSecretRequest(null, "name", "value",
                null, SecretType.STATIC, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void pathBlank_hasViolation() {
        var request = new CreateSecretRequest("  ", "name", "value",
                null, SecretType.STATIC, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void pathNotStartingWithSlash_hasViolation() {
        var request = new CreateSecretRequest("services/app", "name", "value",
                null, SecretType.STATIC, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("path"));
    }

    @Test
    void nameMissing_hasViolation() {
        var request = new CreateSecretRequest("/path", null, "value",
                null, SecretType.STATIC, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void valueMissing_hasViolation() {
        var request = new CreateSecretRequest("/path", "name", null,
                null, SecretType.STATIC, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("value"));
    }

    @Test
    void secretTypeMissing_hasViolation() {
        var request = new CreateSecretRequest("/path", "name", "value",
                null, null, null, null, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("secretType"));
    }

    @Test
    void maxVersionsZero_hasViolation() {
        var request = new CreateSecretRequest("/path", "name", "value",
                null, SecretType.STATIC, null, 0, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("maxVersions"));
    }

    @Test
    void maxVersionsExceedsLimit_hasViolation() {
        var request = new CreateSecretRequest("/path", "name", "value",
                null, SecretType.STATIC, null, 1001, null, null, null);
        Set<ConstraintViolation<CreateSecretRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("maxVersions"));
    }
}
