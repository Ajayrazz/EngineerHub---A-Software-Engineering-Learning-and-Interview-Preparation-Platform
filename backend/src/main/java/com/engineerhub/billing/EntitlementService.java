package com.engineerhub.billing;

import org.springframework.security.core.Authentication;

/**
 * Public API of the billing module: "may this caller read premium content?". Content modules ask
 * this and never look at plans, payments or roles themselves, so the real answer (subscriptions,
 * passes, Phase 6) replaces the implementation without touching callers.
 */
public interface EntitlementService {

  /**
   * @param authentication the caller (anonymous callers are never entitled)
   * @param resourceType the kind of resource whose premium content is requested, e.g. {@code
   *     "course"}
   * @param resourceId its id, so scoped staff (an instructor of that course) can be recognised
   */
  boolean canReadPremium(Authentication authentication, String resourceType, Object resourceId);
}
