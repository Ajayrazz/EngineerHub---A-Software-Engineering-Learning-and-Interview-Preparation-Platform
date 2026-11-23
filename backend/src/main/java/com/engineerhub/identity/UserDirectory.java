package com.engineerhub.identity;

import java.util.Optional;
import org.springframework.security.core.Authentication;

/** Public API of the identity module. Other modules depend on this, never on identity internals. */
public interface UserDirectory {

  /**
   * The local user for an authenticated principal, created on first use (just-in-time provisioning
   * from the verified token claims). Ownership checks must use this id, never an id from a request.
   */
  UserRef current(Authentication authentication);

  Optional<UserRef> findBySubject(String keycloakSubject);

  Optional<UserRef> findById(java.util.UUID id);

  /** The user's IANA time zone (drives "what day is it for them"); UTC if unknown or invalid. */
  java.time.ZoneId zoneOf(java.util.UUID userId);
}
