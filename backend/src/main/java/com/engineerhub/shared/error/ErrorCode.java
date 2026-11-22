package com.engineerhub.shared.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} member of problem+json
 * responses (RFC 9457). Clients branch on these, never on {@code detail} text. Adding a code is
 * additive; renaming or removing one is a breaking API change.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account suspended"),
  ENTITLEMENT_REQUIRED(HttpStatus.FORBIDDEN, "Subscription required"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
  CONFLICT(HttpStatus.CONFLICT, "Conflict"),
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Payload too large"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** Problem {@code type} URI, e.g. {@code urn:engineerhub:problem:not-found}. */
  public String typeUri() {
    return "urn:engineerhub:problem:" + name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /** Default code for a bare HTTP status (used for framework-generated errors). */
  public static ErrorCode forStatus(int httpStatus) {
    if (httpStatus == 400) {
      return MALFORMED_REQUEST; // VALIDATION_FAILED is only used where we know it is a validation
      // error
    }
    for (ErrorCode c : values()) {
      if (c.status.value() == httpStatus) {
        return c;
      }
    }
    return httpStatus >= 500 ? INTERNAL_ERROR : MALFORMED_REQUEST;
  }
}
