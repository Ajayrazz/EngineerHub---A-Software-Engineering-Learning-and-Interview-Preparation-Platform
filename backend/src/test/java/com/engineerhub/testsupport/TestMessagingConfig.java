package com.engineerhub.testsupport;

import com.engineerhub.shared.messaging.MessagingTopology;
import org.springframework.amqp.core.Declarables;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Declares the queue (and dead-letter queue) of {@link TestEventConsumer}, like a real module
 * would.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMessagingConfig {

  @Bean
  Declarables testRecorderQueue() {
    return MessagingTopology.consumerQueue(TestEventConsumer.QUEUE, "it.#");
  }
}
