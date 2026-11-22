package com.engineerhub.identity.internal;

import com.engineerhub.identity.UserDirectory;
import com.engineerhub.identity.UserRef;
import com.engineerhub.shared.error.ApiException;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own account. The user is ALWAYS taken from the authenticated principal — there is no
 * user id in any URL or body, so there is no way to address someone else's account (no IDOR
 * surface).
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

  private final UserDirectory directory;
  private final UserAccountRepository users;
  private final UserProfileRepository profiles;
  private final Clock clock;

  MeController(
      UserDirectory directory,
      UserAccountRepository users,
      UserProfileRepository profiles,
      Clock clock) {
    this.directory = directory;
    this.users = users;
    this.profiles = profiles;
    this.clock = clock;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Transactional
  MeResponse me(Authentication authentication) {
    UserRef ref = directory.current(authentication); // provisions on first use
    return view(ref, authentication);
  }

  @PatchMapping("/profile")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  MeResponse updateProfile(
      Authentication authentication, @Valid @RequestBody ProfileUpdateRequest request) {
    UserRef ref = directory.current(authentication);
    UserProfile profile =
        profiles.findById(ref.id()).orElseThrow(() -> ApiException.notFound("Profile not found."));
    profile.apply(request, clock.instant());
    return view(ref, authentication);
  }

  @PutMapping("/preferences")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  ResponseEntity<MeResponse> updatePreferences(
      Authentication authentication, @Valid @RequestBody PreferencesRequest request) {
    UserRef ref = directory.current(authentication);
    UserAccount account = users.findById(ref.id()).orElseThrow();
    account.changePreferences(request.timezone(), request.locale(), clock.instant());
    return ResponseEntity.ok(view(ref, authentication));
  }

  private MeResponse view(UserRef ref, Authentication authentication) {
    UserAccount a = users.findById(ref.id()).orElseThrow();
    UserProfile p =
        profiles.findById(ref.id()).orElseThrow(() -> ApiException.notFound("Profile not found."));
    List<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(r -> r.startsWith("ROLE_"))
            .map(r -> r.substring(5))
            .sorted()
            .toList();
    return new MeResponse(
        a.id(),
        a.username(),
        a.email(),
        a.emailVerified(),
        a.status().db(),
        roles,
        a.timezone(),
        a.locale(),
        a.createdAt(),
        new MeResponse.Profile(
            p.displayName(), p.bio(), p.website(), p.githubUrl(), p.linkedinUrl()));
  }
}
