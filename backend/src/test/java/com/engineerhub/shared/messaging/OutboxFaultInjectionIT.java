package com.engineerhub.shared.messaging;

import static com.engineerhub.shared.messaging.MessagingTestSupport.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.engineerhub.testsupport.IntegrationTest;
import com.engineerhub.testsupport.TestEventConsumer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "The process dies after the broker confirmed a message but before the relay's transaction
 * commits." The row stays pending, so the event is published AGAIN. The consumer must see two
 * deliveries but produce exactly one effect — this is the at-least-once + idempotency contract end
 * to end.
 */
@IntegrationTest
class OutboxFaultInjectionIT {

  @MockitoSpyBean OutboxStore store;
  @Autowired OutboxRelay relay;
  @Autowired OutboxPublisher publisher;
  @Autowired MessagingTestSupport support;
  @Autowired TestEventConsumer consumer;
  @Autowired PlatformTransactionManager txm;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void clean() {
    support.reset();
  }

  @Test
  void crashBetweenBrokerConfirmAndCommitCausesADuplicateDeliveryButASingleEffect() {
    // Other tests share this outbox; an event still in flight would consume the injected crash
    // instead
    // of this test's own event. Start from a drained outbox.
    await(() -> support.pending() == 0);
    relay.setPaused(true);
    doThrow(new RuntimeException("simulated crash after the broker confirmed, before commit"))
        .doCallRealMethod()
        .when(store)
        .markPublished(any(), any());
    UUID id =
        new TransactionTemplate(txm)
            .execute(s -> publisher.publish("it.crash", 1, "t", "1", Map.of("n", 1)));

    relay.setPaused(false);
    await(() -> consumer.deliveries.get() >= 2); // published, "crashed", published again
    await(() -> support.pending() == 0);

    assertThat(consumer.deliveries.get()).as("duplicate delivery").isEqualTo(2);
    assertThat(consumer.effects).as("but exactly one effect").hasSize(1);
    assertThat(consumer.effects.get(0).eventId()).isEqualTo(id);
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.processed_messages where consumer = :c and message_id = :id")
                .param("c", TestEventConsumer.CONSUMER)
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1L);
    doCallRealMethod().when(store).markPublished(any(), any());
  }
}
