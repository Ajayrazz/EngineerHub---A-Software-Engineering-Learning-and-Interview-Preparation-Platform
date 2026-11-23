package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RazorpaySignaturesTest {

  // Vectors computed independently with Python's hmac/hashlib (not with this code).
  private static final String CHECKOUT =
      "3543fd48eae872bdbf073cba210df94625e83a36ae8f72468ea7de5556bad9bf";
  private static final String WEBHOOK =
      "a865e98e201c1f3f20bd330f264cfe96ba9380948c706aee4a62095109b432b5";
  private static final byte[] BODY =
      "{\"event\":\"payment.captured\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);

  @Test
  void checkoutSignatureMatchesTheDocumentedConstruction() {
    assertThat(
            RazorpaySignatures.checkoutValid(
                "vector_key_secret", "order_ABC123", "pay_XYZ789", CHECKOUT))
        .isTrue();
    assertThat(
            RazorpaySignatures.checkoutValid(
                "vector_key_secret", "order_ABC123", "pay_XYZ789", CHECKOUT.toUpperCase()))
        .as("hex case does not matter")
        .isTrue();
  }

  @Test
  void anyChangedInputInvalidatesTheCheckoutSignature() {
    assertThat(
            RazorpaySignatures.checkoutValid(
                "vector_key_secret", "order_ABC124", "pay_XYZ789", CHECKOUT))
        .isFalse();
    assertThat(
            RazorpaySignatures.checkoutValid(
                "vector_key_secret", "order_ABC123", "pay_XYZ780", CHECKOUT))
        .isFalse();
    assertThat(
            RazorpaySignatures.checkoutValid(
                "other_secret", "order_ABC123", "pay_XYZ789", CHECKOUT))
        .isFalse();
    // swapping the parts must not validate (the pipe separates order and payment)
    assertThat(
            RazorpaySignatures.checkoutValid(
                "vector_key_secret", "pay_XYZ789", "order_ABC123", CHECKOUT))
        .isFalse();
  }

  @Test
  void webhookSignatureIsOverTheExactRawBytes() {
    assertThat(RazorpaySignatures.webhookValid("vector_webhook_secret", BODY, WEBHOOK)).isTrue();
    // re-serialised JSON (a space) is different bytes: not valid
    byte[] reformatted =
        "{\"event\": \"payment.captured\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);
    assertThat(RazorpaySignatures.webhookValid("vector_webhook_secret", reformatted, WEBHOOK))
        .isFalse();
    assertThat(RazorpaySignatures.webhookValid("wrong", BODY, WEBHOOK)).isFalse();
  }

  @Test
  void missingOrBlankInputsAreSimplyInvalid() {
    assertThat(RazorpaySignatures.checkoutValid("s", "o", "p", null)).isFalse();
    assertThat(RazorpaySignatures.checkoutValid("", "o", "p", CHECKOUT)).isFalse();
    assertThat(RazorpaySignatures.checkoutValid(null, "o", "p", CHECKOUT)).isFalse();
    assertThat(RazorpaySignatures.checkoutValid("s", null, "p", CHECKOUT)).isFalse();
    assertThat(RazorpaySignatures.webhookValid("s", BODY, null)).isFalse();
    assertThat(RazorpaySignatures.webhookValid("s", BODY, "")).isFalse();
    assertThat(RazorpaySignatures.webhookValid(" ", BODY, WEBHOOK)).isFalse();
    assertThat(RazorpaySignatures.webhookValid("s", null, WEBHOOK)).isFalse();
  }

  @Test
  void liveModeAndLiveKeysAreRefusedAtStartup() {
    new RazorpayModeGuard(new RazorpayProperties("test", "rzp_test_abc", "s", "w")).check();
    new RazorpayModeGuard(new RazorpayProperties("test", "", "", ""))
        .check(); // not configured is fine
    new RazorpayModeGuard(new RazorpayProperties(null, null, null, null)).check();
    assertThatThrownBy(
            () ->
                new RazorpayModeGuard(new RazorpayProperties("live", "rzp_test_abc", "s", "w"))
                    .check())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new RazorpayModeGuard(new RazorpayProperties("test", "rzp_live_abc", "s", "w"))
                    .check())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyTestKeysCountAsConfigured() {
    assertThat(new RazorpayProperties("test", "rzp_test_abc", "s", "w").checkoutConfigured())
        .isTrue();
    assertThat(new RazorpayProperties("test", "key_without_prefix", "s", "w").checkoutConfigured())
        .isFalse();
    assertThat(new RazorpayProperties("test", "rzp_test_abc", "", "w").checkoutConfigured())
        .isFalse();
    assertThat(new RazorpayProperties("live", "rzp_test_abc", "s", "w").checkoutConfigured())
        .isFalse();
    assertThat(new RazorpayProperties("test", "rzp_test_abc", "s", "").webhookConfigured())
        .isFalse();
  }
}
