package com.engineerhub.shared.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

  private static final LimitsProperties.Rate RATE =
      new LimitsProperties.Rate(true, Duration.ofSeconds(60), 5, 5, 5);

  @Test
  void failsOpenWhenTheLimiterBreaks() throws Exception {
    var meters = new SimpleMeterRegistry();
    RateLimiter broken =
        (k, l, w) -> {
          throw new IllegalStateException("redis down\r\nforged log line");
        };
    var filter = new RateLimitFilter(broken, RATE, meters, JsonMapper.builder().build());
    var req = new MockHttpServletRequest("GET", "/api/v1/me");
    var res = new MockHttpServletResponse();
    var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).as("request still served").isNotNull();
    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(meters.get("eh.ratelimit.errors").counter().count()).isEqualTo(1.0);
  }

  @Test
  void disabledFilterNeverCallsTheLimiter() throws Exception {
    var off = new LimitsProperties.Rate(false, Duration.ofSeconds(60), 1, 1, 1);
    RateLimiter boom =
        (k, l, w) -> {
          throw new AssertionError("must not be called");
        };
    var filter =
        new RateLimitFilter(boom, off, new SimpleMeterRegistry(), JsonMapper.builder().build());
    var chain = new MockFilterChain();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/me"), new MockHttpServletResponse(), chain);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void countingStreamAbortsChunkedBodiesAtTheLimit() throws Exception {
    var in =
        new RequestSizeLimitFilter.Counting(
            new DelegatingServletInputStream(new java.io.ByteArrayInputStream(new byte[100])), 50);
    byte[] buf = new byte[40];
    assertThat(in.read(buf, 0, 40)).isEqualTo(40);
    assertThatThrownBy(() -> in.read(buf, 0, 40))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
  }

  @Test
  void countingStreamAllowsABodyExactlyAtTheLimit() throws Exception {
    var in =
        new RequestSizeLimitFilter.Counting(
            new DelegatingServletInputStream(new java.io.ByteArrayInputStream(new byte[50])), 50);
    assertThat(in.readAllBytes()).hasSize(50);
  }
}
