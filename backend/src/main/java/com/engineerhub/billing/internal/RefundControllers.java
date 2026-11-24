package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.RefundDtos.AdminRefundDetail;
import com.engineerhub.billing.internal.RefundDtos.AdminRefundRow;
import com.engineerhub.billing.internal.RefundDtos.ApproveRequest;
import com.engineerhub.billing.internal.RefundDtos.MyRefundView;
import com.engineerhub.billing.internal.RefundDtos.RefundFlag;
import com.engineerhub.billing.internal.RefundDtos.RefundPolicyView;
import com.engineerhub.billing.internal.RefundDtos.RefundRequestCreate;
import com.engineerhub.billing.internal.RefundDtos.RefundSettingsRequest;
import com.engineerhub.billing.internal.RefundDtos.RefundSettingsView;
import com.engineerhub.billing.internal.RefundDtos.RejectRequest;
import com.engineerhub.billing.internal.RefundDtos.RetryRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Refund requests of the signed-in user, the public policy, and the owner's own credit notes. */
@RestController
@RequestMapping("/api/v1")
class RefundController {

  private final RefundService service;
  private final DocumentService documents;

  RefundController(RefundService service, DocumentService documents) {
    this.service = service;
    this.documents = documents;
  }

  /** The owner's refund policy, or none if it has not been written (nothing is invented). */
  @GetMapping("/billing/refund-policy")
  @PermitAll
  ResponseEntity<RefundPolicyView> policy() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.policy());
  }

  @PostMapping("/billing/refund-requests")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  MyRefundView create(
      Authentication authentication, @Valid @RequestBody RefundRequestCreate request) {
    return service.create(authentication, request.paymentId(), request.reason(), request.details());
  }

  @GetMapping("/me/refund-requests")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<List<MyRefundView>> mine(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.mine(authentication));
  }

  @GetMapping("/me/refunds/{refundId}/credit-note")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<byte[]> creditNote(Authentication authentication, @PathVariable UUID refundId) {
    DocumentService.PdfFile f = documents.creditNotePdf(authentication, refundId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(f.filename()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(f.bytes());
  }
}

/** The refund queue and settings: global {@code billing.manage}. */
@RestController
@RequestMapping("/api/v1/admin/billing")
class RefundAdminController {

  private final RefundService service;

  RefundAdminController(RefundService service) {
    this.service = service;
  }

  @GetMapping("/refund-settings")
  @PreAuthorize("@authz.can('billing.manage')")
  RefundSettingsView settings() {
    return service.settings();
  }

  @PutMapping("/refund-settings")
  @PreAuthorize("@authz.can('billing.manage')")
  RefundSettingsView update(
      Authentication actor, @Valid @RequestBody RefundSettingsRequest request) {
    return service.updateSettings(actor, request);
  }

  @GetMapping("/refunds")
  @PreAuthorize("@authz.can('billing.manage')")
  List<AdminRefundRow> queue(@RequestParam(required = false) String status) {
    return service.queue(status);
  }

  @GetMapping("/refund-flags")
  @PreAuthorize("@authz.can('billing.manage')")
  List<RefundFlag> flags() {
    return service.flags();
  }

  @GetMapping("/refunds/{id}")
  @PreAuthorize("@authz.can('billing.manage')")
  AdminRefundDetail detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/refunds/{id}/review")
  @PreAuthorize("@authz.can('billing.manage')")
  AdminRefundDetail review(Authentication actor, @PathVariable UUID id) {
    return service.review(actor, id);
  }

  @PostMapping("/refunds/{id}/approve")
  @PreAuthorize("@authz.can('billing.manage')")
  AdminRefundDetail approve(
      Authentication actor, @PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
    return service.approve(actor, id, request.amountMinor(), request.note());
  }

  @PostMapping("/refunds/{id}/reject")
  @PreAuthorize("@authz.can('billing.manage')")
  AdminRefundDetail reject(
      Authentication actor, @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
    return service.reject(actor, id, request.note());
  }

  @PostMapping("/refunds/{id}/retry")
  @PreAuthorize("@authz.can('billing.manage')")
  AdminRefundDetail retry(
      Authentication actor, @PathVariable UUID id, @Valid @RequestBody RetryRequest request) {
    return service.retry(actor, id, Boolean.TRUE.equals(request.confirmNotRefundedAtProvider()));
  }
}
