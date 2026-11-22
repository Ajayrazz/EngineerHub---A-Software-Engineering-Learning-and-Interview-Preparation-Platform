package com.engineerhub.shared.web;

import org.slf4j.MDC;

/** Access to the current request's correlation id (set by {@link CorrelationIdFilter}). */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private CorrelationId() {}

  /** The current correlation id, or {@code null} outside a request. */
  public static String current() {
    return MDC.get(MDC_KEY);
  }
}
