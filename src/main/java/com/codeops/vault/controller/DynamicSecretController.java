package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateDynamicLeaseRequest;
import com.codeops.vault.dto.response.DynamicLeaseResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.DynamicSecretService;
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
 * REST controller for dynamic secret lease management.
 *
 * <p>Provides lease creation, retrieval, revocation, and statistics.
 * All endpoints require ADMIN role and an unsealed Vault.</p>
 */
@RestController
@RequestMapping("/api/v1/vault/dynamic")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Dynamic Secrets", description = "Lease-based dynamic credential management")
@Slf4j
public class DynamicSecretController {

    private final DynamicSecretService dynamicSecretService;
    private final SealService sealService;

    // ─── Lease CRUD ─────────────────────────────────────────

    /**
     * Creates a new dynamic secret lease.
     *
     * @param request The lease creation request with secretId and TTL.
     * @return 201 with the created DynamicLeaseResponse including connection details.
     */
    @PostMapping("/leases")
    @Operation(summary = "Create a new dynamic secret lease")
    public ResponseEntity<DynamicLeaseResponse> createLease(
            @Valid @RequestBody CreateDynamicLeaseRequest request) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(201).body(dynamicSecretService.createLease(request, userId));
    }

    /**
     * Gets a lease by its lease ID.
     *
     * @param leaseId The unique lease identifier string.
     * @return 200 with DynamicLeaseResponse (credentials not included).
     */
    @GetMapping("/leases/{leaseId}")
    @Operation(summary = "Get lease by lease ID")
    public ResponseEntity<DynamicLeaseResponse> getLease(@PathVariable String leaseId) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(dynamicSecretService.getLease(leaseId));
    }

    /**
     * Lists leases for a dynamic secret.
     *
     * @param secretId The source secret ID.
     * @param page     Page number (zero-based).
     * @param size     Page size.
     * @return 200 with paginated DynamicLeaseResponse list.
     */
    @GetMapping("/leases")
    @Operation(summary = "List leases for a dynamic secret")
    public ResponseEntity<PageResponse<DynamicLeaseResponse>> listLeases(
            @RequestParam UUID secretId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        sealService.requireUnsealed();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(dynamicSecretService.listLeases(secretId, pageable));
    }

    // ─── Revocation ─────────────────────────────────────────

    /**
     * Revokes an active lease.
     *
     * @param leaseId The lease identifier to revoke.
     * @return 204 No Content.
     */
    @PostMapping("/leases/{leaseId}/revoke")
    @Operation(summary = "Revoke an active lease")
    public ResponseEntity<Void> revokeLease(@PathVariable String leaseId) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        dynamicSecretService.revokeLease(leaseId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revokes all active leases for a secret.
     *
     * @param secretId The source secret ID.
     * @return 200 with revocation count.
     */
    @PostMapping("/leases/revoke-all")
    @Operation(summary = "Revoke all active leases for a secret")
    public ResponseEntity<Map<String, Integer>> revokeAllLeases(@RequestParam UUID secretId) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        int count = dynamicSecretService.revokeAllLeases(secretId, userId);
        return ResponseEntity.ok(Map.of("revoked", count));
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets lease statistics for a secret.
     *
     * @param secretId The source secret ID.
     * @return 200 with statistics map.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get lease statistics")
    public ResponseEntity<Map<String, Long>> getLeaseStats(@RequestParam UUID secretId) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(dynamicSecretService.getLeaseStats(secretId));
    }

    /**
     * Gets the total active lease count across all secrets.
     *
     * @return 200 with active lease count.
     */
    @GetMapping("/active-count")
    @Operation(summary = "Get total active lease count")
    public ResponseEntity<Map<String, Long>> getActiveCount() {
        sealService.requireUnsealed();
        return ResponseEntity.ok(Map.of("activeLeases", dynamicSecretService.getTotalActiveLeases()));
    }
}
