package com.engineerhub.identity.internal;

import com.engineerhub.identity.UserDirectory;
import com.engineerhub.identity.UserRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryImpl implements UserDirectory {

  private final UserProvisioner provisioner;
  private final UserAccountRepository users;

  UserDirectoryImpl(UserProvisioner provisioner, UserAccountRepository users) {
    this.provisioner = provisioner;
    this.users = users;
  }

  @Override
  @Transactional
  public UserRef current(Authentication authentication) {
    UUID id = provisioner.provision(authentication).id();
    return users.findById(id).map(UserDirectoryImpl::ref).orElseThrow();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserRef> findBySubject(String keycloakSubject) {
    return users.findByKeycloakSub(keycloakSubject).map(UserDirectoryImpl::ref);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserRef> findById(UUID id) {
    return users.findById(id).map(UserDirectoryImpl::ref);
  }

  private static UserRef ref(UserAccount u) {
    return new UserRef(u.id(), u.username(), u.status() == UserStatus.ACTIVE);
  }
}
