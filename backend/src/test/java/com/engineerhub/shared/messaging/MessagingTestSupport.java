package com.engineerhub.shared.messaging;

import com.engineerhub.testsupport.TestEventConsumer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared helpers for the messaging integration tests (same package: may use package-private types).
 */
@Component
class MessagingTestSupport {

  private final JdbcClient jdbc;
  private final AmqpAdmin admin;
  private final OutboxRelay relay;
  private final TestEventConsumer consumer;
  private final OutboxPublisher publisher;
  private final TransactionTemplate tx;

  MessagingTestSupport(
      JdbcClient jdbc,
      AmqpAdmin admin,
      OutboxRelay relay,
      TestEventConsumer consumer,
      OutboxPublisher publisher,
      PlatformTransactionManager txm) {
    this.jdbc = jdbc;
    this.admin = admin;
    this.relay = relay;
    this.consumer = consumer;
    this.publisher = publisher;
    this.tx = new TransactionTemplate(txm);
  }

  /** Clean slate: empty outbox and queues, relay running, consumer counters reset. */
  void reset() {
    relay.setPaused(false);
    jdbc.sql("delete from shared.outbox").update();
    jdbc.sql("delete from shared.processed_messages where consumer = :c")
        .param("c", TestEventConsumer.CONSUMER)
        .update();
    admin.purgeQueue(TestEventConsumer.QUEUE, true);
    admin.purgeQueue(MessagingTopology.deadLetterQueueName(TestEventConsumer.QUEUE), true);
    consumer.reset();
  }

  /** Publishes {@code count} events in one business transaction; returns their ids in order. */
  List<UUID> publishInOneTransaction(int count, String type) {
    return tx.execute(
        status -> {
          List<UUID> ids = new ArrayList<>();
          for (int i = 0; i < count; i++) {
            ids.add(publisher.publish(type, 1, "test", "agg-" + i, Map.of("n", i)));
          }
          return ids;
        });
  }

  long pending() {
    return jdbc.sql("select count(*) from shared.outbox where published_at is null")
        .query(Long.class)
        .single();
  }

  long total() {
    return jdbc.sql("select count(*) from shared.outbox").query(Long.class).single();
  }

  static void await(Supplier<Boolean> condition) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(50))
        .until(condition::get);
  }
}
