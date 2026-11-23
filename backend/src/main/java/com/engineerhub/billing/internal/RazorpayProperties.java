package com.engineerhub.billing.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials, from the environment only. TEST MODE ONLY: until the Live Readiness Gate
 * (docs/21 §9) exists, a live mode or a live key is refused at startup ({@link RazorpayModeGuard}).
 * Blank = payments are not configured and the purchase endpoints answer 503.
 */
@ConfigurationProperties("engineerhub.billing.razorpay")
record RazorpayProperties(String mode, String keyId, String keySecret, String webhookSecret) {

  boolean checkoutConfigured() {
    return notBlank(keyId)
        && notBlank(keySecret)
        && "test".equals(mode)
        && keyId.startsWith("rzp_test_");
  }

  boolean webhookConfigured() {
    return notBlank(webhookSecret);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
