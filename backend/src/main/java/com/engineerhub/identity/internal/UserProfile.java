package com.engineerhub.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** {@code identity.user_profiles} — user-editable, publicly displayable fields (PII). */
@Entity
@Table(schema = "identity", name = "user_profiles")
class UserProfile {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "avatar_key")
  private String avatarKey;

  private String bio;
  private String website;

  @Column(name = "github_url")
  private String githubUrl;

  @Column(name = "linkedin_url")
  private String linkedinUrl;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected UserProfile() {}

  UUID userId() {
    return userId;
  }

  String displayName() {
    return displayName;
  }

  String bio() {
    return bio;
  }

  String website() {
    return website;
  }

  String githubUrl() {
    return githubUrl;
  }

  String linkedinUrl() {
    return linkedinUrl;
  }

  /** null = leave unchanged, empty string = clear the field. */
  void apply(ProfileUpdateRequest r, Instant now) {
    if (r.displayName() != null) displayName = blankToNull(r.displayName());
    if (r.bio() != null) bio = blankToNull(r.bio());
    if (r.website() != null) website = blankToNull(r.website());
    if (r.githubUrl() != null) githubUrl = blankToNull(r.githubUrl());
    if (r.linkedinUrl() != null) linkedinUrl = blankToNull(r.linkedinUrl());
    updatedAt = now;
  }

  private static String blankToNull(String v) {
    String t = v.strip();
    return t.isEmpty() ? null : t;
  }
}
