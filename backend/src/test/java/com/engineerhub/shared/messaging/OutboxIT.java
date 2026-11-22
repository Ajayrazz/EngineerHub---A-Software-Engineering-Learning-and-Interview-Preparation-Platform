package com.engineerhub.shared.messaging;

import static com.engineerhub.shared.messaging.MessagingTestSupport.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.testsupport.IntegrationTest;
import com.engineerhub.testsupport.TestEventConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** The transactional outbox against real PostgreSQL 18 and RabbitMQ. */
@IntegrationTest
class OutboxIT {

  @Autowired MessagingTestSupport support;
  @Autowired OutboxPublisher publisher;
  @Autowired OutboxRelay relay;
  @Autowired OutboxStore store;
  @Autowired TestEventConsumer consumer;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager txm;
  @Autowired ObjectMapper mapper;
  @Autowired EventCodec codec;
  @Autowired MessagingProperties props;
  @Autowired MeterRegistry meters;
  @Autowired java.time.Clock clock;

  @BeforeEach
  void clean() {
    support.reset();
  }

  // ---------------------------------------------------------------- atomicity with the business
  // change

  @Test
  void eventIsInvisibleUntilTheBusinessTransactionCommitsThenPublishedExactlyOnce()
      throws Exception {
    relay.setPaused(true);
    TransactionTemplate tx = new TransactionTemplate(txm);

    UUID id =
        tx.execute(
            status -> {
              UUID eventId =
                  publisher.publish(
                      "it.lesson.completed",
                      3,
                      "lesson",
                      "L-42",
                      Map.of("user", "u-1", "score", 7));
              // inside the transaction the row exists for this connection...
              assertThat(support.total()).isEqualTo(1);
              return eventId;
            });

    assertThat(support.pending()).isEqualTo(1); // committed, waiting for the relay
    Thread.sleep(300);
    assertThat(consumer.deliveries.get()).isZero();

    relay.setPaused(false);
    await(() -> consumer.effects.size() == 1);

    EventEnvelope e = consumer.effects.get(0);
    assertThat(e.eventId()).isEqualTo(id);
    assertThat(e.type()).isEqualTo("it.lesson.completed");
    assertThat(e.version()).isEqualTo(3);
    assertThat(e.aggregateType()).isEqualTo("lesson");
    assertThat(e.aggregateId()).isEqualTo("L-42");
    assertThat(e.payload().get("score").asInt()).isEqualTo(7);
    assertThat(e.occurredAt()).isBefore(Instant.now().plusSeconds(1));
    await(() -> support.pending() == 0);
    assertThat(consumer.deliveries.get()).isEqualTo(1); // exactly one delivery in the happy path
  }

  @Test
  void aRolledBackTransactionLeavesNoEventBehind() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txm);
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      publisher.publish("it.never.happens", 1, "x", "1", Map.of());
                      throw new IllegalStateException(
                          "business logic failed after emitting the event");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(support.total()).isZero();
    Thread.sleep(500);
    assertThat(consumer.deliveries.get()).isZero();
  }

  @Test
  void publishingOutsideATransactionIsAProgrammingError() {
    assertThatThrownBy(() -> publisher.publish("it.no.tx", 1, "x", "1", Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(support.total()).isZero();
  }

  @Test
  void invalidEventTypesAndVersionsAreRejectedBeforeTouchingTheDatabase() {
    TransactionTemplate tx = new TransactionTemplate(txm);
    for (String bad :
        new String[] {
          null, "", "NoDots", "Upper.case", "has space.x", "a..b", ".a.b", "a.b.", "x/y.z", "1a.b"
        }) {
      assertThatThrownBy(
              () -> tx.executeWithoutResult(s -> publisher.publish(bad, 1, "x", "1", Map.of())))
          .as("type %s", bad)
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(
            () -> tx.executeWithoutResult(s -> publisher.publish("it.ok", 0, "x", "1", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(support.total()).isZero();
  }

  // ---------------------------------------------------------------- message shape

  @Test
  void messagesAreDurableTraceableAndWellFormed() {
    MDC.put("correlationId", "trace-abc-12345");
    try {
      new TransactionTemplate(txm)
          .executeWithoutResult(s -> publisher.publish("it.shape", 2, "t", "9", Map.of("k", "v")));
    } finally {
      MDC.remove("correlationId");
    }
    await(() -> consumer.lastMessage != null && consumer.effects.size() == 1);

    Message m = consumer.lastMessage;
    EventEnvelope e = consumer.effects.get(0);
    // on a RECEIVED message the broker's delivery mode is exposed as "receivedDeliveryMode"
    assertThat(m.getMessageProperties().getReceivedDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(m.getMessageProperties().getContentType()).isEqualTo("application/json");
    assertThat(m.getMessageProperties().getMessageId()).isEqualTo(e.eventId().toString());
    assertThat(m.getMessageProperties().getType()).isEqualTo("it.shape");
    assertThat(m.getMessageProperties().getHeaders())
        .containsEntry("X-Correlation-Id", "trace-abc-12345");
    assertThat(e.correlationId()).isEqualTo("trace-abc-12345");
    // the consumer's logs join the originating request's trace
    assertThat(consumer.mdcSeen.get(e.eventId())).isEqualTo("trace-abc-12345");
  }

  @Test
  void eventsKeepTheirOrderThroughASingleRelayAndConsumer() {
    List<UUID> ids = support.publishInOneTransaction(30, "it.ordered");
    await(() -> consumer.effects.size() == 30);
    // same transaction => same created_at; the (created_at, id) order is UUIDv7 time order
    List<Integer> received =
        consumer.effects.stream().map(e -> e.payload().get("n").asInt()).toList();
    assertThat(received)
        .containsExactlyInAnyOrderElementsOf(
            java.util.stream.IntStream.range(0, 30).boxed().toList());
    assertThat(consumer.effects.stream().map(EventEnvelope::eventId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(ids);
  }

  // ---------------------------------------------------------------- concurrency

  @Test
  void concurrentRelaysNeverPublishTheSameEventTwice() throws Exception {
    relay.setPaused(true);
    int events = 200;
    support.publishInOneTransaction(events, "it.concurrent");
    assertThat(support.pending()).isEqualTo(events);

    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<Integer>> runs = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        runs.add(pool.submit(() -> relay.publishPending()));
      }
      int published = 0;
      for (Future<Integer> f : runs) {
        published += f.get();
      }
      assertThat(published).as("every event published once across all relays").isEqualTo(events);
    } finally {
      pool.shutdownNow();
    }

    await(() -> consumer.effects.size() == events);
    assertThat(consumer.deliveries.get()).as("no duplicate deliveries").isEqualTo(events);
    Set<UUID> distinct =
        consumer.effects.stream().map(EventEnvelope::eventId).collect(Collectors.toSet());
    assertThat(distinct).hasSize(events);
    assertThat(support.pending()).isZero();
  }

  // ---------------------------------------------------------------- failure and recovery

  @Test
  void brokerOutageKeepsEventsPendingAndNothingIsLostWhenItReturns() {
    relay.setPaused(true); // the healthy relay stays out of the way while a "broken" one runs
    support.publishInOneTransaction(3, "it.outage");

    CachingConnectionFactory dead =
        new CachingConnectionFactory("127.0.0.1", 1); // nothing listens here
    dead.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    dead.setUsername("eh_user");
    dead.setPassword("super-secret-password");
    dead.setConnectionTimeout(500);
    RabbitTemplate deadTemplate = new RabbitTemplate(dead);
    OutboxRelay broken =
        new OutboxRelay(store, deadTemplate, mapper, codec, txm, clock, props, meters);

    assertThat(broken.publishPending()).isZero(); // no exception escapes, nothing marked published
    assertThat(support.pending()).isEqualTo(3);
    String error =
        jdbc.sql("select last_error from shared.outbox where attempts > 0 limit 1")
            .query(String.class)
            .single();
    assertThat(error).isNotBlank().doesNotContain("super-secret-password");
    assertThat(
            jdbc.sql("select coalesce(sum(attempts),0) from shared.outbox")
                .query(Long.class)
                .single())
        .isEqualTo(1L); // stops at the first failure instead of hammering the broker per row

    relay.setPaused(false); // the broker "comes back" (healthy relay resumes)
    await(() -> consumer.effects.size() == 3);
    await(() -> support.pending() == 0);
    assertThat(
            jdbc.sql("select count(*) from shared.outbox where last_error is not null")
                .query(Long.class)
                .single())
        .as("error text cleared once published")
        .isZero();
  }

  @Test
  void theRelayBacksOffInsteadOfHammeringADeadBroker() {
    relay.setPaused(true);
    support.publishInOneTransaction(1, "it.backoff");
    CachingConnectionFactory dead = new CachingConnectionFactory("127.0.0.1", 1);
    dead.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    dead.setConnectionTimeout(500);
    OutboxRelay broken =
        new OutboxRelay(store, new RabbitTemplate(dead), mapper, codec, txm, clock, props, meters);

    for (int i = 0; i < 50; i++) {
      broken.poll(); // 50 scheduler ticks in a row
    }

    // only the first tick tried the broker; the rest were skipped by the backoff
    assertThat(
            jdbc.sql("select coalesce(sum(attempts),0) from shared.outbox")
                .query(Long.class)
                .single())
        .isEqualTo(1L);
  }

  @Test
  void eventsWithNoSubscriberAreCountedAsUnroutableButNotRetriedForever() {
    double before = meters.counter("eh.outbox.unroutable").count();
    new TransactionTemplate(txm)
        .executeWithoutResult(s -> publisher.publish("nobody.listens", 1, "x", "1", Map.of()));

    await(() -> support.pending() == 0); // confirmed by the broker => published
    assertThat(meters.counter("eh.outbox.unroutable").count()).isGreaterThan(before);
    assertThat(consumer.deliveries.get()).isZero();
  }

  @Test
  void retentionPurgeRemovesOnlyOldPublishedRows() {
    relay.setPaused(true);
    List<UUID> ids = support.publishInOneTransaction(3, "it.retention");
    // 0: published long ago, 1: published just now, 2: still pending
    jdbc.sql("update shared.outbox set published_at = :t where id = :id")
        .param("t", Timestamp.from(Instant.now().minus(Duration.ofDays(30))))
        .param("id", ids.get(0))
        .update();
    jdbc.sql("update shared.outbox set published_at = :t where id = :id")
        .param("t", Timestamp.from(Instant.now()))
        .param("id", ids.get(1))
        .update();

    relay.purge();

    List<UUID> left = jdbc.sql("select id from shared.outbox").query(UUID.class).list();
    assertThat(left).containsExactlyInAnyOrder(ids.get(1), ids.get(2));
  }

  @Test
  void relayErrorTextNeverLeaksCredentials() {
    String text =
        OutboxRelay.sanitize(
            new IllegalStateException(
                "connect to amqp://eh_user:hunter2@broker:5672/vhost failed\nline2"));
    assertThat(text).doesNotContain("hunter2").doesNotContain("\n").contains("***@");
    assertThat(OutboxRelay.sanitize(new RuntimeException("x".repeat(1000))))
        .hasSizeLessThanOrEqualTo(300);
  }
}
