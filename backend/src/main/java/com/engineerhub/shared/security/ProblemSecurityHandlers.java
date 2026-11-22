package com.engineerhub.shared.security;

import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Filter-level 401/403 responses as problem+json (same shape as controller errors). */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper mapper;

  ProblemSecurityHandlers(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    if (ex instanceof IdentityProviderUnavailableException) {
      // Not the caller's fault and not a login challenge: tell clients to retry shortly.
      response.setHeader(HttpHeaders.RETRY_AFTER, "5");
      write(
          response,
          Problems.of(
              ErrorCode.SERVICE_UNAVAILABLE,
              "Authentication is temporarily unavailable. Please retry shortly.",
              request.getRequestURI()));
      return;
    }
    // Only the standard error *code* (e.g. invalid_token) goes into WWW-Authenticate — never the
    // exception message, which may contain parser/validator internals.
    String challenge = "Bearer";
    if (ex instanceof OAuth2AuthenticationException oae
        && oae.getError() != null
        && oae.getError().getErrorCode() != null
        && oae.getError().getErrorCode().matches("[a-z_]{1,40}")) {
      challenge = "Bearer error=\"" + oae.getError().getErrorCode() + "\"";
    }
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
    write(
        response,
        Problems.of(
            ErrorCode.UNAUTHENTICATED, "Authentication is required.", request.getRequestURI()));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    write(
        response,
        Problems.of(
            ErrorCode.FORBIDDEN,
            "You do not have access to this resource.",
            request.getRequestURI()));
  }

  private void write(HttpServletResponse response, ProblemDetail pd) throws IOException {
    response.setStatus(pd.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), pd);
  }
}
