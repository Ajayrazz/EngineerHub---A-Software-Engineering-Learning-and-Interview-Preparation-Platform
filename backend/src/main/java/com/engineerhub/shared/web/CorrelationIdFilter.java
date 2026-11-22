package com.engineerhub.shared.web;

import com.engineerhub.shared.id.Ids;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id, exposes it to logs (MDC) and echoes it in the response,
 * so a user-reported id can be traced across web → API → worker logs.
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured only if it is a short token of safe
 * characters; anything else is discarded and replaced (prevents log injection and unbounded
 * values).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String inbound = request.getHeader(CorrelationId.HEADER);
    String id = (inbound != null && SAFE.matcher(inbound).matches()) ? inbound : newId();
    MDC.put(CorrelationId.MDC_KEY, id);
    response.setHeader(CorrelationId.HEADER, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }

  private static String newId() {
    return Ids.newId().toString();
  }
}
