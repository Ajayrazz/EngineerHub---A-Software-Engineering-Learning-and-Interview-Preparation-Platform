package com.engineerhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.engineerhub.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API-contract gate: the OpenAPI document generated from the code must equal the committed {@code
 * openapi/engineerhub-v1.yaml}. The frontend TypeScript client is generated from that file, so an
 * API change that is not reflected in it fails the build.
 *
 * <p>To accept an intentional API change run: {@code make api-update}.
 */
@IntegrationTest
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiContractIT {

  private static final Path COMMITTED = Path.of("..", "openapi", "engineerhub-v1.yaml");

  @Autowired MockMvc mvc;

  @Test
  void generatedSpecMatchesCommittedSpec() throws Exception {
    String generated =
        normalize(
            mvc.perform(get("/v3/api-docs.yaml"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    assertThat(generated).contains("openapi:").contains("/api/v1/system/ping");
    assertThat(generated)
        .as("test-only endpoints must not leak into the contract")
        .doesNotContain("test-support");

    if (Boolean.getBoolean("openapi.update")) {
      Files.createDirectories(COMMITTED.getParent());
      Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
      return;
    }
    assertThat(COMMITTED).as("committed OpenAPI spec missing — run `make api-update`").exists();
    assertThat(generated)
        .as("API changed but openapi/engineerhub-v1.yaml was not updated — run `make api-update`")
        .isEqualTo(normalize(Files.readString(COMMITTED, StandardCharsets.UTF_8)));
  }

  @Test
  void swaggerUiStaysOffUnlessExplicitlyEnabled() throws Exception {
    assertThat(mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus())
        .isEqualTo(404);
  }

  private static String normalize(String yaml) {
    return yaml.replace("\r\n", "\n").stripTrailing() + "\n";
  }
}
