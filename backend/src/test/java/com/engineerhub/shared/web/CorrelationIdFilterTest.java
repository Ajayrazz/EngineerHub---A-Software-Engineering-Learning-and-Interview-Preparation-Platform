package com.engineerhub.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  private String run(String inboundHeader, AtomicReference<String> mdcInsideChain)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    if (inboundHeader != null) {
      request.addHeader(CorrelationId.HEADER, inboundHeader);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            mdcInsideChain.set(CorrelationId.current());
          }
        };
    filter.doFilter(request, response, chain);
    return response.getHeader(CorrelationId.HEADER);
  }

  @Test
  void generatesAnIdWhenNoneIsProvided() throws Exception {
    AtomicReference<String> inside = new AtomicReference<>();
    String header = run(null, inside);
    assertThat(header).isNotBlank();
    assertThat(inside.get()).isEqualTo(header);
  }

  @Test
  void honoursAWellFormedInboundId() throws Exception {
    AtomicReference<String> inside = new AtomicReference<>();
    assertThat(run("web-req-1234.abcd_EF", inside)).isEqualTo("web-req-1234.abcd_EF");
    assertThat(inside.get()).isEqualTo("web-req-1234.abcd_EF");
  }

  @Test
  void replacesMaliciousOrOversizedInboundIds() throws Exception {
    for (String bad :
        new String[] {
          "short",
          "has space in it 12345",
          "line\nbreak-injection-123",
          "a".repeat(65),
          "<script>alert(1)</script>"
        }) {
      AtomicReference<String> inside = new AtomicReference<>();
      String header = run(bad, inside);
      assertThat(header).isNotEqualTo(bad).matches("^[0-9a-f-]{36}$"); // a fresh UUIDv7
    }
  }

  @Test
  void clearsTheMdcAfterTheRequest() throws Exception {
    run(null, new AtomicReference<>());
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }
}
