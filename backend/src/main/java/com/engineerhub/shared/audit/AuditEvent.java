package com.engineerhub.shared.audit;

import java.util.Map;

/**
 * What happened. {@code before}/{@code after} should hold only the fields that changed and must
 * never contain secrets, tokens or unnecessary personal data.
 */
public record AuditEvent(
    String action,
    String resourceType,
    String resourceId,
    Map<String, Object> before,
    Map<String, Object> after,
    String reason) {

  public static AuditEvent of(String action, String resourceType, String resourceId) {
    return new AuditEvent(action, resourceType, resourceId, null, null, null);
  }

  public AuditEvent withChange(Map<String, Object> before, Map<String, Object> after) {
    return new AuditEvent(action, resourceType, resourceId, before, after, reason);
  }

  public AuditEvent withReason(String reason) {
    return new AuditEvent(action, resourceType, resourceId, before, after, reason);
  }
}
