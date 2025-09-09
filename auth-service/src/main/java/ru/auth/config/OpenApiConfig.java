package ru.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@OpenAPIDefinition(
    info = @Info(title = "Auth Service API", version = "v1", description = "Документация Auth-сервиса"),
    security = { @SecurityRequirement(name = "bearerAuth") }
)
public class OpenApiConfig {

	@Bean
	public OpenAPI api() {
		return new OpenAPI()
				.info(new io.swagger.v3.oas.models.info.Info()
						.title("Auth Service API")
						.version("v1")
						.description("Документация Auth-сервиса")
						.license(new License().name("MIT"))
						.contact(new Contact().name("Team")))
				.servers(List.of(
					new Server().url("/").description("Root via Gateway")
				));
	}
}



