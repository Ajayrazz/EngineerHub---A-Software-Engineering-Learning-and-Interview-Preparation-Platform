package com.engineerhub.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.engineerhub.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Logs are structured JSON (ECS) and carry the request's correlation id, without leaking secrets.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIT {

  @Autowired MockMvc mvc;

  @Test
  void errorLogsAreJsonWithTheCorrelationId(CapturedOutput output) throws Exception {
    mvc.perform(get("/test-support/boom").header("X-Correlation-Id", "trace-me-0001-xyz"));

    String line =
        output
            .getAll()
            .lines()
            .filter(
                l -> l.contains("Unhandled exception on GET") && l.contains("trace-me-0001-xyz"))
            .findFirst()
            .orElse("");
    assertThat(line).as("an ECS JSON log line was emitted").startsWith("{").endsWith("}");
    assertThat(line).contains("\"correlationId\":\"trace-me-0001-xyz\"");
    assertThat(line).contains("\"log\":{\"level\":\"ERROR\"");
    assertThat(line).contains("\"service\":{\"name\":\"engineerhub-api\"}");
  }
}
