package com.engineerhub.billing.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Razorpay Orders API over HTTPS with short timeouts. Responses and errors are never echoed to
 * callers.
 */
@Component
class RazorpayProvider implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(RazorpayProvider.class);
  static final String BASE = "https://api.razorpay.com/v1";

  private final RazorpayProperties props;
  private final RestClient http;

  RazorpayProvider(RazorpayProperties props) {
    this.props = props;
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(8));
    this.http = RestClient.builder().requestFactory(factory).baseUrl(BASE).build();
  }

  @Override
  public String name() {
    return "razorpay";
  }

  @Override
  public boolean configured() {
    return props.checkoutConfigured();
  }

  @Override
  public String publicKeyId() {
    return props.keyId();
  }

  @Override
  public ProviderOrder createOrder(
      long amountMinor, String currency, String receipt, Map<String, String> notes) {
    if (!configured()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Online payment is not available yet.");
    }
    try {
      JsonNode r =
          http.post()
              .uri("/orders")
              .headers(h -> h.setBasicAuth(props.keyId(), props.keySecret()))
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Map.of(
                      "amount",
                      amountMinor,
                      "currency",
                      currency,
                      "receipt",
                      receipt,
                      "notes",
                      notes))
              .retrieve()
              .body(JsonNode.class);
      if (r == null
          || !r.hasNonNull("id")
          || r.path("amount").asLong(-1) != amountMinor
          || !currency.equals(r.path("currency").asString())) {
        throw new RestClientException("unexpected order response");
      }
      return new ProviderOrder(r.get("id").asString(), amountMinor, currency);
    } catch (RestClientException e) {
      log.warn(
          "Razorpay order creation failed: {}",
          e.getClass().getSimpleName()); // class only: bodies may hold identifiers
      throw new ApiException(
          ErrorCode.SERVICE_UNAVAILABLE,
          "The payment provider is temporarily unavailable. Please retry shortly.",
          e);
    }
  }

  @Override
  public String createPlan(String period, long amountMinor, String currency, String name) {
    requireConfigured();
    JsonNode r =
        call(
            "/plans",
            Map.of(
                "period",
                "year".equals(period) ? "yearly" : "monthly",
                "interval",
                1,
                "item",
                Map.of("name", name, "amount", amountMinor, "currency", currency)));
    if (r == null
        || !r.hasNonNull("id")
        || r.path("item").path("amount").asLong(-1) != amountMinor) {
      throw unavailable(new RestClientException("unexpected plan response"));
    }
    return r.get("id").asString();
  }

  @Override
  public ProviderSubscription createSubscription(
      String planRef, int totalCycles, Map<String, String> notes) {
    requireConfigured();
    JsonNode r =
        call(
            "/subscriptions",
            Map.of("plan_id", planRef, "total_count", totalCycles, "notes", notes));
    if (r == null || !r.hasNonNull("id") || !planRef.equals(r.path("plan_id").asString())) {
      throw unavailable(new RestClientException("unexpected subscription response"));
    }
    return new ProviderSubscription(r.get("id").asString());
  }

  @Override
  public void cancelSubscription(String subscriptionRef) {
    requireConfigured();
    call("/subscriptions/" + subscriptionRef + "/cancel", Map.of("cancel_at_cycle_end", 0));
  }

  @Override
  public boolean verifySubscriptionSignature(
      String subscriptionRef, String paymentId, String signature) {
    return RazorpaySignatures.subscriptionValid(
        props.keySecret(), subscriptionRef, paymentId, signature);
  }

  @Override
  public ProviderRefund refundPayment(
      String paymentRef, long amountMinor, Map<String, String> notes) {
    requireConfigured();
    JsonNode r =
        call("/payments/" + paymentRef + "/refund", Map.of("amount", amountMinor, "notes", notes));
    if (r == null
        || !r.hasNonNull("id")
        || r.path("amount").asLong(-1) != amountMinor
        || !paymentRef.equals(r.path("payment_id").asString())) {
      throw unavailable(new RestClientException("unexpected refund response"));
    }
    return new ProviderRefund(r.get("id").asString(), r.path("status").asString("pending"));
  }

  @Override
  public Listing<ProviderPayment> listPayments(
      java.time.Instant from, java.time.Instant to, int maxItems) {
    requireConfigured();
    List<ProviderPayment> out = new ArrayList<>();
    boolean complete =
        page(
            "/payments",
            from,
            to,
            maxItems,
            item -> {
              out.add(
                  new ProviderPayment(
                      item.path("id").asString(""),
                      item.path("amount").asLong(0),
                      item.path("currency").asString(""),
                      item.path("status").asString(""),
                      item.path("order_id").asString(""),
                      item.path("amount_refunded").asLong(0)));
            });
    return new Listing<>(out, complete);
  }

  @Override
  public Listing<ProviderRefundRow> listRefunds(
      java.time.Instant from, java.time.Instant to, int maxItems) {
    requireConfigured();
    List<ProviderRefundRow> out = new ArrayList<>();
    boolean complete =
        page(
            "/refunds",
            from,
            to,
            maxItems,
            item -> {
              out.add(
                  new ProviderRefundRow(
                      item.path("id").asString(""),
                      item.path("payment_id").asString(""),
                      item.path("amount").asLong(0),
                      item.path("status").asString("")));
            });
    return new Listing<>(out, complete);
  }

  /** Pages through a provider listing; false when it stopped at {@code maxItems} with more left. */
  private boolean page(
      String path,
      java.time.Instant from,
      java.time.Instant to,
      int maxItems,
      java.util.function.Consumer<JsonNode> each) {
    int count = 100;
    int seen = 0;
    while (seen < maxItems) {
      int skip = seen;
      JsonNode r;
      try {
        r =
            http.get()
                .uri(
                    u ->
                        u.path(path)
                            .queryParam("from", from.getEpochSecond())
                            .queryParam("to", to.getEpochSecond())
                            .queryParam("count", count)
                            .queryParam("skip", skip)
                            .build())
                .headers(h -> h.setBasicAuth(props.keyId(), props.keySecret()))
                .retrieve()
                .body(JsonNode.class);
      } catch (RestClientException e) {
        throw unavailable(e);
      }
      JsonNode items = r == null ? null : r.path("items");
      if (items == null || !items.isArray()) {
        throw unavailable(new RestClientException("unexpected listing response"));
      }
      for (JsonNode item : items) {
        each.accept(item);
        seen++;
      }
      if (items.size() < count) {
        return true;
      }
    }
    return false;
  }

  private void requireConfigured() {
    if (!configured()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Online payment is not available yet.");
    }
  }

  private JsonNode call(String path, Map<String, Object> body) {
    try {
      return http.post()
          .uri(path)
          .headers(h -> h.setBasicAuth(props.keyId(), props.keySecret()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException e) {
      throw unavailable(e);
    }
  }

  private static ApiException unavailable(RestClientException e) {
    log.warn("Razorpay call failed: {}", e.getClass().getSimpleName()); // class only
    return new ApiException(
        ErrorCode.SERVICE_UNAVAILABLE,
        "The payment provider is temporarily unavailable. Please retry shortly.",
        e);
  }

  @Override
  public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
    return RazorpaySignatures.checkoutValid(props.keySecret(), orderId, paymentId, signature);
  }

  @Override
  public boolean webhookConfigured() {
    return props.webhookConfigured();
  }

  @Override
  public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
    return RazorpaySignatures.webhookValid(props.webhookSecret(), rawBody, signature);
  }
}
