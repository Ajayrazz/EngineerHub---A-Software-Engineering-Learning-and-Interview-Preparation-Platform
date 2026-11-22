package com.engineerhub.shared.error;

import com.engineerhub.shared.web.CorrelationId;
import java.net.URI;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 problem details with EngineerHub's standard members: type, title, status, detail,
 * instance, code, correlationId.
 */
public final class Problems {

  private Problems() {}

  public static ProblemDetail of(ErrorCode code, String safeDetail, String requestPath) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), safeDetail);
    enrich(pd, code, requestPath);
    return pd;
  }

  /** Adds the standard members to a problem created elsewhere (e.g. by Spring MVC). */
  public static void enrich(ProblemDetail pd, ErrorCode code, String requestPath) {
    pd.setType(URI.create(code.typeUri()));
    pd.setTitle(code.title());
    if (requestPath != null && pd.getInstance() == null) {
      try {
        pd.setInstance(new URI(null, null, requestPath, null));
      } catch (java.net.URISyntaxException ignored) {
        // never fail an error response because the path is odd; just omit "instance"
      }
    }
    pd.setProperty("code", code.name());
    String correlationId = CorrelationId.current();
    if (correlationId != null) {
      pd.setProperty("correlationId", correlationId);
    }
  }
}
