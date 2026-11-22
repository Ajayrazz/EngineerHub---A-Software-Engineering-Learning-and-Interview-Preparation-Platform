package com.engineerhub.shared.security;

import org.springframework.security.core.AuthenticationException;

/**
 * The identity provider (Keycloak) could not be reached to validate a token. Deliberately
 * <em>not</em> an {@code AuthenticationServiceException}: Spring rethrows those as 500s, whereas
 * this one is turned into a {@code 503 Service Unavailable} problem by {@link
 * ProblemSecurityHandlers}.
 */
class IdentityProviderUnavailableException extends AuthenticationException {

  IdentityProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
