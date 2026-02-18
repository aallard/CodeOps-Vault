package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateTransitKeyRequest;
import com.codeops.vault.dto.request.TransitDecryptRequest;
import com.codeops.vault.dto.request.TransitEncryptRequest;
import com.codeops.vault.dto.request.TransitRewrapRequest;
import com.codeops.vault.dto.request.UpdateTransitKeyRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.TransitDecryptResponse;
import com.codeops.vault.dto.response.TransitEncryptResponse;
import com.codeops.vault.dto.response.TransitKeyResponse;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.SealService;
import com.codeops.vault.service.TransitService;
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
 * REST controller for transit encryption (encryption-as-a-service).
 *
 * <p>Provides named key management, encrypt/decrypt/rewrap operations,
 * data key generation, and statistics. All endpoints require ADMIN role
 * and an unsealed Vault.</p>
 */
@RestController
@RequestMapping("/api/v1/vault/transit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Transit", description = "Encryption-as-a-service with named keys")
@Slf4j
public class TransitController {

    private final TransitService transitService;
    private final SealService sealService;

    // ─── Key Management ─────────────────────────────────────

    /**
     * Creates a named transit encryption key.
     *
     * @param request The creation request with name, algorithm, and flags.
     * @return 201 with the created TransitKeyResponse.
     */
    @PostMapping("/keys")
    @Operation(summary = "Create a named transit encryption key")
    public ResponseEntity<TransitKeyResponse> createKey(
            @Valid @RequestBody CreateTransitKeyRequest request) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(201).body(transitService.createKey(request, teamId, userId));
    }

    /**
     * Gets a transit key by ID.
     *
     * @param id The transit key ID.
     * @return 200 with TransitKeyResponse.
     */
    @GetMapping("/keys/{id}")
    @Operation(summary = "Get transit key by ID")
    public ResponseEntity<TransitKeyResponse> getKeyById(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(transitService.getKeyById(id));
    }

    /**
     * Gets a transit key by name.
     *
     * @param name The key name.
     * @return 200 with TransitKeyResponse.
     */
    @GetMapping("/keys/by-name")
    @Operation(summary = "Get transit key by name")
    public ResponseEntity<TransitKeyResponse> getKeyByName(@RequestParam String name) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(transitService.getKeyByName(teamId, name));
    }

    /**
     * Lists transit keys with optional active filter and pagination.
     *
     * @param activeOnly Whether to return only active keys.
     * @param page       Page number (zero-based).
     * @param size       Page size.
     * @return 200 with paginated TransitKeyResponse list.
     */
    @GetMapping("/keys")
    @Operation(summary = "List transit keys")
    public ResponseEntity<PageResponse<TransitKeyResponse>> listKeys(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(transitService.listKeys(teamId, activeOnly, pageable));
    }

    /**
     * Updates transit key metadata.
     *
     * @param id      The transit key ID.
     * @param request The update request with optional fields.
     * @return 200 with updated TransitKeyResponse.
     */
    @PutMapping("/keys/{id}")
    @Operation(summary = "Update transit key metadata")
    public ResponseEntity<TransitKeyResponse> updateKey(
            @PathVariable UUID id, @Valid @RequestBody UpdateTransitKeyRequest request) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(transitService.updateKey(id, request));
    }

    /**
     * Rotates a transit key (adds a new version).
     *
     * @param id The transit key ID.
     * @return 200 with updated TransitKeyResponse showing new version.
     */
    @PostMapping("/keys/{id}/rotate")
    @Operation(summary = "Rotate transit key (add new version)")
    public ResponseEntity<TransitKeyResponse> rotateKey(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(transitService.rotateKey(id));
    }

    /**
     * Deletes a transit key (must be deletable).
     *
     * @param id The transit key ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/keys/{id}")
    @Operation(summary = "Delete transit key (must be deletable)")
    public ResponseEntity<Void> deleteKey(@PathVariable UUID id) {
        sealService.requireUnsealed();
        transitService.deleteKey(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Encrypt / Decrypt ──────────────────────────────────

    /**
     * Encrypts plaintext with a named transit key.
     *
     * @param request The encryption request with key name and plaintext.
     * @return 200 with TransitEncryptResponse containing ciphertext.
     */
    @PostMapping("/encrypt")
    @Operation(summary = "Encrypt data with a named transit key")
    public ResponseEntity<TransitEncryptResponse> encrypt(
            @Valid @RequestBody TransitEncryptRequest request) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(transitService.encrypt(request, teamId));
    }

    /**
     * Decrypts ciphertext with a named transit key.
     *
     * @param request The decryption request with key name and ciphertext.
     * @return 200 with TransitDecryptResponse containing plaintext.
     */
    @PostMapping("/decrypt")
    @Operation(summary = "Decrypt data with a named transit key")
    public ResponseEntity<TransitDecryptResponse> decrypt(
            @Valid @RequestBody TransitDecryptRequest request) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(transitService.decrypt(request, teamId));
    }

    /**
     * Re-encrypts ciphertext with the current key version.
     *
     * @param request The rewrap request with key name and existing ciphertext.
     * @return 200 with TransitEncryptResponse containing rewrapped ciphertext.
     */
    @PostMapping("/rewrap")
    @Operation(summary = "Re-encrypt ciphertext with current key version")
    public ResponseEntity<TransitEncryptResponse> rewrap(
            @Valid @RequestBody TransitRewrapRequest request) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(transitService.rewrap(request, teamId));
    }

    /**
     * Generates a data encryption key wrapped with a named transit key.
     *
     * @param keyName The transit key name to wrap with.
     * @return 200 with plaintext key and ciphertext key.
     */
    @PostMapping("/datakey")
    @Operation(summary = "Generate a data encryption key")
    public ResponseEntity<Map<String, String>> generateDataKey(@RequestParam String keyName) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(transitService.generateDataKey(keyName, teamId));
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets transit key statistics for the team.
     *
     * @return 200 with statistics map.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get transit key statistics")
    public ResponseEntity<Map<String, Long>> getKeyStats() {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(transitService.getKeyStats(teamId));
    }
}
