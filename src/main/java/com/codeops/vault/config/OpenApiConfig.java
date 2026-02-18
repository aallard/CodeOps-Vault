package com.codeops.vault.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI (Swagger) specification metadata for the CodeOps Vault API.
 *
 * <p>Sets the API title, description, version, server URL, and JWT bearer authentication
 * scheme. SpringDoc auto-discovers all {@code @RestController} endpoints and merges
 * them with this configuration to produce the final OpenAPI spec.</p>
 *
 * @see <a href="http://localhost:8097/swagger-ui.html">Swagger UI</a>
 * @see <a href="http://localhost:8097/v3/api-docs.yaml">OpenAPI YAML</a>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Builds the OpenAPI specification with project metadata and security configuration.
     *
     * @return the configured OpenAPI instance
     */
    @Bean
    public OpenAPI vaultOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeOps Vault API")
                        .description("Secrets management service for the CodeOps platform. "
                                + "Provides encrypted storage and retrieval of sensitive configuration values, "
                                + "credentials, and API keys with fine-grained access control.")
                        .version("1.0.0")
                        .contact(new Contact().name("CodeOps Team")))
                .servers(List.of(new Server()
                        .url("http://localhost:8097")
                        .description("Local development")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
