package com.sgkrashi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata for the SG Krashi platform.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sgKrashiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SG Krashi API")
                        .version("1.0")
                        .description("API documentation for SG Krashi, a modular monolith agriculture "
                                + "platform composed of independently developed but co-deployed modules."));
    }
}
