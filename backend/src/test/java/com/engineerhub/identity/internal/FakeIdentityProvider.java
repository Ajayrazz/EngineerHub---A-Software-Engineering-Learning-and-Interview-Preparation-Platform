package com.engineerhub.identity.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * In-memory identity provider for integration tests (the real client has its own HTTP-level test).
 */
@Component
@Primary
class FakeIdentityProvider implements IdentityProviderAdmin {

  final Map<String, Set<String>> roles = new ConcurrentHashMap<>();
  volatile boolean configured = true;
  volatile boolean down = false;

  void reset() {
    roles.clear();
    configured = true;
    down = false;
  }

  void give(String sub, String... names) {
    roles.computeIfAbsent(sub, k -> ConcurrentHashMap.newKeySet()).addAll(Set.of(names));
  }

  private void check() {
    if (down) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "down");
    }
  }

  @Override
  public boolean configured() {
    return configured;
  }

  @Override
  public Set<String> realmRolesOf(String id) {
    check();
    return new HashSet<>(roles.getOrDefault(id, Set.of()));
  }

  @Override
  public void changeRealmRoles(String id, Set<String> add, Set<String> remove) {
    check();
    Set<String> set = roles.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    set.removeAll(remove);
    set.addAll(add);
  }

  @Override
  public int countHolders(String role) {
    check();
    return (int) roles.values().stream().filter(s -> s.contains(role)).count();
  }
}
