package com.engineerhub.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AdminDtos {

  private AdminDtos() {}

  record UserView(
      UUID id,
      String username,
      String email,
      boolean emailVerified,
      String status,
      String timezone,
      Instant createdAt,
      Instant lastLoginAt) {}

  record UserPage(List<UserView> items, int page, int size, long total) {}

  record StatusChangeRequest(
      @NotBlank
          @Pattern(regexp = "^(active|suspended)$", message = "must be 'active' or 'suspended'")
          String status,
      @NotBlank @Size(min = 5, max = 500) @PlainText String reason) {}

  record GrantRequest(
      @NotBlank
          @Pattern(
              regexp = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$",
              message = "invalid permission code")
          String permission,
      @NotBlank @Pattern(regexp = "^[a-z][a-z-]{1,39}$", message = "invalid resource type")
          String resourceType,
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9._:-]{1,100}$", message = "invalid resource id")
          String resourceId,
      Instant expiresAt) {}

  record GrantView(
      UUID id,
      String permission,
      String resourceType,
      String resourceId,
      UUID grantedBy,
      Instant grantedAt,
      Instant expiresAt) {}

  record RolesView(List<String> roles) {}

  record RolesChangeRequest(
      @jakarta.validation.constraints.NotNull @Size(max = 6)
          List<@NotBlank @Pattern(regexp = "^[A-Z_]{3,30}$", message = "invalid role") String>
              roles,
      @NotBlank @Size(min = 5, max = 500) @PlainText String reason) {}

  record RoleView(String role, List<String> global, List<String> scoped) {}
}
