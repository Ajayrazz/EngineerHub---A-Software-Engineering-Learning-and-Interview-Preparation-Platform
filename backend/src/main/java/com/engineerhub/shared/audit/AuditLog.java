package com.engineerhub.shared.audit;

import java.util.UUID;
import org.springframework.security.core.Authentication;

/**
 * Records a privileged action. Must be called inside the transaction that performs the action
 * ({@code MANDATORY}): the audit entry then exists if and only if the change committed.
 */
public interface AuditLog {

  /**
   * @param actorUserId the actor's local user id (may be null only for system actors)
   */
  void record(Authentication actor, UUID actorUserId, AuditEvent event);
}
