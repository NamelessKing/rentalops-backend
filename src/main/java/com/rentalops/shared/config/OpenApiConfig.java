package com.rentalops.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI rentalOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RentalOps API")
                        .version("v1")
                        .description("Backend API for the RentalOps MVP."));
    }
}
