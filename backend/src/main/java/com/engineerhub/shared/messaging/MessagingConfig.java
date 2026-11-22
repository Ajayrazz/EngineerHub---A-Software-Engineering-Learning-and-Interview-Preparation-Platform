package com.engineerhub.shared.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
class MessagingConfig {

  /**
   * Malformed messages can never succeed, so retrying them only delays the dead-letter queue and
   * blocks the consumer thread behind the backoff. Anything that signals "reject, do not requeue"
   * anywhere in its cause chain skips the retries and is dead-lettered immediately; ordinary
   * handler failures are still retried with backoff.
   */
  @Bean
  RabbitListenerRetrySettingsCustomizer skipRetriesForRejectedMessages() {
    return settings ->
        settings.setExceptionPredicate(
            failure -> {
              for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
                if (t instanceof AmqpRejectAndDontRequeueException) {
                  return false;
                }
              }
              return true;
            });
  }
}
