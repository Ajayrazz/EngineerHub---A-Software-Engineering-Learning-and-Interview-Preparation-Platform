package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.PurchaseDtos.CheckoutView;
import com.engineerhub.billing.internal.PurchaseDtos.ConfirmRequest;
import com.engineerhub.billing.internal.PurchaseDtos.ConfirmView;
import com.engineerhub.billing.internal.PurchaseDtos.IntentView;
import com.engineerhub.billing.internal.PurchaseDtos.PaymentsConfig;
import com.engineerhub.billing.internal.PurchaseDtos.PurchaseRequest;
import com.engineerhub.billing.internal.PurchaseDtos.QuoteView;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quote, start, confirm and status of a purchase. Starting needs login; nothing here grants access.
 */
@RestController
@RequestMapping("/api/v1/billing")
class PurchaseController {

  private final PurchaseService service;

  PurchaseController(PurchaseService service) {
    this.service = service;
  }

  @GetMapping("/config")
  @PermitAll
  ResponseEntity<PaymentsConfig> config() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new PaymentsConfig(service.paymentsEnabled(), "test"));
  }

  /** The final amount the user will pay (tax-inclusive), decided by the server. */
  @GetMapping("/quote")
  @PermitAll
  ResponseEntity<QuoteView> quote(@RequestParam UUID planPriceId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.quote(planPriceId));
  }

  @PostMapping("/purchases")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  CheckoutView start(
      Authentication authentication,
      @Valid @RequestBody PurchaseRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return service.start(authentication, request.planPriceId(), idempotencyKey);
  }

  @PostMapping("/purchases/{intentId}/confirm")
  @PreAuthorize("isAuthenticated()")
  ConfirmView confirm(
      Authentication authentication,
      @PathVariable UUID intentId,
      @Valid @RequestBody ConfirmRequest request) {
    return service.confirm(
        authentication,
        intentId,
        request.razorpayOrderId(),
        request.razorpayPaymentId(),
        request.razorpaySignature());
  }

  @GetMapping("/purchases/{intentId}")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<IntentView> status(Authentication authentication, @PathVariable UUID intentId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.intent(authentication, intentId));
  }
}

/**
 * Server-to-server callback from Razorpay. Authenticated by its HMAC signature over the raw body,
 * not by a login.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
class RazorpayWebhookController {

  private final RazorpayWebhookService service;

  RazorpayWebhookController(RazorpayWebhookService service) {
    this.service = service;
  }

  @PostMapping("/razorpay")
  @PermitAll
  Map<String, String> receive(
      @RequestBody byte[] body,
      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
      @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
    return Map.of("status", service.receive(body, signature, eventId));
  }
}
