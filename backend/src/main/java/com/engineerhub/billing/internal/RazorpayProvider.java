package com.engineerhub.billing.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
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
