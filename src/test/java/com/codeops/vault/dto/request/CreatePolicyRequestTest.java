package com.codeops.vault.dto.request;

import com.codeops.vault.entity.enums.PolicyPermission;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation tests for {@link CreatePolicyRequest}.
 */
class CreatePolicyRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest_noViolations() {
        var request = new CreatePolicyRequest("readonly-policy", null,
                "/services/app/*", List.of(PolicyPermission.READ, PolicyPermission.LIST), false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validDenyPolicy_noViolations() {
        var request = new CreatePolicyRequest("deny-delete", "Prevents deletion of secrets",
                "/services/**", List.of(PolicyPermission.DELETE), true);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void nameMissing_hasViolation() {
        var request = new CreatePolicyRequest(null, null,
                "/services/*", List.of(PolicyPermission.READ), false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void nameBlank_hasViolation() {
        var request = new CreatePolicyRequest("  ", null,
                "/services/*", List.of(PolicyPermission.READ), false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void pathPatternMissing_hasViolation() {
        var request = new CreatePolicyRequest("policy", null,
                null, List.of(PolicyPermission.READ), false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("pathPattern"));
    }

    @Test
    void permissionsEmpty_hasViolation() {
        var request = new CreatePolicyRequest("policy", null,
                "/services/*", Collections.emptyList(), false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("permissions"));
    }

    @Test
    void permissionsNull_hasViolation() {
        var request = new CreatePolicyRequest("policy", null,
                "/services/*", null, false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void nameExceedsMaxSize_hasViolation() {
        var request = new CreatePolicyRequest("x".repeat(201), null,
                "/services/*", List.of(PolicyPermission.READ), false);
        Set<ConstraintViolation<CreatePolicyRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }
}
