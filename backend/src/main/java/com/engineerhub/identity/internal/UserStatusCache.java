package com.engineerhub.identity.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Short-lived cache of account status by Keycloak subject, so the suspension guard does not hit the
 * database on every request. Another instance may serve a stale "active" for at most the TTL after
 * a suspension — acceptable, and bounded.
 */
@Component
class UserStatusCache {

  static final Duration TTL = Duration.ofSeconds(30);

  private final Cache<String, Optional<UserStatus>> cache =
      Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(50_000).build();
  private final UserAccountRepository repository;

  UserStatusCache(UserAccountRepository repository) {
    this.repository = repository;
  }

  /** Empty = no local record yet (first request: not blocked). */
  Optional<UserStatus> statusOf(String subject) {
    return cache.get(subject, s -> repository.findStatusBySub(s).map(UserStatus::fromDb));
  }

  void evict(String subject) {
    cache.invalidate(subject);
  }
}
