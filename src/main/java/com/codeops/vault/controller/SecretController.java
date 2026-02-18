package com.codeops.vault.controller;

import com.codeops.vault.dto.request.CreateSecretRequest;
import com.codeops.vault.dto.request.UpdateSecretRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.SecretResponse;
import com.codeops.vault.dto.response.SecretValueResponse;
import com.codeops.vault.dto.response.SecretVersionResponse;
import com.codeops.vault.entity.enums.SecretType;
import com.codeops.vault.security.SecurityUtils;
import com.codeops.vault.service.SealService;
import com.codeops.vault.service.SecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for secret lifecycle management.
 *
 * <p>Provides CRUD operations for secrets, version management,
 * value retrieval (decrypt), metadata management, and path listing.
 * All endpoints require ADMIN role.</p>
 *
 * <p>Secret values (decrypted) are ONLY returned from explicit
 * value-read endpoints ({@code GET /{id}/value} and
 * {@code GET /{id}/versions/{version}/value}). All other endpoints
 * return metadata only.</p>
 */
@RestController
@RequestMapping("/api/v1/vault/secrets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Secrets", description = "Secret lifecycle management")
@Slf4j
public class SecretController {

    private final SecretService secretService;
    private final SealService sealService;

    // ─── CRUD ───────────────────────────────────────────────

    /**
     * Creates a new secret.
     *
     * @param request The creation request with path, name, value, type, etc.
     * @return 201 with the created SecretResponse.
     */
    @PostMapping
    @Operation(summary = "Create a new secret")
    public ResponseEntity<SecretResponse> createSecret(@Valid @RequestBody CreateSecretRequest request) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        UUID userId = SecurityUtils.getCurrentUserId();
        SecretResponse response = secretService.createSecret(request, teamId, userId);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Gets secret metadata by ID.
     *
     * @param id The secret ID.
     * @return 200 with SecretResponse.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get secret metadata by ID")
    public ResponseEntity<SecretResponse> getSecret(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(secretService.getSecretById(id));
    }

    /**
     * Gets secret metadata by path.
     *
     * @param path The hierarchical secret path.
     * @return 200 with SecretResponse.
     */
    @GetMapping("/by-path")
    @Operation(summary = "Get secret metadata by path")
    public ResponseEntity<SecretResponse> getSecretByPath(@RequestParam String path) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(secretService.getSecretByPath(teamId, path));
    }

    /**
     * Reads (decrypts) the current version of a secret's value.
     *
     * @param id The secret ID.
     * @return 200 with SecretValueResponse containing the decrypted value.
     */
    @GetMapping("/{id}/value")
    @Operation(summary = "Read decrypted secret value (current version)")
    public ResponseEntity<SecretValueResponse> readSecretValue(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(secretService.readSecretValue(id));
    }

    /**
     * Reads (decrypts) a specific version of a secret's value.
     *
     * @param id      The secret ID.
     * @param version The version number.
     * @return 200 with SecretValueResponse.
     */
    @GetMapping("/{id}/versions/{version}/value")
    @Operation(summary = "Read decrypted secret value for a specific version")
    public ResponseEntity<SecretValueResponse> readSecretVersionValue(
            @PathVariable UUID id, @PathVariable int version) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(secretService.readSecretVersionValue(id, version));
    }

    /**
     * Updates a secret (new version if value provided, and/or metadata changes).
     *
     * @param id      The secret ID.
     * @param request The update request.
     * @return 200 with updated SecretResponse.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update secret metadata and/or create new version")
    public ResponseEntity<SecretResponse> updateSecret(
            @PathVariable UUID id, @Valid @RequestBody UpdateSecretRequest request) {
        sealService.requireUnsealed();
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(secretService.updateSecret(id, request, userId));
    }

    /**
     * Soft-deletes a secret (sets inactive).
     *
     * @param id The secret ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a secret (set inactive)")
    public ResponseEntity<Void> softDeleteSecret(@PathVariable UUID id) {
        sealService.requireUnsealed();
        secretService.softDeleteSecret(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Permanently deletes a secret and all versions.
     *
     * @param id The secret ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete a secret and all versions")
    public ResponseEntity<Void> hardDeleteSecret(@PathVariable UUID id) {
        sealService.requireUnsealed();
        secretService.hardDeleteSecret(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Listing ────────────────────────────────────────────

    /**
     * Lists secrets with optional filters and pagination.
     *
     * @param type       Optional secret type filter.
     * @param pathPrefix Optional path prefix filter.
     * @param activeOnly Whether to return only active secrets.
     * @param page       Page number (zero-based).
     * @param size       Page size.
     * @param sortBy     Sort field.
     * @param sortDir    Sort direction (asc/desc).
     * @return 200 with paginated SecretResponse list.
     */
    @GetMapping
    @Operation(summary = "List secrets with optional filters")
    public ResponseEntity<PageResponse<SecretResponse>> listSecrets(
            @RequestParam(required = false) SecretType type,
            @RequestParam(required = false) String pathPrefix,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return ResponseEntity.ok(secretService.listSecrets(teamId, type, pathPrefix, activeOnly, pageable));
    }

    /**
     * Searches secrets by name.
     *
     * @param q    Search query.
     * @param page Page number.
     * @param size Page size.
     * @return 200 with paginated search results.
     */
    @GetMapping("/search")
    @Operation(summary = "Search secrets by name")
    public ResponseEntity<PageResponse<SecretResponse>> searchSecrets(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(secretService.searchSecrets(teamId, q, pageable));
    }

    /**
     * Lists secret paths under a prefix.
     *
     * @param prefix The path prefix to list under.
     * @return 200 with list of paths.
     */
    @GetMapping("/paths")
    @Operation(summary = "List secret paths under a prefix")
    public ResponseEntity<List<String>> listPaths(@RequestParam(defaultValue = "/") String prefix) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(secretService.listPaths(teamId, prefix));
    }

    // ─── Version Management ─────────────────────────────────

    /**
     * Lists versions of a secret.
     *
     * @param id   The secret ID.
     * @param page Page number.
     * @param size Page size.
     * @return 200 with paginated version list.
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "List secret versions")
    public ResponseEntity<PageResponse<SecretVersionResponse>> listVersions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        sealService.requireUnsealed();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(secretService.listVersions(id, pageable));
    }

    /**
     * Gets a specific version's metadata.
     *
     * @param id      The secret ID.
     * @param version The version number.
     * @return 200 with SecretVersionResponse.
     */
    @GetMapping("/{id}/versions/{version}")
    @Operation(summary = "Get version metadata")
    public ResponseEntity<SecretVersionResponse> getVersion(
            @PathVariable UUID id, @PathVariable int version) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(secretService.getVersion(id, version));
    }

    /**
     * Destroys a secret version (irreversible).
     *
     * @param id      The secret ID.
     * @param version The version number.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}/versions/{version}")
    @Operation(summary = "Destroy a secret version (irreversible)")
    public ResponseEntity<Void> destroyVersion(
            @PathVariable UUID id, @PathVariable int version) {
        sealService.requireUnsealed();
        secretService.destroyVersion(id, version);
        return ResponseEntity.noContent().build();
    }

    // ─── Metadata ───────────────────────────────────────────

    /**
     * Gets all metadata key-value pairs for a secret.
     *
     * @param id The secret ID.
     * @return 200 with metadata map.
     */
    @GetMapping("/{id}/metadata")
    @Operation(summary = "Get secret metadata key-value pairs")
    public ResponseEntity<Map<String, String>> getMetadata(@PathVariable UUID id) {
        sealService.requireUnsealed();
        return ResponseEntity.ok(secretService.getMetadata(id));
    }

    /**
     * Sets a metadata key-value pair.
     *
     * @param id    The secret ID.
     * @param key   The metadata key.
     * @param value The metadata value.
     * @return 204 No Content.
     */
    @PutMapping("/{id}/metadata/{key}")
    @Operation(summary = "Set a metadata key-value pair")
    public ResponseEntity<Void> setMetadata(
            @PathVariable UUID id, @PathVariable String key, @RequestBody String value) {
        sealService.requireUnsealed();
        secretService.setMetadata(id, key, value);
        return ResponseEntity.noContent().build();
    }

    /**
     * Removes a metadata key.
     *
     * @param id  The secret ID.
     * @param key The metadata key to remove.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}/metadata/{key}")
    @Operation(summary = "Remove a metadata key")
    public ResponseEntity<Void> removeMetadata(@PathVariable UUID id, @PathVariable String key) {
        sealService.requireUnsealed();
        secretService.removeMetadata(id, key);
        return ResponseEntity.noContent().build();
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets secret counts by type.
     *
     * @return 200 with statistics map.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get secret statistics")
    public ResponseEntity<Map<String, Long>> getStats() {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(secretService.getSecretCounts(teamId));
    }

    /**
     * Gets secrets expiring within a time window.
     *
     * @param withinHours Hours to look ahead.
     * @return 200 with list of expiring secrets.
     */
    @GetMapping("/expiring")
    @Operation(summary = "Get secrets expiring within a time window")
    public ResponseEntity<List<SecretResponse>> getExpiringSecrets(
            @RequestParam(defaultValue = "24") int withinHours) {
        sealService.requireUnsealed();
        UUID teamId = SecurityUtils.getCurrentTeamId();
        return ResponseEntity.ok(secretService.getExpiringSecrets(teamId, withinHours));
    }
}
