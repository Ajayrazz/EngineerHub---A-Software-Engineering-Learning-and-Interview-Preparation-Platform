package com.engineerhub.billing.internal;

import com.engineerhub.billing.EntitlementService;
import com.engineerhub.identity.Authorizer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * STUB until Phase 6 (M6.1 replaces it with plans / AccessPeriod): premium content is available
 * only to staff who hold {@code content.premium.read} (globally, or scoped to the course for
 * instructors). Learners are therefore never entitled yet; there is deliberately no flag or
 * property that grants premium access, so the stub cannot be switched on by configuration in
 * production.
 */
@Service
class StubEntitlementService implements EntitlementService {

  private final Authorizer authz;

  StubEntitlementService(Authorizer authz) {
    this.authz = authz;
  }

  @Override
  public boolean canReadPremium(
      Authentication authentication, String resourceType, Object resourceId) {
    return authentication != null
        && authz.can(authentication, "content.premium.read", resourceType, resourceId);
  }
}
