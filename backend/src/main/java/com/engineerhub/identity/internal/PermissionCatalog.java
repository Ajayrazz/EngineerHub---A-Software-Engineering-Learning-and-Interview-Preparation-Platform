package com.engineerhub.identity.internal;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** role -> permissions (global and scoped), loaded from the database and refreshed every minute. */
@Component
class PermissionCatalog {

  record RolePermissions(Set<String> global, Set<String> scoped) {}

  record Snapshot(
      Map<String, RolePermissions> byRole, Set<String> allPermissions, Set<String> grantable) {}

  private static final Object KEY = new Object();

  private final LoadingCache<Object, Snapshot> cache;

  PermissionCatalog(JdbcClient jdbc) {
    this.cache =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).build(k -> load(jdbc));
  }

  Snapshot snapshot() {
    return cache.get(KEY);
  }

  void refresh() {
    cache.invalidateAll();
  }

  private static Snapshot load(JdbcClient jdbc) {
    Map<String, Set<String>> global = new HashMap<>();
    Map<String, Set<String>> scoped = new HashMap<>();
    jdbc.sql("select role, permission_code, scoped from identity.role_permissions")
        .query(
            rs -> {
              (rs.getBoolean("scoped") ? scoped : global)
                  .computeIfAbsent(rs.getString("role"), r -> new HashSet<>())
                  .add(rs.getString("permission_code"));
            });
    Set<String> roles = new HashSet<>(global.keySet());
    roles.addAll(scoped.keySet());
    Map<String, RolePermissions> byRole = new HashMap<>();
    for (String role : roles) {
      byRole.put(
          role,
          new RolePermissions(
              Set.copyOf(global.getOrDefault(role, Set.of())),
              Set.copyOf(scoped.getOrDefault(role, Set.of()))));
    }
    Set<String> all =
        new HashSet<>(jdbc.sql("select code from identity.permissions").query(String.class).list());
    Set<String> grantable =
        new HashSet<>(
            jdbc.sql("select code from identity.permissions where grantable")
                .query(String.class)
                .list());
    return new Snapshot(Map.copyOf(byRole), Set.copyOf(all), Set.copyOf(grantable));
  }
}
