package com.engineerhub.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Static API metadata for the generated OpenAPI contract (contract file:
 * openapi/engineerhub-v1.yaml).
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  @Bean
  OpenAPI engineerHubOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("EngineerHub API")
                .version("v1")
                .description("Public REST API of the EngineerHub platform."));
  }
}
