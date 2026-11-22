package com.engineerhub.shared.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes pending outbox rows to RabbitMQ.
 *
 * <p><b>Guarantees:</b> at-least-once. A row is marked published only after the broker confirmed
 * the message, in the same transaction that holds the row locks. If the process dies after the
 * broker confirmed but before that transaction commits, the row is published again (a duplicate) —
 * which is why every consumer goes through {@link IdempotentEventHandler}. No event is ever lost
 * once its business transaction committed.
 *
 * <p><b>Ordering:</b> best effort only (oldest first, but concurrent transactions can commit out of
 * creation order and several relays may run). Consumers must not depend on global order.
 *
 * <p>Safe to run in several processes at once ({@code FOR UPDATE SKIP LOCKED}).
 */
@Component
@ConditionalOnProperty(
    name = "engineerhub.outbox.relay.enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ROUNDS_PER_POLL = 20;

  private final OutboxStore store;
  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;
  private final EventCodec codec;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final MessagingProperties props;
  private final Counter published;
  private final Counter failures;
  private final Counter unroutable;
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

  /** Set when a publish failed during the current run (broker down, nack, timeout). */
  private final AtomicBoolean runHadFailure = new AtomicBoolean(false);

  private volatile int consecutiveFailedRuns = 0;
  private volatile Instant notBefore = Instant.MIN;

  OutboxRelay(
      OutboxStore store,
      RabbitTemplate rabbit,
      ObjectMapper mapper,
      EventCodec codec,
      PlatformTransactionManager txManager,
      Clock clock,
      MessagingProperties props,
      MeterRegistry meters) {
    this.store = store;
    this.rabbit = rabbit;
    this.mapper = mapper;
    this.codec = codec;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
    this.props = props;
    this.published =
        Counter.builder("eh.outbox.published")
            .description("Outbox events confirmed by the broker")
            .register(meters);
    this.failures =
        Counter.builder("eh.outbox.publish.failures")
            .description("Failed outbox publish attempts")
            .register(meters);
    this.unroutable =
        Counter.builder("eh.outbox.unroutable")
            .description("Events no queue was bound for")
            .register(meters);
    Gauge.builder("eh.outbox.pending", store, OutboxStore::countPending)
        .description("Outbox events waiting to be published")
        .register(meters);
  }

  /**
   * Operational switch (maintenance, broker migration, tests). Pending events simply accumulate.
   */
  void setPaused(boolean value) {
    paused.set(value);
  }

  @Scheduled(fixedDelayString = "${engineerhub.outbox.relay.poll-interval}")
  void poll() {
    if (paused.get() || clock.instant().isBefore(notBefore)) {
      return; // paused, or backing off after failures
    }
    runHadFailure.set(false);
    try {
      publishPending();
    } catch (Exception e) {
      // Never let an exception escape the scheduler thread: the next poll retries.
      failures.increment();
      runHadFailure.set(true);
      if (consecutiveFailedRuns == 0) {
        log.error("Outbox relay run failed: {}", sanitize(e));
      }
    }
    if (runHadFailure.get()) {
      consecutiveFailedRuns++;
      // Exponential backoff (poll interval x 2^n, capped): a broker outage must not be hammered
      // with
      // reconnects twice a second or flood the logs. Pending events simply wait in PostgreSQL.
      long factor = 1L << Math.min(consecutiveFailedRuns, 10);
      Duration delay = props.relay().pollInterval().multipliedBy(factor);
      notBefore = clock.instant().plus(delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay);
    } else if (consecutiveFailedRuns > 0) {
      log.info("Outbox relay recovered after {} failed runs", consecutiveFailedRuns);
      consecutiveFailedRuns = 0;
      notBefore = Instant.MIN;
    }
  }

  /** Hourly retention purge of rows that were published long ago. */
  @Scheduled(cron = "0 7 * * * *")
  void purge() {
    try {
      int removed = store.deletePublishedBefore(clock.instant().minus(props.retention()));
      if (removed > 0) {
        log.info("Outbox purge removed {} published rows", removed);
      }
    } catch (Exception e) {
      log.warn("Outbox purge failed: {}", sanitize(e));
    }
  }

  /** Publishes batches until the outbox is drained (bounded per call). Returns events published. */
  int publishPending() {
    int total = 0;
    for (int round = 0; round < MAX_ROUNDS_PER_POLL; round++) {
      Integer n = tx.execute(status -> publishOneBatch());
      int done = n == null ? 0 : n;
      total += done;
      if (done < props.relay().batchSize()) {
        break;
      }
    }
    return total;
  }

  /** One transaction: lock rows, publish + confirm each, mark the confirmed ones published. */
  private int publishOneBatch() {
    List<OutboxRow> rows = store.lockPending(props.relay().batchSize());
    List<UUID> confirmed = new ArrayList<>();
    for (OutboxRow row : rows) {
      try {
        send(row);
        confirmed.add(row.id());
      } catch (Exception e) {
        // Broker down / nack / timeout: remember why, keep this and later rows pending, retry next
        // poll.
        failures.increment();
        runHadFailure.set(true);
        store.recordFailure(row.id(), sanitize(e));
        if (consecutiveFailedRuns
            == 0) { // log the first failure of a streak; the rest is in outbox.last_error
          log.warn(
              "Outbox publish failed for event {} ({}): {}",
              row.id(),
              row.eventType(),
              sanitize(e));
        }
        break;
      }
    }
    if (!confirmed.isEmpty()) {
      // If this update (or the commit) fails, the transaction rolls back and the batch is
      // re-published:
      // duplicates, never losses.
      store.markPublished(confirmed, clock.instant());
      published.increment(confirmed.size());
    }
    return confirmed.size();
  }

  private void send(OutboxRow row) throws Exception {
    EventEnvelope envelope =
        new EventEnvelope(
            row.id(),
            row.eventType(),
            row.eventVersion(),
            row.occurredAt(),
            row.aggregateType(),
            row.aggregateId(),
            row.correlationId(),
            mapper.readTree(row.payloadJson()));
    MessageBuilderSupport<Message> builder =
        MessageBuilder.withBody(codec.encode(envelope))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setContentEncoding("UTF-8")
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT) // survives a broker restart
            .setMessageId(row.id().toString())
            .setType(row.eventType())
            .setHeader("eh-event-version", row.eventVersion());
    if (row.correlationId() != null) {
      builder
          .setCorrelationId(row.correlationId())
          .setHeader("X-Correlation-Id", row.correlationId());
    }
    Message message = builder.build();

    CorrelationData confirmation = new CorrelationData(row.id().toString());
    rabbit.send(MessagingTopology.EVENTS_EXCHANGE, row.eventType(), message, confirmation);
    CorrelationData.Confirm confirm =
        confirmation
            .getFuture()
            .get(props.relay().confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
    if (!confirm.ack()) {
      throw new IllegalStateException("broker nacked the message");
    }
    if (confirmation.getReturned() != null) {
      // Published and confirmed, but no queue was bound for this routing key: nobody subscribes
      // (yet).
      unroutable.increment();
      log.warn("Event {} ({}) was unroutable: no queue is bound for it", row.id(), row.eventType());
    }
  }

  /** Short, single-line failure text with any credentials in URLs removed. */
  static String sanitize(Throwable e) {
    String text = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
    text = text.replaceAll("://[^/@\\s]+@", "://***@").replaceAll("[\\r\\n]+", " ");
    return text.length() > 300 ? text.substring(0, 300) : text;
  }
}
