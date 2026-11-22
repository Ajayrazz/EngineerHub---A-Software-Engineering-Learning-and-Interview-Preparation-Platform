package com.engineerhub.identity.internal;

import com.engineerhub.identity.internal.AdminDtos.GrantRequest;
import com.engineerhub.identity.internal.AdminDtos.GrantView;
import com.engineerhub.identity.internal.AdminDtos.RoleView;
import com.engineerhub.identity.internal.AdminDtos.StatusChangeRequest;
import com.engineerhub.identity.internal.AdminDtos.UserPage;
import com.engineerhub.identity.internal.AdminDtos.UserView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of users, account status and scoped grants. Every endpoint states its permission.
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

  private final AdminService service;

  AdminController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/users")
  @PreAuthorize("@authz.can('user.read')")
  UserPage users(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(q, status, page, size);
  }

  @GetMapping("/users/{id}")
  @PreAuthorize("@authz.can('user.read')")
  UserView user(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/users/{id}/status")
  @PreAuthorize("@authz.can('user.suspend')")
  UserView changeStatus(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody StatusChangeRequest request) {
    return service.changeStatus(authentication, id, request.status(), request.reason());
  }

  @GetMapping("/users/{id}/grants")
  @PreAuthorize("@authz.can('user.read')")
  List<GrantView> grants(@PathVariable UUID id) {
    return service.grantsOf(id);
  }

  @PostMapping("/users/{id}/grants")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('role.grant.standard')")
  GrantView grant(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody GrantRequest request) {
    return service.grant(authentication, id, request);
  }

  @DeleteMapping("/users/{id}/grants/{grantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@authz.can('role.grant.standard')")
  void revoke(Authentication authentication, @PathVariable UUID id, @PathVariable UUID grantId) {
    service.revoke(authentication, id, grantId);
  }

  @GetMapping("/roles")
  @PreAuthorize("@authz.can('user.read')")
  List<RoleView> roles() {
    return service.roles();
  }
}
