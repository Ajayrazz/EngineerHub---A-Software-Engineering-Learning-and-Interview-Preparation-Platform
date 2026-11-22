package com.engineerhub.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.RemoteKeySourceException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

/** 401 (bad token) must stay distinct from 503 (cannot verify tokens right now). */
class ResilientJwtDecoderTest {

  private static ResilientJwtDecoder decoderThrowing(RuntimeException ex) {
    JwtDecoder failing =
        token -> {
          throw ex;
        };
    return new ResilientJwtDecoder(failing);
  }

  @Test
  void validTokensPassThrough() {
    Jwt jwt =
        Jwt.withTokenValue("t").header("alg", "none").subject("u").issuedAt(Instant.now()).build();
    assertThat(new ResilientJwtDecoder(token -> jwt).decode("t")).isSameAs(jwt);
  }

  @Test
  void invalidTokensStayJwtExceptionsSoTheyBecome401() {
    BadJwtException bad = new BadJwtException("bad signature");
    assertThatThrownBy(() -> decoderThrowing(bad).decode("t")).isSameAs(bad);
  }

  @Test
  void initialisationFailureBecomesIdentityProviderUnavailable() {
    assertThatThrownBy(
            () -> decoderThrowing(new JwtDecoderInitializationException("down", null)).decode("t"))
        .isInstanceOf(IdentityProviderUnavailableException.class);
  }

  @Test
  void jwkSetRetrievalFailureBecomesIdentityProviderUnavailable() {
    JwtException wrapped =
        new JwtException(
            "decode failed", new RuntimeException(new RemoteKeySourceException("x", null)));
    assertThatThrownBy(() -> decoderThrowing(wrapped).decode("t"))
        .isInstanceOf(IdentityProviderUnavailableException.class);
  }
}
