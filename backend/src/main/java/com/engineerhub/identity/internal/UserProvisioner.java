package com.engineerhub.identity.internal;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Just-in-time provisioning: the first authenticated request of a Keycloak user creates the local
 * record, later requests keep it in sync with the token claims. Race-free (a single atomic upsert
 * on the unique Keycloak subject) and write-light (an existing, unchanged row is not rewritten more
 * often than every 5 minutes).
 */
@Service
class UserProvisioner {

  record Provisioned(UUID id, boolean created) {}

  private final JdbcClient jdbc;
  private final Clock clock;
  private final OutboxPublisher events;
  private final UserStatusCache statusCache;

  UserProvisioner(
      JdbcClient jdbc, Clock clock, OutboxPublisher events, UserStatusCache statusCache) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.events = events;
    this.statusCache = statusCache;
  }

  @Transactional
  Provisioned provision(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      throw new IllegalStateException("provisioning requires a JWT-authenticated principal");
    }
    Jwt jwt = token.getToken();
    String sub = jwt.getSubject();
    String username = clip(jwt.getClaimAsString("preferred_username"), 64);
    if (username == null || username.isBlank()) {
      username = clip(sub, 64);
    }
    String email = clip(jwt.getClaimAsString("email"), 254);
    boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
    Instant now = clock.instant();

    var upserted =
        jdbc.sql(
                """
                insert into identity.users
                  (id, keycloak_sub, username, email, email_verified, created_at, updated_at, last_login_at)
                values (:id, :sub, :username, :email, :ev, :now, :now, :now)
                on conflict (keycloak_sub) do update set
                  username = excluded.username, email = excluded.email,
                  email_verified = excluded.email_verified, last_login_at = excluded.last_login_at,
                  updated_at = excluded.updated_at, version = identity.users.version + 1
                where identity.users.username is distinct from excluded.username
                   or identity.users.email is distinct from excluded.email
                   or identity.users.email_verified is distinct from excluded.email_verified
                   or identity.users.last_login_at < excluded.last_login_at - interval '5 minutes'
                returning id, (xmax = 0) as created
                """)
            .param("id", Ids.newId())
            .param("sub", sub)
            .param("username", username)
            .param("email", email)
            .param("ev", emailVerified)
            .param("now", java.sql.Timestamp.from(now))
            .query(
                (rs, i) ->
                    new Provisioned(rs.getObject("id", UUID.class), rs.getBoolean("created")))
            .optional();

    if (upserted.isEmpty()) { // existing row, nothing changed: just read its id
      UUID id =
          jdbc.sql("select id from identity.users where keycloak_sub = :sub")
              .param("sub", sub)
              .query(UUID.class)
              .single();
      return new Provisioned(id, false);
    }
    Provisioned result = upserted.get();
    if (result.created()) {
      jdbc.sql(
              "insert into identity.user_profiles (user_id, updated_at) values (:id, :now) on conflict do nothing")
          .param("id", result.id())
          .param("now", java.sql.Timestamp.from(now))
          .update();
      // PII stays out of events: only the opaque id and the public username.
      events.publish(
          "identity.user.registered",
          1,
          "user",
          result.id().toString(),
          Map.of("userId", result.id(), "username", username));
    }
    statusCache.evict(sub);
    return result;
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    String t = value.strip();
    return t.length() > max ? t.substring(0, max) : t;
  }
}
