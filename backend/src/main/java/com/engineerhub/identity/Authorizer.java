package com.engineerhub.identity;

import org.springframework.security.core.Authentication;

/**
 * The single authorization question every module asks (docs/08 §3). Exposed to SpEL as the bean
 * {@code authz}:
 *
 * <pre>{@code
 * @PreAuthorize("@authz.can('user.read')")                       // global capability
 * @PreAuthorize("@authz.can('course.edit', 'course', #courseId)") // capability on one resource
 * }</pre>
 *
 * <p>Deny by default: unknown permission codes, anonymous callers and any error answer {@code
 * false}. Scoped permissions (instructors, problem setters) are true only for resources the user
 * was explicitly granted; a grant can never exceed what the user's role allows.
 */
public interface Authorizer {

  /** Global capability of the current caller. */
  boolean can(String permission);

  /** Capability on one resource (global capability OR a matching, unexpired scoped grant). */
  boolean can(String permission, String resourceType, Object resourceId);

  boolean can(Authentication authentication, String permission);

  boolean can(
      Authentication authentication, String permission, String resourceType, Object resourceId);
}
