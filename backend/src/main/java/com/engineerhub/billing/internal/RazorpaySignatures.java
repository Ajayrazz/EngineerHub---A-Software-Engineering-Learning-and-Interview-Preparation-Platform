package com.engineerhub.billing.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signature checks of Razorpay (verified against its documentation): checkout callback
 * = {@code HMAC(order_id + "|" + payment_id, key_secret)}; webhook = {@code HMAC(raw body,
 * webhook_secret)}. Comparison is constant-time and a missing or malformed signature is simply "not
 * valid".
 */
final class RazorpaySignatures {

  private RazorpaySignatures() {}

  static String hmacHex(String secret, byte[] message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  static boolean checkoutValid(
      String keySecret, String orderId, String paymentId, String signature) {
    if (keySecret == null || keySecret.isBlank() || orderId == null || paymentId == null) {
      return false;
    }
    return constantTimeEquals(
        hmacHex(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)),
        signature);
  }

  static boolean webhookValid(String webhookSecret, byte[] rawBody, String signature) {
    if (webhookSecret == null || webhookSecret.isBlank() || rawBody == null) {
      return false;
    }
    return constantTimeEquals(hmacHex(webhookSecret, rawBody), signature);
  }

  private static boolean constantTimeEquals(String expectedHex, String given) {
    if (given == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expectedHex.getBytes(StandardCharsets.UTF_8),
        given.strip().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
  }
}
