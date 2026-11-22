package com.engineerhub.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Every error path must yield RFC 9457 problem+json with a stable code and leak nothing internal.
 */
@IntegrationTest
class ErrorModelIT {

  @Autowired MockMvc mvc;

  private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

  @Test
  void apiExceptionBecomesProblemWithStableCode() throws Exception {
    mvc.perform(get("/test-support/api-exception"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.type").value("urn:engineerhub:problem:not-found"))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Course not found."))
        .andExpect(jsonPath("$.instance").value("/test-support/api-exception"))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void validationFailureListsFieldsButNeverEchoesRejectedValues() throws Exception {
    String body = "{\"name\":\"\",\"age\":-987654,\"secretCode\":\"hunter2\"}";
    MvcResult result =
        mvc.perform(
                post("/test-support/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.length()").value(3))
            .andReturn();
    String json = result.getResponse().getContentAsString();
    assertThat(json).contains("name", "age", "secretCode");
    assertThat(json).doesNotContain("hunter2").doesNotContain("987654");
  }

  @Test
  void unreadableBodyIsAGenericMalformedRequest() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/test-support/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{bad json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.detail").value("The request body could not be read."))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("Unexpected")
        .doesNotContain("jackson")
        .doesNotContain("JsonParse");
  }

  @Test
  void unexpectedExceptionsAreOpaque500s() throws Exception {
    MvcResult result =
        mvc.perform(get("/test-support/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty())
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("secret-internal-detail")
        .doesNotContain("hunter2")
        .doesNotContain("jdbc:")
        .doesNotContain("IllegalStateException")
        .doesNotContain("at com.engineerhub")
        .doesNotContain("stackTrace");
  }

  @Test
  void unknownPathIsA404Problem() throws Exception {
    mvc.perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void wrongMethodIsA405Problem() throws Exception {
    mvc.perform(post("/api/v1/system/ping"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void wrongContentTypeIsA415Problem() throws Exception {
    mvc.perform(post("/test-support/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  // ---- method-security denial raised inside a controller ----

  @Test
  void methodSecurityDenialWithoutLoginIs401() throws Exception {
    mvc.perform(get("/test-support/admin-only"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void methodSecurityDenialWithWrongRoleIs403() throws Exception {
    mvc.perform(get("/test-support/admin-only").with(jwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  // ---- correlation id ----

  @Test
  void inboundCorrelationIdIsEchoedInHeaderAndBody() throws Exception {
    mvc.perform(get("/test-support/api-exception").header("X-Correlation-Id", "web-req-0001-abcd"))
        .andExpect(header().string("X-Correlation-Id", "web-req-0001-abcd"))
        .andExpect(jsonPath("$.correlationId").value("web-req-0001-abcd"));
  }

  @Test
  void invalidInboundCorrelationIdIsReplaced() throws Exception {
    MvcResult r =
        mvc.perform(
                get("/test-support/api-exception")
                    .header("X-Correlation-Id", "bad id with spaces!!"))
            .andReturn();
    assertThat(r.getResponse().getHeader("X-Correlation-Id"))
        .isNotEqualTo("bad id with spaces!!")
        .hasSize(36);
  }
}
