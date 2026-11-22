package com.engineerhub.shared.messaging;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology. Events go to one durable topic exchange; each consuming module owns a durable
 * <b>quorum</b> queue bound by routing-key pattern, with a dead-letter queue behind it.
 *
 * <p>A consumer's queue must be declared when the application starts (it is, via {@link
 * #consumerQueue}), because a topic exchange silently drops messages that match no queue.
 */
public final class MessagingTopology {

  public static final String EVENTS_EXCHANGE = "eh.domain.events";
  public static final String DEAD_LETTER_EXCHANGE = "eh.dlx";

  private MessagingTopology() {}

  public static String deadLetterQueueName(String queue) {
    return queue + ".dlq";
  }

  /**
   * Declarables for one consumer: its queue (bound to the events exchange for each routing-key
   * pattern, e.g. {@code lesson.*} or {@code #}) and its dead-letter queue.
   */
  public static Declarables consumerQueue(String queueName, String... routingKeyPatterns) {
    TopicExchange events = new TopicExchange(EVENTS_EXCHANGE, true, false);
    DirectExchange dlx = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    Queue queue =
        QueueBuilder.durable(queueName)
            .quorum()
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(queueName)
            .build();
    Queue dlq = QueueBuilder.durable(deadLetterQueueName(queueName)).quorum().build();

    List<Declarable> all = new ArrayList<>();
    all.add(queue);
    all.add(dlq);
    all.add(BindingBuilder.bind(dlq).to(dlx).with(queueName));
    for (String pattern : routingKeyPatterns) {
      Binding binding = BindingBuilder.bind(queue).to(events).with(pattern);
      all.add(binding);
    }
    return new Declarables(all);
  }

  /** The exchanges themselves (declared once, idempotently). */
  @Configuration(proxyBeanMethods = false)
  static class Exchanges {

    @Bean
    TopicExchange eventsExchange() {
      return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
      return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
  }
}
