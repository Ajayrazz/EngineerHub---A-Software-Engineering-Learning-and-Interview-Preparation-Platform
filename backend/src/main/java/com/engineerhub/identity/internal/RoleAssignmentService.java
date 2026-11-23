package com.engineerhub.identity.internal;

import com.engineerhub.identity.Authorizer;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.identity.UserRef;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Staff role assignment. Roles are Keycloak realm roles (they become token claims), so the change
 * is made in Keycloak and audited here.
 *
 * <p>Ordering: the Keycloak call happens <em>outside</em> the database transaction (the audit log's
 * advisory lock must never be held across an HTTP call); the audit entry and event are then written
 * in their own transaction. If that fails, the Keycloak change is reverted (best effort) and the
 * caller gets an error, so a role change is never left un-audited by design. Effect on the target
 * is delayed until their access token is next issued (access tokens live 5 minutes).
 */
@Service
class RoleAssignmentService {

  private static final Logger log = LoggerFactory.getLogger(RoleAssignmentService.class);

  /** Changing, or changing anyone who holds, one of these needs {@code role.grant.all}. */
  static final Set<String> PRIVILEGED = Set.of("SUPER_ADMIN", "ADMIN");

  private final IdentityProviderAdmin idp;
  private final UserAccountRepository users;
  private final UserDirectory directory;
  private final Authorizer authz;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final PermissionCatalog catalog;
  private final TransactionTemplate tx;

  RoleAssignmentService(
      IdentityProviderAdmin idp,
      UserAccountRepository users,
      UserDirectory directory,
      Authorizer authz,
      AuditLog audit,
      OutboxPublisher events,
      PermissionCatalog catalog,
      TransactionTemplate tx) {
    this.idp = idp;
    this.users = users;
    this.directory = directory;
    this.authz = authz;
    this.audit = audit;
    this.events = events;
    this.catalog = catalog;
    this.tx = tx;
  }

  List<String> rolesOf(UUID userId) {
    requireConfigured();
    UserAccount target = target(userId);
    return List.copyOf(new TreeSet<>(staffRolesOf(target)));
  }

  List<String> change(Authentication actor, UUID targetId, List<String> requested, String reason) {
    requireConfigured();
    UserRef caller = directory.current(actor);
    if (caller.id().equals(targetId)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "You cannot change your own roles.");
    }
    UserAccount target = target(targetId);
    Set<String> known = catalog.snapshot().byRole().keySet();
    Set<String> desired = new HashSet<>(requested);
    if (!known.containsAll(desired)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown role.");
    }
    Set<String> current = staffRolesOf(target);
    Set<String> add = new HashSet<>(desired);
    add.removeAll(current);
    Set<String> remove = new HashSet<>(current);
    remove.removeAll(desired);
    if (add.isEmpty() && remove.isEmpty()) {
      return sorted(current); // nothing to do: idempotent, not audited
    }

    // Anti-escalation: administrators' roles (and anyone who holds one) are for super admins only.
    boolean touchesPrivileged =
        add.stream().anyMatch(PRIVILEGED::contains)
            || remove.stream().anyMatch(PRIVILEGED::contains)
            || current.stream().anyMatch(PRIVILEGED::contains);
    if (touchesPrivileged && !authz.can(actor, "role.grant.all")) {
      throw new ApiException(
          ErrorCode.FORBIDDEN, "Only a super admin can change administrator roles.");
    }
    if (remove.contains("SUPER_ADMIN") && idp.countHolders("SUPER_ADMIN") <= 1) {
      throw ApiException.conflict("The last super admin cannot be removed.");
    }

    idp.changeRealmRoles(target.keycloakSub(), add, remove);
    try {
      tx.executeWithoutResult(
          status -> {
            audit.record(
                actor,
                caller.id(),
                AuditEvent.of("user.roles.changed", "user", targetId.toString())
                    .withChange(Map.of("roles", sorted(current)), Map.of("roles", sorted(desired)))
                    .withReason(reason));
            events.publish(
                "identity.user.roles.changed",
                1,
                "user",
                targetId.toString(),
                Map.of("userId", targetId, "roles", sorted(desired)));
          });
    } catch (RuntimeException e) {
      try {
        idp.changeRealmRoles(target.keycloakSub(), remove, add); // revert
      } catch (RuntimeException revertFailure) {
        log.error(
            "Role change for user {} could not be audited AND could not be reverted: manual check needed",
            targetId);
      }
      throw e;
    }
    return sorted(desired);
  }

  /**
   * Is the target an administrator, so that only a super admin may act on the account? Skipped (no
   * call to the identity provider) when the actor is a super admin anyway.
   */
  void assertMayModerate(Authentication actor, UserAccount target) {
    if (authz.can(actor, "role.grant.all")) {
      return;
    }
    requireConfigured();
    if (staffRolesOf(target).stream().anyMatch(PRIVILEGED::contains)) {
      throw new ApiException(
          ErrorCode.FORBIDDEN, "Only a super admin can change an administrator's account.");
    }
  }

  // ------------------------------------------------------------------ helpers

  private Set<String> staffRolesOf(UserAccount target) {
    Set<String> known = catalog.snapshot().byRole().keySet();
    Set<String> staff = new HashSet<>(idp.realmRolesOf(target.keycloakSub()));
    staff.retainAll(known); // ignores marker/default roles
    return staff;
  }

  private UserAccount target(UUID id) {
    return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
  }

  private void requireConfigured() {
    if (!idp.configured()) {
      throw new ApiException(
          ErrorCode.SERVICE_UNAVAILABLE, "Role management is not configured on this server.");
    }
  }

  private static List<String> sorted(Set<String> roles) {
    return new ArrayList<>(new TreeSet<>(roles));
  }
}
