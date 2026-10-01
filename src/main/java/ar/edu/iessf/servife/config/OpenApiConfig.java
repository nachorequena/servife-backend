package ar.edu.iessf.servife.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI en /api/v1/swagger-ui.html, con el botón Authorize para el Bearer. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info().title("Servife API").version("v1")
                .description("Contrato en servife-ia/.ai/05-api-contract.md"))
            .components(new Components().addSecuritySchemes(BEARER,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
