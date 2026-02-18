package com.codeops.vault.controller;

import com.codeops.vault.dto.request.SealActionRequest;
import com.codeops.vault.dto.response.SealStatusResponse;
import com.codeops.vault.service.SealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for Vault seal/unseal lifecycle.
 *
 * <p>The status endpoint is PUBLIC for monitoring. All other endpoints
 * require ADMIN role. SealController does NOT use class-level
 * {@code @PreAuthorize} because the status endpoint must be accessible
 * without authentication.</p>
 */
@RestController
@RequestMapping("/api/v1/vault/seal")
@RequiredArgsConstructor
@Tag(name = "Seal", description = "Vault seal/unseal lifecycle management")
@Slf4j
public class SealController {

    private final SealService sealService;

    /**
     * Gets the current Vault seal status. Public endpoint — no authentication required.
     *
     * @return 200 with SealStatusResponse.
     */
    @GetMapping("/status")
    @Operation(summary = "Get Vault seal status (public — no auth required)")
    public ResponseEntity<SealStatusResponse> getStatus() {
        return ResponseEntity.ok(sealService.getStatus());
    }

    /**
     * Seals the Vault — blocks all secret operations.
     *
     * @return 200 with updated SealStatusResponse.
     */
    @PostMapping("/seal")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Seal the Vault")
    public ResponseEntity<SealStatusResponse> seal() {
        sealService.seal();
        return ResponseEntity.ok(sealService.getStatus());
    }

    /**
     * Submits a key share to unseal the Vault.
     *
     * @param request The seal action request containing the key share.
     * @return 200 with updated SealStatusResponse (may still be UNSEALING if more shares needed).
     */
    @PostMapping("/unseal")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Submit a key share to unseal the Vault")
    public ResponseEntity<SealStatusResponse> unseal(@RequestBody SealActionRequest request) {
        return ResponseEntity.ok(sealService.submitKeyShare(request.keyShare()));
    }

    /**
     * Generates key shares for distribution. Vault must be unsealed.
     *
     * @return 200 with shares array, total shares, and threshold.
     */
    @PostMapping("/generate-shares")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generate key shares for distribution")
    public ResponseEntity<Map<String, Object>> generateShares() {
        String[] shares = sealService.generateKeyShares();
        SealStatusResponse status = sealService.getStatus();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shares", shares);
        response.put("totalShares", status.totalShares());
        response.put("threshold", status.threshold());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets seal configuration info. Requires ADMIN.
     *
     * @return 200 with seal configuration map.
     */
    @GetMapping("/info")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get seal configuration info")
    public ResponseEntity<Map<String, Object>> getSealInfo() {
        return ResponseEntity.ok(sealService.getSealInfo());
    }
}
