package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.BillingDtos.AccessView;
import com.engineerhub.billing.internal.BillingDtos.BillingSummary;
import com.engineerhub.billing.internal.BillingDtos.EntitlementsRequest;
import com.engineerhub.billing.internal.BillingDtos.GrantRequest;
import com.engineerhub.billing.internal.BillingDtos.PlanCreate;
import com.engineerhub.billing.internal.BillingDtos.PlanPatch;
import com.engineerhub.billing.internal.BillingDtos.PlanView;
import com.engineerhub.billing.internal.BillingDtos.PriceCreate;
import com.engineerhub.billing.internal.BillingDtos.PricePatch;
import com.engineerhub.billing.internal.BillingDtos.RevokeRequest;
import com.engineerhub.billing.internal.TaxDtos.TaxRuleCreate;
import com.engineerhub.billing.internal.TaxDtos.TaxRulePatch;
import com.engineerhub.billing.internal.TaxDtos.TaxSettingsRequest;
import com.engineerhub.billing.internal.TaxDtos.TaxSettingsView;
import com.engineerhub.identity.UserDirectory;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public price list and the caller's own access. Prices are tax-inclusive final amounts (docs/21
 * §5).
 */
@RestController
@RequestMapping("/api/v1")
class PublicBillingController {

  private final BillingService service;
  private final UserDirectory directory;

  PublicBillingController(BillingService service, UserDirectory directory) {
    this.service = service;
    this.directory = directory;
  }

  /**
   * Active plans that have at least one active price. Empty until the owner configures plans (P-7).
   */
  @GetMapping("/plans")
  @PermitAll
  ResponseEntity<List<PlanView>> plans() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
        .body(service.plans(true));
  }

  @GetMapping("/me/access")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<AccessView> myAccess(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.access(directory.current(authentication).id()));
  }

  /** The caller's billing page: validity, status, renewal window and payment history. */
  @GetMapping("/me/billing")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<BillingSummary> myBilling(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.summary(directory.current(authentication).id()));
  }
}

/** Plan and price administration: needs the global {@code billing.manage}. */
@RestController
@RequestMapping("/api/v1/admin/billing")
class BillingAdminController {

  private final BillingService service;

  BillingAdminController(BillingService service) {
    this.service = service;
  }

  @GetMapping("/plans")
  @PreAuthorize("@authz.can('billing.manage')")
  List<PlanView> plans() {
    return service.plans(false);
  }

  @PostMapping("/plans")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('billing.manage')")
  PlanView create(Authentication actor, @Valid @RequestBody PlanCreate request) {
    return service.createPlan(actor, request);
  }

  @PatchMapping("/plans/{planId}")
  @PreAuthorize("@authz.can('billing.manage')")
  PlanView patch(
      Authentication actor, @PathVariable UUID planId, @Valid @RequestBody PlanPatch request) {
    return service.patchPlan(actor, planId, request);
  }

  @PutMapping("/plans/{planId}/entitlements")
  @PreAuthorize("@authz.can('billing.manage')")
  PlanView entitlements(
      Authentication actor,
      @PathVariable UUID planId,
      @Valid @RequestBody EntitlementsRequest request) {
    return service.setEntitlements(actor, planId, request.codes());
  }

  @PostMapping("/plans/{planId}/prices")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('billing.manage')")
  PlanView addPrice(
      Authentication actor, @PathVariable UUID planId, @Valid @RequestBody PriceCreate request) {
    return service.addPrice(actor, planId, request);
  }

  @PatchMapping("/plans/{planId}/prices/{priceId}")
  @PreAuthorize("@authz.can('billing.manage')")
  PlanView price(
      Authentication actor,
      @PathVariable UUID planId,
      @PathVariable UUID priceId,
      @Valid @RequestBody PricePatch request) {
    return service.setPriceActive(actor, planId, priceId, request.active());
  }

  @GetMapping("/users/{userId}/access")
  @PreAuthorize("@authz.can('billing.manage')")
  AccessView access(@PathVariable UUID userId) {
    return service.access(userId);
  }

  @PostMapping("/users/{userId}/access-grants")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('billing.manage')")
  AccessView grant(
      Authentication actor, @PathVariable UUID userId, @Valid @RequestBody GrantRequest request) {
    return service.grant(actor, userId, request);
  }

  @PostMapping("/access-periods/{periodId}/revoke")
  @PreAuthorize("@authz.can('billing.manage')")
  AccessView revoke(
      Authentication actor,
      @PathVariable UUID periodId,
      @Valid @RequestBody RevokeRequest request) {
    return service.revoke(actor, periodId, request.reason());
  }
}

/**
 * Tax configuration and the owner's own receipts. Tax settings need the global {@code
 * billing.manage}; a document is downloadable only by the owner of the payment.
 */
@RestController
@RequestMapping("/api/v1")
class TaxAndDocumentController {

  private final TaxService tax;
  private final DocumentService documents;

  TaxAndDocumentController(TaxService tax, DocumentService documents) {
    this.tax = tax;
    this.documents = documents;
  }

  @GetMapping("/admin/billing/tax")
  @PreAuthorize("@authz.can('billing.manage')")
  TaxSettingsView settings() {
    return tax.get();
  }

  @PutMapping("/admin/billing/tax")
  @PreAuthorize("@authz.can('billing.manage')")
  TaxSettingsView update(Authentication actor, @Valid @RequestBody TaxSettingsRequest request) {
    return tax.update(actor, request);
  }

  @PostMapping("/admin/billing/tax/rules")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('billing.manage')")
  TaxSettingsView addRule(Authentication actor, @Valid @RequestBody TaxRuleCreate request) {
    return tax.addRule(actor, request);
  }

  @PatchMapping("/admin/billing/tax/rules/{ruleId}")
  @PreAuthorize("@authz.can('billing.manage')")
  TaxSettingsView rule(
      Authentication actor, @PathVariable UUID ruleId, @Valid @RequestBody TaxRulePatch request) {
    return tax.setRuleActive(actor, ruleId, request.active());
  }

  /** The receipt or tax invoice of one of the caller's own payments, as a PDF download. */
  @GetMapping("/me/payments/{paymentId}/document")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<byte[]> document(Authentication authentication, @PathVariable UUID paymentId) {
    DocumentService.PdfFile f = documents.pdfFor(authentication, paymentId);
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            org.springframework.http.ContentDisposition.attachment()
                .filename(f.filename())
                .build()
                .toString())
        .cacheControl(CacheControl.noStore())
        .body(f.bytes());
  }
}
