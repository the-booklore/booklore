package com.adityachandel.booklore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Booklore API")
                        .version("1.0")
                        .description("Booklore is a personal library management system. This API allows you to manage books, libraries, users, shelves, and more.")
                        .contact(new Contact()
                                .name("Booklore")
                                .url("https://github.com/booklore-app/booklore"))
                        .license(new License()
                                .name("GNU General Public License v3.0")
                                .url("https://www.gnu.org/licenses/gpl-3.0.html"))
                )
                .addServersItem(new Server().url("/").description("Current server"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
