package com.engineerhub.system;

import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnostic endpoints used to prove the API is up and that authentication/authorization are wired
 * correctly (also consumed by the BFF and the dev-stack checks). Not product features.
 */
@RestController
@RequestMapping("/api/v1/system")
class SystemController {

  record Ping(String status, String service, Instant time) {}

  /** Who the API believes the caller is. Contains no personal data beyond the opaque subject id. */
  record WhoAmI(String subject, List<String> roles) {}

  private final String applicationName;

  SystemController(@Value("${spring.application.name}") String applicationName) {
    this.applicationName = applicationName;
  }

  @GetMapping("/ping")
  @PermitAll
  Ping ping() {
    return new Ping("ok", applicationName, Instant.now());
  }

  @GetMapping("/whoami")
  @PreAuthorize("isAuthenticated()")
  WhoAmI whoami(Authentication authentication) {
    List<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .sorted()
            .toList();
    return new WhoAmI(authentication.getName(), roles);
  }

  @GetMapping("/admin/ping")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  Ping adminPing() {
    return new Ping("ok", applicationName, Instant.now());
  }
}
