package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateRotationPolicyRequest;
import com.codeops.vault.dto.request.UpdateRotationPolicyRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.RotationHistoryResponse;
import com.codeops.vault.dto.response.RotationPolicyResponse;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.RotationService;
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

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for secret rotation policy management and execution.
 *
 * <p>Provides CRUD for rotation policies, manual rotation triggering,
 * rotation history retrieval, and statistics. All endpoints require
 * ADMIN role and an unsealed Vault.</p>
 */
@RestController
@RequestMapping("/api/v1/vault/rotation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Rotation", description = "Secret rotation management")
@Slf4j
public class RotationController {

    private final RotationService rotationService;
    private final SealService sealService;

    // ─── Policy CRUD ────────────────────────────────────────

    /**
     * Creates or updates a rotation policy for a secret.
     *
     * @param request The rotation policy request with strategy and parameters.
     * @return 201 with the created/updated RotationPolicyResponse.
     */
    @PostMapping("/policies")
    @Operation(summary = "Create or update a rotation policy for a secret")
    public ResponseEntity<RotationPolicyResponse> createOrUpdatePolicy(
            @Valid @RequestBody CreateRotationPolicyRequest request) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(201).body(rotationService.createOrUpdatePolicy(request, userId));
    }

    /**
     * Gets the rotation policy for a secret.
     *
     * @param secretId The secret ID.
     * @return 200 with RotationPolicyResponse.
     */
    @GetMapping("/policies/{secretId}")
    @Operation(summary = "Get rotation policy for a secret")
    public ResponseEntity<RotationPolicyResponse> getPolicy(@PathVariable UUID secretId) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(rotationService.getPolicy(secretId));
    }

    /**
     * Updates an existing rotation policy.
     *
     * @param policyId The rotation policy ID.
     * @param request  The update request with optional fields.
     * @return 200 with updated RotationPolicyResponse.
     */
    @PutMapping("/policies/{policyId}")
    @Operation(summary = "Update a rotation policy")
    public ResponseEntity<RotationPolicyResponse> updatePolicy(
            @PathVariable UUID policyId, @Valid @RequestBody UpdateRotationPolicyRequest request) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(rotationService.updatePolicy(policyId, request));
    }

    /**
     * Deletes a rotation policy.
     *
     * @param policyId The rotation policy ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/policies/{policyId}")
    @Operation(summary = "Delete a rotation policy")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID policyId) {
        sealService.requireUnsealed();
        rotationService.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }

    // ─── Rotation Execution ─────────────────────────────────

    /**
     * Triggers manual rotation for a secret.
     *
     * @param secretId The secret ID to rotate.
     * @return 200 with RotationHistoryResponse for this rotation attempt.
     */
    @PostMapping("/rotate/{secretId}")
    @Operation(summary = "Trigger manual rotation for a secret")
    public ResponseEntity<RotationHistoryResponse> rotateSecret(@PathVariable UUID secretId) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(rotationService.rotateSecret(secretId, userId));
    }

    // ─── History ────────────────────────────────────────────

    /**
     * Gets rotation history for a secret.
     *
     * @param secretId The secret ID.
     * @param page     Page number (zero-based).
     * @param size     Page size.
     * @return 200 with paginated RotationHistoryResponse list.
     */
    @GetMapping("/history/{secretId}")
    @Operation(summary = "Get rotation history for a secret")
    public ResponseEntity<PageResponse<RotationHistoryResponse>> getHistory(
            @PathVariable UUID secretId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        sealService.requireUnsealed();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(rotationService.getRotationHistory(secretId, pageable));
    }

    /**
     * Gets the last successful rotation for a secret.
     *
     * @param secretId The secret ID.
     * @return 200 with RotationHistoryResponse, or 404 if never rotated.
     */
    @GetMapping("/history/{secretId}/last")
    @Operation(summary = "Get last successful rotation")
    public ResponseEntity<RotationHistoryResponse> getLastSuccessful(@PathVariable UUID secretId) {
        sealService.requireUnsealed();
        RotationHistoryResponse response = rotationService.getLastSuccessfulRotation(secretId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets rotation statistics for a secret.
     *
     * @param secretId The secret ID.
     * @return 200 with statistics map.
     */
    @GetMapping("/stats/{secretId}")
    @Operation(summary = "Get rotation statistics")
    public ResponseEntity<Map<String, Long>> getStats(@PathVariable UUID secretId) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(rotationService.getRotationStats(secretId));
    }
}
