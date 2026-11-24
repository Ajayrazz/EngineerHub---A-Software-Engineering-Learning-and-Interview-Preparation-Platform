package com.engineerhub.billing.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Razorpay webhooks (docs/21 §6): the signature over the RAW body is checked first; the event is
 * stored (deduplicated on the event id, falling back to the body hash when no id header is present)
 * and processed in the same transaction, so a crash leaves nothing half-done and the provider's
 * retry finishes the job. Delivery is at-least-once and unordered, so every handler is idempotent.
 */
@Service
class RazorpayWebhookService {

  private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
  private static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9_.:-]{1,100}$");

  private final JdbcClient jdbc;
  private final Clock clock;
  private final ObjectMapper mapper;
  private final PaymentProvider provider;
  private final PaymentCapturedHandler handler;
  private final SubscriptionLifecycle lifecycle;
  private final RefundService refunds;

  RazorpayWebhookService(
      JdbcClient jdbc,
      Clock clock,
      ObjectMapper mapper,
      PaymentProvider provider,
      PaymentCapturedHandler handler,
      SubscriptionLifecycle lifecycle,
      RefundService refunds) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.mapper = mapper;
    this.provider = provider;
    this.handler = handler;
    this.lifecycle = lifecycle;
    this.refunds = refunds;
  }

  @Transactional
  String receive(byte[] rawBody, String signature, String eventIdHeader) {
    if (!provider.webhookConfigured()) {
      // 503 so Razorpay retries later instead of dropping the event
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Webhook receiver is not configured.");
    }
    if (!provider.verifyWebhookSignature(rawBody, signature)) {
      log.warn("Rejected a webhook with an invalid signature");
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid signature.");
    }
    JsonNode event;
    try {
      event = mapper.readTree(rawBody);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Malformed event.");
    }
    String type = event.path("event").asString("");
    String eventId =
        eventIdHeader != null && EVENT_ID.matcher(eventIdHeader).matches()
            ? eventIdHeader
            : sha256(rawBody);

    int inserted =
        jdbc.sql(
                "insert into billing.webhook_events (provider, event_id, event_type, received_at, payload) values ('razorpay', :id, :t, :now, :p) on conflict do nothing")
            .param("id", eventId)
            .param("t", type.length() > 60 ? type.substring(0, 60) : type)
            .param("now", Timestamp.from(clock.instant()))
            .param("p", redacted(event, rawBody))
            .update();
    if (inserted == 0) {
      boolean done =
          jdbc.sql(
                  "select processed_at is not null from billing.webhook_events where provider = 'razorpay' and event_id = :id")
              .param("id", eventId)
              .query(Boolean.class)
              .single();
      if (done) {
        return "duplicate";
      }
    }

    String outcome =
        switch (type) {
          case "payment.captured", "order.paid" -> captured(event);
          case "payment.failed" -> failed(event);
          case "subscription.charged" -> subscriptionCharged(event);
          case "refund.processed", "refund.failed" -> refundEvent(type, event);
          default ->
              SubscriptionLifecycle.handles(type) ? subscriptionEvent(type, event) : "ignored";
        };
    jdbc.sql(
            "update billing.webhook_events set processed_at = :now, outcome = :o where provider = 'razorpay' and event_id = :id")
        .param("now", Timestamp.from(clock.instant()))
        .param("o", outcome)
        .param("id", eventId)
        .update();
    return outcome;
  }

  /**
   * Payer details the provider includes but we never need (data minimisation): dropped before
   * storing.
   */
  static final java.util.Set<String> PERSONAL =
      java.util.Set.of(
          "email",
          "contact",
          "card",
          "card_id",
          "token_id",
          "vpa",
          "wallet",
          "bank",
          "acquirer_data",
          "customer_id",
          "customer_details",
          "billing_address",
          "shipping_address");

  /**
   * The event as stored: everything except payer details. If the body is not an object we keep it
   * as it came (nothing to redact), which only happens for events we ignore anyway.
   */
  String redacted(JsonNode event, byte[] rawBody) {
    if (!(event instanceof tools.jackson.databind.node.ObjectNode root)) {
      return new String(rawBody, StandardCharsets.UTF_8);
    }
    JsonNode payload = root.path("payload");
    for (var entry : payload.properties()) {
      JsonNode entity = entry.getValue().path("entity");
      if (entity instanceof tools.jackson.databind.node.ObjectNode obj) {
        for (String key : PERSONAL) {
          obj.remove(key);
        }
      }
    }
    return mapper.writeValueAsString(root);
  }

  private String captured(JsonNode event) {
    JsonNode pay = event.path("payload").path("payment").path("entity");
    if (!"captured".equals(pay.path("status").asString(""))) {
      return "ignored_not_captured";
    }
    String paymentId = pay.path("id").asString("");
    String orderId = pay.path("order_id").asString("");
    String currency = pay.path("currency").asString("");
    if (paymentId.isBlank()
        || orderId.isBlank()
        || currency.isBlank()
        || !pay.path("amount").canConvertToLong()) {
      return "ignored_malformed";
    }
    return handler.handle(
        new PaymentCapturedHandler.Captured(
            "razorpay", paymentId, orderId, pay.path("amount").asLong(), currency));
  }

  private String subscriptionCharged(JsonNode event) {
    JsonNode payload = event.path("payload");
    JsonNode pay = payload.path("payment").path("entity");
    String subscriptionId = payload.path("subscription").path("entity").path("id").asString("");
    String paymentId = pay.path("id").asString("");
    String currency = pay.path("currency").asString("");
    if (!"captured".equals(pay.path("status").asString(""))) {
      return "ignored_not_captured";
    }
    if (subscriptionId.isBlank()
        || paymentId.isBlank()
        || currency.isBlank()
        || !pay.path("amount").canConvertToLong()) {
      return "ignored_malformed";
    }
    return handler.subscriptionCharged(
        new PaymentCapturedHandler.Charge(
            "razorpay", subscriptionId, paymentId, pay.path("amount").asLong(), currency));
  }

  private String refundEvent(String type, JsonNode event) {
    String refundId = event.path("payload").path("refund").path("entity").path("id").asString("");
    return refundId.isBlank() ? "ignored_malformed" : refunds.providerEvent(type, refundId);
  }

  private String subscriptionEvent(String type, JsonNode event) {
    String subscriptionId =
        event.path("payload").path("subscription").path("entity").path("id").asString("");
    return subscriptionId.isBlank() ? "ignored_malformed" : lifecycle.apply(type, subscriptionId);
  }

  private String failed(JsonNode event) {
    String orderId =
        event.path("payload").path("payment").path("entity").path("order_id").asString("");
    return orderId.isBlank() ? "ignored_malformed" : handler.failed("razorpay", orderId);
  }

  private static String sha256(byte[] body) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
