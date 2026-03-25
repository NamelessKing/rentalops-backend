package com.rentalops.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for the RentalOps backend.
 *
 * <p>Registers the Bearer JWT security scheme globally so that Swagger UI
 * automatically sends the Authorization header to every protected endpoint.
 * All metadata (title, version, contact, license) is declared here so that
 * consumers have a single place to update it.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    OpenAPI rentalOpsOpenApi() {
        return new OpenAPI()
                .info(buildInfo())
                .externalDocs(new ExternalDocumentation()
                        .description("RentalOps project documentation")
                        .url("https://github.com/rentalops/rentalops-backend"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Local development server"))
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a JWT obtained from POST /auth/login."
                                        + " Prefix is added automatically by Swagger UI.")
                ))
                // Applies the Bearer security requirement to every endpoint globally.
                // Individual public endpoints override this with an empty list if needed.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
    }

    private Info buildInfo() {
        return new Info()
                .title("RentalOps API")
                .version("v1")
                .description("Multi-tenant property task management SaaS backend."
                        + " Supports admin onboarding, operator team management,"
                        + " property setup, task execution flow, issue reporting and"
                        + " dashboard summary.")
                .contact(new Contact()
                        .name("RentalOps Team")
                        .url("https://github.com/rentalops"))
                .license(new License()
                        .name("Private — All rights reserved"));
    }
}
