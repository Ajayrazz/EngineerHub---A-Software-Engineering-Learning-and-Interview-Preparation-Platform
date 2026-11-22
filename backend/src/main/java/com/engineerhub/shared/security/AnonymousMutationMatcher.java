package com.engineerhub.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;

/**
 * Matches state-changing requests (anything but GET/HEAD/OPTIONS/TRACE) NOT on the public
 * allowlist.
 */
final class AnonymousMutationMatcher implements RequestMatcher {

  private static final Set<String> SAFE =
      Set.of(
          HttpMethod.GET.name(),
          HttpMethod.HEAD.name(),
          HttpMethod.OPTIONS.name(),
          HttpMethod.TRACE.name());
  private static final AntPathMatcher PATHS = new AntPathMatcher();

  private final List<String> publicPatterns;

  AnonymousMutationMatcher(List<String> publicPatterns) {
    this.publicPatterns = publicPatterns;
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    if (SAFE.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT))) {
      return false;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return publicPatterns.stream().noneMatch(p -> PATHS.match(p, path));
  }
}
