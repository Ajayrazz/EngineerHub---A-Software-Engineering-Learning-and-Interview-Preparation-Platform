package com.engineerhub.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AnonymousMutationMatcherTest {

  private static boolean needsAuth(AnonymousMutationMatcher m, String method, String path) {
    return m.matches(new MockHttpServletRequest(method, path));
  }

  @Test
  void everyStateChangingMethodNeedsAuthenticationByDefault() {
    var m = new AnonymousMutationMatcher(List.of());
    for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE", "post"}) {
      assertThat(needsAuth(m, method, "/api/v1/anything")).as(method).isTrue();
    }
  }

  @Test
  void safeMethodsAreNotCaught() {
    var m = new AnonymousMutationMatcher(List.of());
    for (String method : new String[] {"GET", "HEAD", "OPTIONS", "TRACE"}) {
      assertThat(needsAuth(m, method, "/api/v1/anything")).as(method).isFalse();
    }
  }

  @Test
  void onlyExplicitlyListedPathsMayBeAnonymousMutations() {
    var m =
        new AnonymousMutationMatcher(
            List.of("/api/v1/webhooks/**", "/api/v1/newsletter/subscribe"));
    assertThat(needsAuth(m, "POST", "/api/v1/webhooks/razorpay")).isFalse();
    assertThat(needsAuth(m, "POST", "/api/v1/newsletter/subscribe")).isFalse();
    assertThat(needsAuth(m, "POST", "/api/v1/newsletter/subscribe/extra")).isTrue();
    assertThat(needsAuth(m, "POST", "/api/v1/me/profile")).isTrue();
    // Ant semantics: "/api/v1/webhooks/**" also matches "/api/v1/webhooks" itself, but nothing
    // outside it.
    assertThat(needsAuth(m, "DELETE", "/api/v1/webhooks")).isFalse();
    assertThat(needsAuth(m, "DELETE", "/api/v1/webhooksX")).isTrue();
  }
}
