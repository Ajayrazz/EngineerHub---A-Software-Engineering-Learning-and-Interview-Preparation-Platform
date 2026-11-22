package com.engineerhub.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** {@code identity.users}. Created by {@link UserProvisioner}; changed through services only. */
@Entity
@Table(schema = "identity", name = "users")
class UserAccount {

  @Id private UUID id;

  @Column(name = "keycloak_sub", nullable = false, updatable = false)
  private String keycloakSub;

  @Column(nullable = false)
  private String username;

  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String timezone;

  @Column(nullable = false)
  private String locale;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_login_at", nullable = false)
  private Instant lastLoginAt;

  @Version private long version;

  protected UserAccount() {}

  UUID id() {
    return id;
  }

  String keycloakSub() {
    return keycloakSub;
  }

  String username() {
    return username;
  }

  String email() {
    return email;
  }

  boolean emailVerified() {
    return emailVerified;
  }

  UserStatus status() {
    return UserStatus.fromDb(status);
  }

  String timezone() {
    return timezone;
  }

  String locale() {
    return locale;
  }

  Instant createdAt() {
    return createdAt;
  }

  void changeStatus(UserStatus newStatus, Instant now) {
    this.status = newStatus.db();
    this.updatedAt = now;
  }

  void changePreferences(String timezone, String locale, Instant now) {
    this.timezone = timezone;
    this.locale = locale;
    this.updatedAt = now;
  }
}
