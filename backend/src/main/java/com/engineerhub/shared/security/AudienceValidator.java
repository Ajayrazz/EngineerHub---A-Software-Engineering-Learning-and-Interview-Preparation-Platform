package com.engineerhub.shared.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects tokens that were not issued for this API. A token from the same Keycloak realm but for a
 * different client/audience must not be accepted (confused-deputy protection).
 */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String requiredAudience;

  AudienceValidator(String requiredAudience) {
    this.requiredAudience = requiredAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (jwt.getAudience() != null && jwt.getAudience().contains(requiredAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "The token is not intended for this API.", null));
  }
}
