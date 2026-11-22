package com.engineerhub.shared.security;

import com.nimbusds.jose.RemoteKeySourceException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Separates "this token is bad" (401) from "we cannot check tokens right now" (503). Without this,
 * an identity-provider outage would surface as unhandled 500s on every authenticated request.
 */
final class ResilientJwtDecoder implements JwtDecoder {

  private final JwtDecoder delegate;

  ResilientJwtDecoder(JwtDecoder delegate) {
    this.delegate = delegate;
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    try {
      return delegate.decode(token);
    } catch (JwtDecoderInitializationException ex) {
      throw new IdentityProviderUnavailableException("JWT decoder could not be initialised", ex);
    } catch (JwtException ex) {
      if (causedByRemoteKeyFailure(ex)) {
        throw new IdentityProviderUnavailableException("JWK set could not be retrieved", ex);
      }
      throw ex; // genuinely invalid token -> 401
    }
  }

  private static boolean causedByRemoteKeyFailure(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof RemoteKeySourceException) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
