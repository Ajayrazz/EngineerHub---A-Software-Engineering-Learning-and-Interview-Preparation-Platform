package com.engineerhub.shared.limits;

import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.error.Problems;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-principal request quotas (placed after authentication, so the key is the user, or the client
 * address for anonymous callers). Classes: read, write (state-changing methods), admin
 * (/api/v1/admin).
 *
 * <p><b>Fails open</b>: if Redis is unreachable the request is allowed and the error is
 * counted/logged. Rationale: the limiter protects against abuse, the platform must stay available
 * if its cache is down. (Authentication-critical flows are protected by Keycloak's own brute-force
 * detection.)
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
  private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

  private final RateLimiter limiter;
  private final LimitsProperties.Rate rate;
  private final ObjectMapper mapper;
  private final Counter errors;
  private final MeterRegistry meters;

  RateLimitFilter(
      RateLimiter limiter, LimitsProperties.Rate rate, MeterRegistry meters, ObjectMapper mapper) {
    this.limiter = limiter;
    this.rate = rate;
    this.meters = meters;
    this.mapper = mapper;
    this.errors =
        Counter.builder("eh.ratelimit.errors")
            .description("Rate limiter failures (fail-open)")
            .register(meters);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !rate.enabled() || path.startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String clazz;
    int limit;
    if (request.getRequestURI().startsWith("/api/v1/admin")) {
      clazz = "admin";
      limit = rate.adminPerWindow();
    } else if (SAFE.contains(request.getMethod())) {
      clazz = "read";
      limit = rate.readPerWindow();
    } else {
      clazz = "write";
      limit = rate.writePerWindow();
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String principal =
        auth instanceof JwtAuthenticationToken
            ? "u:" + auth.getName()
            : "ip:" + request.getRemoteAddr();

    RateLimiter.Decision decision;
    try {
      decision = limiter.tryAcquire("rl:" + clazz + ":" + principal, limit, rate.window());
    } catch (RuntimeException e) {
      errors.increment();
      log.warn(
          "Rate limiter unavailable, allowing request ({}): {}",
          e.getClass().getSimpleName(),
          String.valueOf(e.getMessage()).replaceAll("[\\r\\n]+", " "));
      chain.doFilter(request, response);
      return;
    }
    response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
    if (!decision.allowed()) {
      meters.counter("eh.ratelimit.rejected", "class", clazz).increment();
      response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
      var pd =
          Problems.of(
              ErrorCode.RATE_LIMITED,
              "Too many requests. Retry in " + decision.retryAfterSeconds() + " seconds.",
              request.getRequestURI());
      response.setStatus(pd.getStatus());
      response.setContentType("application/problem+json");
      mapper.writeValue(response.getOutputStream(), pd);
      return;
    }
    chain.doFilter(request, response);
  }
}
