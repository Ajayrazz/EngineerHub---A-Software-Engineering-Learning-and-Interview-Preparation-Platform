package com.engineerhub.shared.messaging;

import static com.engineerhub.shared.messaging.MessagingTestSupport.await;
import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import com.engineerhub.testsupport.TestEventConsumer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Consumer-side guarantees: duplicates, retries, dead-lettering and poison messages. */
@IntegrationTest
class IdempotentConsumerIT {

  private static final String DLQ = MessagingTopology.deadLetterQueueName(TestEventConsumer.QUEUE);

  @Autowired MessagingTestSupport support;
  @Autowired TestEventConsumer consumer;
  @Autowired OutboxPublisher publisher;
  @Autowired RabbitTemplate rabbit;
  @Autowired AmqpAdmin admin;
  @Autowired EventCodec codec;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager txm;

  @BeforeEach
  void clean() {
    support.reset();
  }

  private UUID publishViaOutbox(String type, Map<String, Object> payload) {
    return new TransactionTemplate(txm).execute(s -> publisher.publish(type, 1, "t", "1", payload));
  }

  private long ledgerRows(UUID id) {
    return jdbc.sql(
            "select count(*) from shared.processed_messages where consumer = :c and message_id = :id")
        .param("c", TestEventConsumer.CONSUMER)
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private int dlqDepth() {
    var props = admin.getQueueProperties(DLQ);
    return props == null ? 0 : ((Number) props.get("QUEUE_MESSAGE_COUNT")).intValue();
  }

  private Message receiveDeadLetter() {
    Message m = rabbit.receive(DLQ, 20_000);
    assertThat(m).as("a message in the dead-letter queue").isNotNull();
    return m;
  }

  // -------------------------------------------------------------------------------- duplicates

  @Test
  void theSameEventDeliveredManyTimesHasExactlyOneEffect() {
    UUID id = Ids.newId();
    byte[] body =
        codec.encode(
            new EventEnvelope(
                id, "it.dup", 1, Instant.now(), "t", "1", null, mapper.createObjectNode()));
    for (int i = 0; i < 20; i++) {
      rabbit.send(
          MessagingTopology.EVENTS_EXCHANGE,
          "it.dup",
          MessageBuilder.withBody(body)
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setMessageId(id.toString())
              .build());
    }
    await(() -> consumer.deliveries.get() == 20);
    assertThat(consumer.effects).hasSize(1);
    assertThat(ledgerRows(id)).isEqualTo(1);
  }

  // -------------------------------------------------------------------------------- retries

  @Test
  void aTransientlyFailingHandlerIsRetriedAndStillHasOneEffect() {
    UUID id = publishViaOutbox("it.flaky", Map.of("failFirst", 1));

    await(() -> consumer.effects.size() == 1);
    assertThat(consumer.attempts.get(id).get()).isEqualTo(2); // failed once, succeeded on retry
    assertThat(ledgerRows(id)).isEqualTo(1);
    assertThat(dlqDepth()).isZero();
  }

  @Test
  void aPermanentlyFailingHandlerEndsInTheDeadLetterQueueAfterTheConfiguredAttempts() {
    UUID id = publishViaOutbox("it.broken", Map.of("failFirst", 99));

    Message dead = receiveDeadLetter();
    assertThat(dead.getMessageProperties().getMessageId())
        .isEqualTo(id.toString()); // original message preserved
    assertThat(dead.getMessageProperties().getHeaders()).containsKey("x-death");
    assertThat(codec.decode(dead.getBody()).eventId()).isEqualTo(id);

    assertThat(consumer.attempts.get(id).get())
        .isEqualTo(3); // spring.rabbitmq.listener.simple.retry.max-attempts
    assertThat(consumer.effects).isEmpty();
    assertThat(ledgerRows(id))
        .as("a failed handler must not leave an idempotency row behind")
        .isZero();
    assertThat(
            ((Number) admin.getQueueProperties(TestEventConsumer.QUEUE).get("QUEUE_MESSAGE_COUNT"))
                .intValue())
        .as("no hot-looping on the main queue")
        .isZero();
  }

  // -------------------------------------------------------------------------------- poison
  // messages

  @Test
  void malformedMessagesAreDeadLetteredAndNeverReachTheHandler() {
    byte[] oversized = new byte[EventCodec.MAX_BODY_BYTES + 1];
    java.util.Arrays.fill(oversized, (byte) 'x');
    String missingFields = "{\"eventId\":\"" + Ids.newId() + "\",\"type\":\"it.bad\"}";
    String badType =
        "{\"eventId\":\""
            + Ids.newId()
            + "\",\"type\":\"NOT VALID\",\"version\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"aggregateType\":\"t\",\"aggregateId\":\"1\",\"payload\":{}}";
    List<byte[]> poison =
        new ArrayList<>(
            List.of(
                "this is not json".getBytes(StandardCharsets.UTF_8),
                "{}".getBytes(StandardCharsets.UTF_8),
                missingFields.getBytes(StandardCharsets.UTF_8),
                badType.getBytes(StandardCharsets.UTF_8),
                oversized));

    for (byte[] body : poison) {
      rabbit.send(
          MessagingTopology.EVENTS_EXCHANGE, "it.poison", MessageBuilder.withBody(body).build());
    }

    await(() -> dlqDepth() == poison.size());
    assertThat(consumer.effects).isEmpty();
    assertThat(consumer.attempts).as("handler body never ran").isEmpty();
    assertThat(
            jdbc.sql("select count(*) from shared.processed_messages where consumer = :c")
                .param("c", TestEventConsumer.CONSUMER)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void aGoodEventAfterPoisonMessagesIsStillProcessed() {
    rabbit.send(
        MessagingTopology.EVENTS_EXCHANGE,
        "it.poison",
        MessageBuilder.withBody("garbage".getBytes(StandardCharsets.UTF_8)).build());
    UUID id = publishViaOutbox("it.after.poison", Map.of());

    await(() -> consumer.effects.size() == 1);
    assertThat(consumer.effects.get(0).eventId()).isEqualTo(id);
    await(() -> dlqDepth() == 1);
  }
}
