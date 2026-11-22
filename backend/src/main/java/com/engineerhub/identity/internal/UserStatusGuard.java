package com.engineerhub.identity.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.security.AuthenticatedRequestGuard;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Blocks every authenticated request of a suspended / deleted account (403 ACCOUNT_SUSPENDED). */
@Component
class UserStatusGuard implements AuthenticatedRequestGuard {

  private final UserStatusCache cache;

  UserStatusGuard(UserStatusCache cache) {
    this.cache = cache;
  }

  @Override
  public void check(Authentication authentication) {
    cache
        .statusOf(authentication.getName())
        .filter(status -> status != UserStatus.ACTIVE)
        .ifPresent(
            status -> {
              throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "This account is not active.");
            });
  }
}
