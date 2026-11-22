package com.engineerhub.shared.error;

import java.util.Objects;

/**
 * Domain/application exception that maps to a problem+json response with a stable {@link
 * ErrorCode}.
 *
 * <p><b>The {@code detail} is sent to the client.</b> It must be a safe, human-readable sentence
 * that contains no internal identifiers, SQL, stack information, secrets or personal data.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String safeDetail) {
    super(safeDetail);
    this.code = Objects.requireNonNull(code);
  }

  public ApiException(ErrorCode code, String safeDetail, Throwable cause) {
    super(safeDetail, cause);
    this.code = Objects.requireNonNull(code);
  }

  public ErrorCode code() {
    return code;
  }

  public static ApiException notFound(String safeDetail) {
    return new ApiException(ErrorCode.NOT_FOUND, safeDetail);
  }

  public static ApiException conflict(String safeDetail) {
    return new ApiException(ErrorCode.CONFLICT, safeDetail);
  }
}
