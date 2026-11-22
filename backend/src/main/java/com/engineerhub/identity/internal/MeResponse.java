package com.engineerhub.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The caller's own account. Never contains tokens, internal ids of other systems or other users'
 * data.
 */
record MeResponse(
    UUID id,
    String username,
    String email,
    boolean emailVerified,
    String status,
    List<String> roles,
    String timezone,
    String locale,
    Instant createdAt,
    Profile profile) {

  record Profile(
      String displayName, String bio, String website, String githubUrl, String linkedinUrl) {}
}
