package com.engineerhub.identity.internal;

import com.engineerhub.identity.Authorizer;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Public class (not package-private) because SpEL invokes it reflectively as the bean {@code
 * authz}.
 */
@Component("authz")
public class AuthorizerImpl implements Authorizer {

  private static final Logger log = LoggerFactory.getLogger(AuthorizerImpl.class);

  private final PermissionCatalog catalog;
  private final JdbcClient jdbc;
  private final Clock clock;

  AuthorizerImpl(PermissionCatalog catalog, JdbcClient jdbc, Clock clock) {
    this.catalog = catalog;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public boolean can(String permission) {
    return can(SecurityContextHolder.getContext().getAuthentication(), permission);
  }

  @Override
  public boolean can(String permission, String resourceType, Object resourceId) {
    return can(
        SecurityContextHolder.getContext().getAuthentication(),
        permission,
        resourceType,
        resourceId);
  }

  @Override
  public boolean can(Authentication authentication, String permission) {
    try {
      if (!authenticated(authentication) || !known(permission)) {
        return false;
      }
      return roles(authentication).stream().anyMatch(r -> globalOf(r).contains(permission));
    } catch (RuntimeException e) {
      log.error("Authorization check failed; denying", e); // fail closed
      return false;
    }
  }

  @Override
  public boolean can(
      Authentication authentication, String permission, String resourceType, Object resourceId) {
    try {
      if (!authenticated(authentication)
          || !known(permission)
          || resourceType == null
          || resourceId == null) {
        return false;
      }
      Set<String> roles = roles(authentication);
      if (roles.stream().anyMatch(r -> globalOf(r).contains(permission))) {
        return true;
      }
      // A scoped permission needs BOTH: a role that allows it on a scoped basis AND a live grant
      // for the resource.
      if (roles.stream().noneMatch(r -> scopedOf(r).contains(permission))) {
        return false;
      }
      return jdbc.sql(
              """
              select exists (
                select 1 from identity.scoped_grants g
                join identity.users u on u.id = g.user_id
                where u.keycloak_sub = :sub and u.status = 'active'
                  and g.permission_code = :perm and g.resource_type = :type and g.resource_id = :rid
                  and (g.expires_at is null or g.expires_at > :now))
              """)
          .param("sub", authentication.getName())
          .param("perm", permission)
          .param("type", resourceType)
          .param("rid", String.valueOf(resourceId))
          .param("now", Timestamp.from(clock.instant()))
          .query(Boolean.class)
          .single();
    } catch (RuntimeException e) {
      log.error("Authorization check failed; denying", e);
      return false;
    }
  }

  // ---- helpers ----

  private boolean known(String permission) {
    boolean known = permission != null && catalog.snapshot().allPermissions().contains(permission);
    if (!known) {
      log.warn("Authorization requested for unknown permission '{}': denied", permission);
    }
    return known;
  }

  private static boolean authenticated(Authentication a) {
    return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
  }

  private static Set<String> roles(Authentication a) {
    Set<String> roles = new HashSet<>();
    for (GrantedAuthority g : a.getAuthorities()) {
      if (g.getAuthority().startsWith("ROLE_")) {
        roles.add(g.getAuthority().substring(5));
      }
    }
    return roles;
  }

  private Set<String> globalOf(String role) {
    var rp = catalog.snapshot().byRole().get(role);
    return rp == null ? Set.of() : rp.global();
  }

  private Set<String> scopedOf(String role) {
    var rp = catalog.snapshot().byRole().get(role);
    return rp == null ? Set.of() : rp.scoped();
  }
}
