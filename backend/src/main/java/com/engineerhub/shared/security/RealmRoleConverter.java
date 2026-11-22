package com.engineerhub.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps Keycloak realm roles ({@code realm_access.roles}) to Spring authorities {@code ROLE_<NAME>}.
 *
 * <p>Only an explicit allowlist of staff roles is accepted; anything else in the token (Keycloak's
 * built-ins such as {@code offline_access}, or a mistakenly created {@code PREMIUM_USER}) is
 * ignored. Premium/free access is <b>not</b> a role (ADR-006) — it comes from server-side
 * entitlements. Adding a staff role is therefore a deliberate code change plus an
 * authorization-matrix update.
 */
final class RealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  static final Set<String> STAFF_ROLES =
      Set.of(
          "SUPER_ADMIN", "ADMIN", "CONTENT_MANAGER", "PROBLEM_SETTER", "MODERATOR", "INSTRUCTOR");

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Object realmAccess = jwt.getClaim("realm_access");
    if (!(realmAccess instanceof Map<?, ?> map) || !(map.get("roles") instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(STAFF_ROLES::contains)
        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
        .toList();
  }
}
