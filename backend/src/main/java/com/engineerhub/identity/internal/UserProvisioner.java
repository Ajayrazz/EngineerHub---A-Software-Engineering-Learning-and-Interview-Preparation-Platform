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

    Provisioned result;
    try {
      result = insertOrSync(sub, username, email, emailVerified, now);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      // Another local account already owns this username under a different Keycloak identity (e.g.
      // the
      // identity was deleted and re-created). Never merge by name: report it instead of a 500.
      throw new com.engineerhub.shared.error.ApiException(
          com.engineerhub.shared.error.ErrorCode.CONFLICT,
          "This username is already linked to a different account. Please contact support.");
    }
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

  /**
   * Creates the user, or brings an existing one in sync. Concurrent first requests for the same
   * subject race on the unique indexes: {@code on conflict do nothing} lets the loser fall through
   * to the winner's committed row; a username clash with a <i>different</i> subject surfaces as
   * DuplicateKeyException from the update path or as "no row for this subject" after a failed
   * insert.
   */
  private Provisioned insertOrSync(
      String sub, String username, String email, boolean emailVerified, Instant now) {
    var ts = java.sql.Timestamp.from(now);
    var inserted =
        jdbc.sql(
                """
                insert into identity.users
                  (id, keycloak_sub, username, email, email_verified, created_at, updated_at, last_login_at)
                values (:id, :sub, :username, :email, :ev, :now, :now, :now)
                on conflict do nothing
                returning id
                """)
            .param("id", Ids.newId())
            .param("sub", sub)
            .param("username", username)
            .param("email", email)
            .param("ev", emailVerified)
            .param("now", ts)
            .query(UUID.class)
            .optional();
    if (inserted.isPresent()) {
      return new Provisioned(inserted.get(), true);
    }
    // Existing subject (or the concurrent winner): sync changed claims, rewriting at most every 5
    // min.
    jdbc.sql(
            """
            update identity.users set
              username = :username, email = :email, email_verified = :ev,
              last_login_at = :now, updated_at = :now, version = version + 1
            where keycloak_sub = :sub
              and (username is distinct from cast(:username as varchar)
                or email is distinct from cast(:email as varchar)
                or email_verified is distinct from cast(:ev as boolean)
                or last_login_at < cast(:now as timestamptz) - interval '5 minutes')
            """)
        .param("sub", sub)
        .param("username", username)
        .param("email", email)
        .param("ev", emailVerified)
        .param("now", ts)
        .update();
    return jdbc.sql("select id from identity.users where keycloak_sub = :sub")
        .param("sub", sub)
        .query(UUID.class)
        .optional()
        .map(id -> new Provisioned(id, false))
        // Insert was skipped by a conflict, yet no row has this subject: the username is taken.
        .orElseThrow(
            () ->
                new com.engineerhub.shared.error.ApiException(
                    com.engineerhub.shared.error.ErrorCode.CONFLICT,
                    "This username is already linked to a different account. Please contact support."));
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    String t = value.strip();
    return t.length() > max ? t.substring(0, max) : t;
  }
}
