package com.engineerhub.identity.internal;

import jakarta.validation.constraints.Size;

/**
 * PATCH body. Only these fields can ever be changed through the profile endpoint (no id, status,
 * email, roles: mass assignment is impossible by construction). {@code null} = unchanged, {@code
 * ""} = clear.
 */
record ProfileUpdateRequest(
    @Size(max = 100) @PlainText String displayName,
    @Size(max = 500) @PlainText(allowNewlines = true) String bio,
    @SafeUrl String website,
    @SafeUrl(hosts = {"github.com"}) String githubUrl,
    @SafeUrl(hosts = {"linkedin.com", "www.linkedin.com"}) String linkedinUrl) {}
