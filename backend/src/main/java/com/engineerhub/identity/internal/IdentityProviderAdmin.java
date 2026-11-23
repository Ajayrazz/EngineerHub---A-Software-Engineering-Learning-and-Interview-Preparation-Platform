package com.engineerhub.identity.internal;

import java.util.Set;

/**
 * What the platform needs from the identity provider's admin API: staff roles live in Keycloak
 * (they are token claims), so changing them means changing Keycloak. Implementations must fail with
 * an {@link com.engineerhub.shared.error.ApiException} (503) when the provider cannot be reached.
 */
interface IdentityProviderAdmin {

  /**
   * False when no admin credentials are configured; role management is then refused (fail closed).
   */
  boolean configured();

  /** Names of the realm roles directly assigned to the provider user. */
  Set<String> realmRolesOf(String providerUserId);

  /** Adds and removes realm roles in one logical change. Unknown role names are an error. */
  void changeRealmRoles(String providerUserId, Set<String> add, Set<String> remove);

  /** How many users hold the role (capped by the implementation: only "0, 1 or more" matters). */
  int countHolders(String role);
}
