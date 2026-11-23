package com.engineerhub.billing.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider subscription events that change state but never grant access (access comes only from
 * {@link PaymentCapturedHandler#subscriptionCharged}). Delivery is at-least-once and unordered, so
 * every transition is a guarded update: it applies only from the states listed, which means a late
 * or repeated event can never regress a subscription (for example "activated" after "cancelled").
 * There is deliberately no grace period yet (Pending Decision P-2): access ends at the paid-through
 * date whatever the status says.
 */
@Service
class SubscriptionLifecycle {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycle.class);

  private record Transition(String to, List<String> from) {}

  private static final List<String> LIVE =
      List.of("created", "pending", "active", "past_due", "cancel_scheduled");

  private final JdbcClient jdbc;
  private final Clock clock;

  SubscriptionLifecycle(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  private static Transition transition(String event) {
    return switch (event) {
      case "subscription.activated" ->
          new Transition("active", List.of("created", "pending", "past_due"));
      // a renewal charge failed (provider retries) or the provider gave up: access continues only
      // until the paid-through date
      case "subscription.pending", "subscription.halted" ->
          new Transition("past_due", List.of("active"));
      case "subscription.cancelled" -> new Transition("cancelled", LIVE);
      case "subscription.completed" ->
          new Transition("completed", List.of("active", "cancel_scheduled", "past_due"));
      default -> null; // authenticated, paused, resumed, updated, ...: nothing to change
    };
  }

  static boolean handles(String event) {
    return event.startsWith("subscription.") && !event.equals("subscription.charged");
  }

  /**
   * @return applied, ignored_no_transition, ignored_event, unknown_subscription
   */
  @Transactional
  String apply(String event, String subscriptionRef) {
    Transition t = transition(event);
    if (t == null) {
      return "ignored_event";
    }
    boolean known =
        jdbc.sql(
                    "select count(*) from billing.subscriptions where provider = 'razorpay' and provider_subscription_ref = :r")
                .param("r", subscriptionRef)
                .query(Long.class)
                .single()
            > 0;
    if (!known) {
      log.warn("Subscription event {} for an unknown subscription ignored", event);
      return "unknown_subscription";
    }
    int n =
        jdbc.sql(
                "update billing.subscriptions set status = :to, updated_at = :now where provider = 'razorpay' and provider_subscription_ref = :r and status in (:from)")
            .param("to", t.to())
            .param("now", Timestamp.from(clock.instant()))
            .param("r", subscriptionRef)
            .param("from", t.from())
            .update();
    return n > 0 ? "applied" : "ignored_no_transition";
  }
}
