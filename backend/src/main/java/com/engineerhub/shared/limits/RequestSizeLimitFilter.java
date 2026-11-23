package com.engineerhub.shared.limits;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.error.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Caps request body size. A declared {@code Content-Length} over the limit is refused immediately
 * (413, before any parsing or authentication work); a body without a length (chunked) is counted
 * while it is read and aborted at the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long max;
  private final ObjectMapper mapper;

  RequestSizeLimitFilter(LimitsProperties props, ObjectMapper mapper) {
    this.max = props.maxRequestBytes();
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > max) {
      var pd =
          Problems.of(
              ErrorCode.PAYLOAD_TOO_LARGE,
              "The request body exceeds the allowed size.",
              request.getRequestURI());
      response.setStatus(pd.getStatus());
      response.setContentType("application/problem+json");
      mapper.writeValue(response.getOutputStream(), pd);
      return;
    }
    chain.doFilter(new Limited(request, max), response);
  }

  /** Counts bytes read; throws {@link ApiException} (413) once the limit is exceeded. */
  static final class Limited extends HttpServletRequestWrapper {
    private final long max;
    private ServletInputStream stream;

    Limited(HttpServletRequest request, long max) {
      super(request);
      this.max = max;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new Counting(super.getInputStream(), max);
      }
      return stream;
    }
  }

  static final class Counting extends ServletInputStream {
    private final ServletInputStream in;
    private final long max;
    private long seen;

    Counting(ServletInputStream in, long max) {
      this.in = in;
      this.max = max;
    }

    private void count(long n) {
      seen += n;
      if (seen > max) {
        throw new ApiException(
            ErrorCode.PAYLOAD_TOO_LARGE, "The request body exceeds the allowed size.");
      }
    }

    @Override
    public int read() throws IOException {
      int b = in.read();
      if (b >= 0) count(1);
      return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      int n = in.read(buf, off, len);
      if (n > 0) count(n);
      return n;
    }

    @Override
    public boolean isFinished() {
      return in.isFinished();
    }

    @Override
    public boolean isReady() {
      return in.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      in.setReadListener(listener);
    }
  }
}
