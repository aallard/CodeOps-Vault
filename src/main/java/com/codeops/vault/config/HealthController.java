package com.codeops.vault.config;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller providing a public health check endpoint for the Vault service.
 *
 * <p>This endpoint is excluded from JWT authentication requirements in
 * {@link com.codeops.vault.security.SecurityConfig} and can be used by load balancers,
 * monitoring systems, and deployment pipelines to verify service availability.</p>
 *
 * @see com.codeops.vault.security.SecurityConfig
 */
@RestController
@RequestMapping("/health")
@Tag(name = "Health")
public class HealthController {

    /**
     * Returns the current health status of the CodeOps Vault service.
     *
     * <p>The response includes the service status ({@code "UP"}), service name
     * ({@code "codeops-vault"}), and the current server timestamp in ISO-8601 format.</p>
     *
     * @return a 200 response containing a map with {@code status}, {@code service}, and
     *         {@code timestamp} keys
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "codeops-vault",
                "timestamp", Instant.now().toString()
        ));
    }
}
