package com.engineerhub.billing.internal;

import com.engineerhub.shared.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly reconciliation. Single-instance by design today: should two instances ever run, the
 * database allows only one 'running' run at a time and the other simply skips.
 */
@Component
@ConditionalOnProperty(
    name = "engineerhub.billing.reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true)
class ReconciliationScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

  private final ReconciliationService service;

  ReconciliationScheduler(ReconciliationService service) {
    this.service = service;
  }

  @Scheduled(cron = "${engineerhub.billing.reconciliation.cron:0 30 2 * * *}")
  void nightly() {
    try {
      var run = service.run("scheduled");
      log.info("Reconciliation finished: {}", run.status());
    } catch (ApiException e) {
      log.info("Reconciliation skipped: {}", e.getMessage());
    }
  }
}
