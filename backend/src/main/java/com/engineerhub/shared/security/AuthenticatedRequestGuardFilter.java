package com.engineerhub.shared.security;

import com.engineerhub.shared.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs the {@link AuthenticatedRequestGuard}s for JWT-authenticated requests (placed after bearer
 * auth).
 */
final class AuthenticatedRequestGuardFilter extends OncePerRequestFilter {

  private final List<AuthenticatedRequestGuard> guards;
  private final ProblemSecurityHandlers problems;

  AuthenticatedRequestGuardFilter(
      List<AuthenticatedRequestGuard> guards, ProblemSecurityHandlers problems) {
    this.guards = guards;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken && auth.isAuthenticated()) {
      try {
        for (AuthenticatedRequestGuard guard : guards) {
          guard.check(auth);
        }
      } catch (ApiException denied) {
        problems.deny(request, response, denied);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
