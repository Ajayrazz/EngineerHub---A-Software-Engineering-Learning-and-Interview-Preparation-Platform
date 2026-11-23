package com.engineerhub.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.engineerhub.identity.Authorizer;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.identity.UserRef;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

/** The compensation path: a role change that cannot be audited must not stay in effect. */
class RoleAssignmentServiceTest {

  @Test
  void aFailedAuditRevertsTheChangeInTheIdentityProvider() {
    var idp = new FakeIdentityProvider();
    idp.give("kc-target");
    var users = mock(UserAccountRepository.class);
    var account = mock(UserAccount.class);
    UUID targetId = UUID.randomUUID();
    when(account.keycloakSub()).thenReturn("kc-target");
    when(users.findById(targetId)).thenReturn(Optional.of(account));
    var directory = mock(UserDirectory.class);
    when(directory.current(any())).thenReturn(new UserRef(UUID.randomUUID(), "caller", true));
    var authz = mock(Authorizer.class);
    when(authz.can(any(Authentication.class), anyString())).thenReturn(true);
    var catalog = mock(PermissionCatalog.class);
    when(catalog.snapshot())
        .thenReturn(
            new PermissionCatalog.Snapshot(
                Map.of("MODERATOR", new PermissionCatalog.RolePermissions(Set.of(), Set.of())),
                Set.of(),
                Set.of()));
    var audit = mock(AuditLog.class);
    doThrow(new IllegalStateException("audit store down")).when(audit).record(any(), any(), any());
    var tx =
        new TransactionTemplate(
            new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
              @Override
              protected Object doGetTransaction() {
                return new Object();
              }

              @Override
              protected void doBegin(
                  Object t, org.springframework.transaction.TransactionDefinition d) {}

              @Override
              protected void doCommit(
                  org.springframework.transaction.support.DefaultTransactionStatus s) {}

              @Override
              protected void doRollback(
                  org.springframework.transaction.support.DefaultTransactionStatus s) {}
            });

    var service =
        new RoleAssignmentService(
            idp, users, directory, authz, audit, mock(OutboxPublisher.class), catalog, tx);

    assertThatThrownBy(
            () ->
                service.change(
                    mock(Authentication.class), targetId, List.of("MODERATOR"), "revert test"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(idp.roles.get("kc-target")).as("change reverted").isEmpty();
  }
}
