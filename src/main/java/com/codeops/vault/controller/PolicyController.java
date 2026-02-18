package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateBindingRequest;
import com.codeops.vault.dto.request.CreatePolicyRequest;
import com.codeops.vault.dto.request.UpdatePolicyRequest;
import com.codeops.vault.dto.response.AccessPolicyResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.PolicyBindingResponse;
import com.codeops.vault.entity.enums.BindingType;
import com.codeops.vault.entity.enums.PolicyPermission;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.AccessDecision;
import com.codeops.vault.service.PolicyService;
import com.codeops.vault.service.SealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for access policy and binding management.
 *
 * <p>Provides CRUD for policies and bindings, plus access evaluation.
 * All endpoints require ADMIN role.</p>
 */
@RestController
@RequestMapping("/api/v1/vault/policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Policies", description = "Access policy management")
@Slf4j
public class PolicyController {

    private final PolicyService policyService;
    private final SealService sealService;

    // ─── Policy CRUD ────────────────────────────────────────

    /**
     * Creates a new access policy.
     *
     * @param request The creation request with name, path pattern, permissions.
     * @return 201 with the created AccessPolicyResponse.
     */
    @PostMapping
    @Operation(summary = "Create an access policy")
    public ResponseEntity<AccessPolicyResponse> createPolicy(
            @Valid @RequestBody CreatePolicyRequest request) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(201).body(policyService.createPolicy(request, teamId, userId));
    }

    /**
     * Gets a policy by ID.
     *
     * @param id The policy ID.
     * @return 200 with AccessPolicyResponse.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get policy by ID")
    public ResponseEntity<AccessPolicyResponse> getPolicy(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(policyService.getPolicyById(id));
    }

    /**
     * Lists policies with pagination and optional active filter.
     *
     * @param activeOnly Whether to return only active policies.
     * @param page       Page number (zero-based).
     * @param size       Page size.
     * @return 200 with paginated AccessPolicyResponse list.
     */
    @GetMapping
    @Operation(summary = "List policies")
    public ResponseEntity<PageResponse<AccessPolicyResponse>> listPolicies(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(policyService.listPolicies(teamId, activeOnly, pageable));
    }

    /**
     * Updates an access policy.
     *
     * @param id      The policy ID.
     * @param request The update request with optional fields.
     * @return 200 with updated AccessPolicyResponse.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an access policy")
    public ResponseEntity<AccessPolicyResponse> updatePolicy(
            @PathVariable UUID id, @Valid @RequestBody UpdatePolicyRequest request) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(policyService.updatePolicy(id, request));
    }

    /**
     * Deletes a policy and all its bindings.
     *
     * @param id The policy ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete policy and all bindings")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
        sealService.requireUnsealed();
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Binding CRUD ───────────────────────────────────────

    /**
     * Creates a policy binding.
     *
     * @param request The binding request with policy ID, type, and target ID.
     * @return 201 with PolicyBindingResponse.
     */
    @PostMapping("/bindings")
    @Operation(summary = "Create a policy binding")
    public ResponseEntity<PolicyBindingResponse> createBinding(
            @Valid @RequestBody CreateBindingRequest request) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(201).body(policyService.createBinding(request, userId));
    }

    /**
     * Lists bindings for a specific policy.
     *
     * @param id The policy ID.
     * @return 200 with list of PolicyBindingResponse.
     */
    @GetMapping("/{id}/bindings")
    @Operation(summary = "List bindings for a policy")
    public ResponseEntity<List<PolicyBindingResponse>> listBindingsForPolicy(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(policyService.listBindingsForPolicy(id));
    }

    /**
     * Lists bindings for a specific target (user, team, or service).
     *
     * @param type     The binding type (USER, TEAM, SERVICE).
     * @param targetId The target entity ID.
     * @return 200 with list of PolicyBindingResponse.
     */
    @GetMapping("/bindings/target")
    @Operation(summary = "List bindings for a target")
    public ResponseEntity<List<PolicyBindingResponse>> listBindingsForTarget(
            @RequestParam BindingType type, @RequestParam UUID targetId) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(policyService.listBindingsForTarget(type, targetId));
    }

    /**
     * Deletes a binding.
     *
     * @param id The binding ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/bindings/{id}")
    @Operation(summary = "Delete a binding")
    public ResponseEntity<Void> deleteBinding(@PathVariable UUID id) {
        sealService.requireUnsealed();
        policyService.deleteBinding(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Access Evaluation ──────────────────────────────────

    /**
     * Evaluates access for a user on a secret path.
     *
     * @param userId     The user ID to evaluate.
     * @param path       The secret path.
     * @param permission The permission to check.
     * @return 200 with AccessDecision.
     */
    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate access for a user on a path")
    public ResponseEntity<AccessDecision> evaluateAccess(
            @RequestParam UUID userId,
            @RequestParam String path,
            @RequestParam PolicyPermission permission) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(policyService.evaluateAccess(userId, teamId, path, permission));
    }

    /**
     * Evaluates access for a service on a secret path.
     *
     * @param serviceId  The service ID to evaluate.
     * @param path       The secret path.
     * @param permission The permission to check.
     * @return 200 with AccessDecision.
     */
    @PostMapping("/evaluate/service")
    @Operation(summary = "Evaluate access for a service on a path")
    public ResponseEntity<AccessDecision> evaluateServiceAccess(
            @RequestParam UUID serviceId,
            @RequestParam String path,
            @RequestParam PolicyPermission permission) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(policyService.evaluateServiceAccess(serviceId, teamId, path, permission));
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets policy statistics.
     *
     * @return 200 with statistics map.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get policy statistics")
    public ResponseEntity<Map<String, Long>> getPolicyStats() {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(policyService.getPolicyCounts(teamId));
    }
}
