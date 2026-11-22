package com.engineerhub.identity.internal;

import com.engineerhub.identity.UserDirectory;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the caller may do — for the UI to show/hide controls. Purely informational: every action is
 * enforced again on the server; hiding a button is never a security control.
 */
@RestController
@RequestMapping("/api/v1/me/permissions")
class MyPermissionsController {

  record ScopedGrantView(String permission, String resourceType, String resourceId) {}

  record PermissionsResponse(
      List<String> global, List<String> scopedCapabilities, List<ScopedGrantView> grants) {}

  private final PermissionCatalog catalog;
  private final JdbcClient jdbc;
  private final UserDirectory directory;
  private final Clock clock;

  MyPermissionsController(
      PermissionCatalog catalog, JdbcClient jdbc, UserDirectory directory, Clock clock) {
    this.catalog = catalog;
    this.jdbc = jdbc;
    this.directory = directory;
    this.clock = clock;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  PermissionsResponse mine(Authentication authentication) {
    Set<String> global = new TreeSet<>();
    Set<String> scoped = new TreeSet<>();
    for (GrantedAuthority g : authentication.getAuthorities()) {
      if (g.getAuthority().startsWith("ROLE_")) {
        var rp = catalog.snapshot().byRole().get(g.getAuthority().substring(5));
        if (rp != null) {
          global.addAll(rp.global());
          scoped.addAll(rp.scoped());
        }
      }
    }
    scoped.removeAll(global);
    List<ScopedGrantView> grants = new ArrayList<>();
    if (!scoped.isEmpty()) {
      var userId = directory.current(authentication).id();
      grants =
          jdbc.sql(
                  "select permission_code, resource_type, resource_id from identity.scoped_grants where user_id = :u and (expires_at is null or expires_at > :now) order by permission_code, resource_type, resource_id")
              .param("u", userId)
              .param("now", Timestamp.from(clock.instant()))
              .query(
                  (rs, i) -> new ScopedGrantView(rs.getString(1), rs.getString(2), rs.getString(3)))
              .list();
      Set<String> allowed = scoped;
      grants =
          grants.stream()
              .filter(g -> allowed.contains(g.permission()))
              .toList(); // a grant never exceeds the role
    }
    return new PermissionsResponse(List.copyOf(global), List.copyOf(scoped), grants);
  }
}
