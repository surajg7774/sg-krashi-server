package com.sgkrashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Loads the real application.yml / application-{profile}.yml through Spring Boot's own
 * config loading (no beans, no database) and checks the springdoc switches per profile:
 * the OpenAPI document and Swagger UI are public and list every admin endpoint, so
 * they must be off in production and still available for development.
 */
class ProdApiDocsDisabledTest {

    @Configuration
    static class EmptyConfig {
    }

    private Environment environmentFor(String profile, ConfigurableApplicationContext[] holder) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off");
        if (profile != null) {
            builder.profiles(profile);
        }
        holder[0] = builder.run();
        return holder[0].getEnvironment();
    }

    @Test
    void productionTurnsOffTheApiDocsAndSwaggerUi() {
        ConfigurableApplicationContext[] ctx = new ConfigurableApplicationContext[1];
        try {
            Environment env = environmentFor("prod", ctx);
            assertEquals("false", env.getProperty("springdoc.api-docs.enabled"));
            assertEquals("false", env.getProperty("springdoc.swagger-ui.enabled"));
        } finally {
            ctx[0].close();
        }
    }

    @Test
    void developmentKeepsThemOn() {
        for (String profile : new String[]{null, "local"}) {
            ConfigurableApplicationContext[] ctx = new ConfigurableApplicationContext[1];
            try {
                Environment env = environmentFor(profile, ctx);
                assertNotEquals("false", env.getProperty("springdoc.api-docs.enabled"), "profile " + profile);
                assertNotEquals("false", env.getProperty("springdoc.swagger-ui.enabled"), "profile " + profile);
                assertEquals("/swagger-ui.html", env.getProperty("springdoc.swagger-ui.path"));
            } finally {
                ctx[0].close();
            }
        }
    }
}
