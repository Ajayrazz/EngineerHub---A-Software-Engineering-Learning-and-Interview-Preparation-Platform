package com.engineerhub.billing.internal;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fails fast on anything but test mode: a live key must never be usable by accident (docs/21 §6.10,
 * §9).
 */
@Component
@EnableConfigurationProperties(RazorpayProperties.class)
class RazorpayModeGuard {

  private final RazorpayProperties props;

  RazorpayModeGuard(RazorpayProperties props) {
    this.props = props;
  }

  @PostConstruct
  void check() {
    if (props.mode() != null && !props.mode().isBlank() && !"test".equals(props.mode())) {
      throw new IllegalStateException(
          "RAZORPAY_MODE must be 'test': this build refuses live payments whatever the Live Readiness Gate records (ADR-055).");
    }
    if (props.keyId() != null && props.keyId().startsWith("rzp_live_")) {
      throw new IllegalStateException(
          "A live Razorpay key was configured; only rzp_test_ keys are accepted by this build (ADR-055).");
    }
  }
}
