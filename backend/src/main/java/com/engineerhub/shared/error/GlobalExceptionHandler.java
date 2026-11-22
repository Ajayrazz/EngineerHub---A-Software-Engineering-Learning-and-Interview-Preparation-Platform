package com.engineerhub.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place that turns exceptions into RFC 9457 {@code application/problem+json}.
 *
 * <p>Security rules: responses never contain stack traces, SQL, class names, exception messages of
 * unexpected errors, or rejected input values. Unexpected exceptions are logged server-side with
 * the correlation id and returned as a generic {@code INTERNAL_ERROR}.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final AuthenticationTrustResolverImpl TRUST =
      new AuthenticationTrustResolverImpl();

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
    if (ex.code().status().is5xxServerError()) {
      log.error("API exception {}", ex.code(), ex);
    }
    return respond(Problems.of(ex.code(), ex.getMessage(), request.getRequestURI()));
  }

  /** Two concurrent edits of the same row: the loser gets a 409 and may retry with fresh data. */
  @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
  ResponseEntity<Object> handleOptimisticLock(Exception ex, HttpServletRequest request) {
    return respond(
        Problems.of(
            ErrorCode.CONFLICT,
            "The resource was modified concurrently. Reload and try again.",
            request.getRequestURI()));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<Map<String, String>> errors =
        ex.getConstraintViolations().stream()
            .map(
                v ->
                    Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", String.valueOf(v.getMessage())))
            .toList();
    ProblemDetail pd =
        Problems.of(
            ErrorCode.VALIDATION_FAILED, "Request validation failed.", request.getRequestURI());
    pd.setProperty("errors", errors);
    return respond(pd);
  }

  /**
   * Method-security denials thrown inside controllers. An unauthenticated caller gets 401 (so
   * clients know to log in), an authenticated one gets 403 — same semantics as the filter-level
   * handlers.
   */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean anonymous =
        auth == null || auth instanceof AnonymousAuthenticationToken || TRUST.isAnonymous(auth);
    ErrorCode code = anonymous ? ErrorCode.UNAUTHENTICATED : ErrorCode.FORBIDDEN;
    ProblemDetail pd =
        Problems.of(
            code,
            anonymous ? "Authentication is required." : "You do not have access to this resource.",
            request.getRequestURI());
    ResponseEntity.BodyBuilder b = ResponseEntity.status(code.status());
    if (anonymous) {
      b.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    return b.contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(pd);
  }

  /** Last resort: log everything server-side, reveal nothing client-side. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return respond(
        Problems.of(
            ErrorCode.INTERNAL_ERROR,
            "An unexpected error occurred. Quote the correlation id when contacting support.",
            request.getRequestURI()));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    // Field names + constraint messages only: never echo the rejected value (may be a password
    // etc.).
    List<Map<String, String>> errors =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                e ->
                    Map.of(
                        "field",
                        e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
                        "message",
                        String.valueOf(e.getDefaultMessage())))
            .toList();
    ProblemDetail pd =
        Problems.of(ErrorCode.VALIDATION_FAILED, "Request validation failed.", path(request));
    pd.setProperty("errors", errors);
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
        .headers(headers)
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }

  /**
   * All other Spring MVC errors (404, 405, 415, unreadable body, ...) get the standard members
   * added.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail pd) {
      ErrorCode code = codeFor(ex, statusCode.value());
      if (code == ErrorCode.MALFORMED_REQUEST
          && ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
        pd.setDetail("The request body could not be read."); // never echo parser internals
      }
      Problems.enrich(pd, code, path(request));
      if (statusCode.is5xxServerError()) {
        log.error("Framework error {}", statusCode, ex);
      }
    }
    return response;
  }

  private static ErrorCode codeFor(Exception ex, int status) {
    if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException
        || ex instanceof org.springframework.web.servlet.NoHandlerFoundException) {
      return ErrorCode.NOT_FOUND;
    }
    if (ex instanceof org.springframework.web.method.annotation.HandlerMethodValidationException) {
      return ErrorCode.VALIDATION_FAILED;
    }
    return ErrorCode.forStatus(status);
  }

  private static ResponseEntity<Object> respond(ProblemDetail pd) {
    return ResponseEntity.status(pd.getStatus())
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }

  private static String path(WebRequest request) {
    return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
  }
}
