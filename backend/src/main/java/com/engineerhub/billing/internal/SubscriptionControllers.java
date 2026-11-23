package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.SubscriptionDtos.SubscribeRequest;
import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionCheckout;
import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionConfirmRequest;
import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionConfirmView;
import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionView;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auto-renewing subscriptions of the signed-in user. Ownership comes from the token; someone else's
 * subscription looks exactly like a missing one. Nothing here grants access.
 */
@RestController
@RequestMapping("/api/v1/billing/subscriptions")
class SubscriptionController {

  private final SubscriptionService service;

  SubscriptionController(SubscriptionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  SubscriptionCheckout start(
      Authentication authentication,
      @Valid @RequestBody SubscribeRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return service.start(authentication, request.planPriceId(), idempotencyKey);
  }

  @PostMapping("/{id}/confirm")
  @PreAuthorize("isAuthenticated()")
  SubscriptionConfirmView confirm(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody SubscriptionConfirmRequest request) {
    return service.confirm(
        authentication,
        id,
        request.razorpaySubscriptionId(),
        request.razorpayPaymentId(),
        request.razorpaySignature());
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<SubscriptionView> view(Authentication authentication, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.view(authentication, id));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  SubscriptionView cancel(Authentication authentication, @PathVariable UUID id) {
    return service.cancel(authentication, id);
  }
}
