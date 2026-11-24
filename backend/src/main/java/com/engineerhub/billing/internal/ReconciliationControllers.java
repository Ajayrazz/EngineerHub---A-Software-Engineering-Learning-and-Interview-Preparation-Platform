package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.ReconciliationDtos.GateItemRequest;
import com.engineerhub.billing.internal.ReconciliationDtos.GateReadyRequest;
import com.engineerhub.billing.internal.ReconciliationDtos.LiveGateView;
import com.engineerhub.billing.internal.ReconciliationDtos.ReconciliationOverview;
import com.engineerhub.billing.internal.ReconciliationDtos.ReconciliationRunView;
import com.engineerhub.billing.internal.ReconciliationDtos.ResolveRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reconciliation (billing managers) and the Live Readiness Gate (readable by billing managers,
 * changeable only with the global {@code settings.manage}, which only SUPER_ADMIN holds).
 */
@RestController
@RequestMapping("/api/v1/admin/billing")
class ReconciliationController {

  private final ReconciliationService reconciliation;
  private final LiveGateService gate;

  ReconciliationController(ReconciliationService reconciliation, LiveGateService gate) {
    this.reconciliation = reconciliation;
    this.gate = gate;
  }

  @GetMapping("/reconciliation")
  @PreAuthorize("@authz.can('billing.manage')")
  ReconciliationOverview overview() {
    return reconciliation.overview();
  }

  @PostMapping("/reconciliation/run")
  @PreAuthorize("@authz.can('billing.manage')")
  ReconciliationRunView run() {
    return reconciliation.run("manual");
  }

  @PostMapping("/reconciliation/findings/{id}/apply")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@authz.can('billing.manage')")
  void apply(Authentication actor, @PathVariable UUID id) {
    reconciliation.apply(actor, id);
  }

  @PostMapping("/reconciliation/findings/{id}/resolve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@authz.can('billing.manage')")
  void resolve(
      Authentication actor, @PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
    reconciliation.acknowledge(actor, id, request.note());
  }

  @GetMapping("/live-gate")
  @PreAuthorize("@authz.can('billing.manage')")
  LiveGateView liveGate() {
    return gate.view();
  }

  @PutMapping("/live-gate/items/{code}")
  @PreAuthorize("@authz.can('settings.manage')")
  LiveGateView item(
      Authentication actor,
      @PathVariable String code,
      @Valid @RequestBody GateItemRequest request) {
    return gate.setItem(actor, code, Boolean.TRUE.equals(request.done()), request.note());
  }

  @PostMapping("/live-gate/ready")
  @PreAuthorize("@authz.can('settings.manage')")
  LiveGateView ready(Authentication actor, @Valid @RequestBody GateReadyRequest request) {
    return gate.setReady(actor, Boolean.TRUE.equals(request.ready()));
  }
}
