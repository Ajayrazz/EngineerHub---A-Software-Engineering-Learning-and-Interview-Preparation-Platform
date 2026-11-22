package com.engineerhub.shared.security;

import org.springframework.security.core.Authentication;

/**
 * Extension point: after a request has been authenticated (valid JWT), every guard may veto it by
 * throwing an {@link com.engineerhub.shared.error.ApiException} (rendered as problem+json). Lets a
 * feature module (e.g. identity: "this account is suspended") protect all endpoints without the
 * shared kernel depending on that module.
 */
public interface AuthenticatedRequestGuard {

  void check(Authentication authentication);
}
