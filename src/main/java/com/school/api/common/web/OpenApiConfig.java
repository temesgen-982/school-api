package com.school.api.common.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Static OpenAPI metadata served at {@code /v3/api-docs}, with the Swagger UI
 * at {@code /swagger-ui.html}. Both are public (see {@code SecurityConfig});
 * every endpoint they describe still enforces JWT authentication.
 */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "School API",
                        version = "v1",
                        description = "Multi-tenant school management API. Authenticate via "
                                + "/api/v1/auth/login and send the returned token as "
                                + "`Authorization: Bearer <token>`."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {}
