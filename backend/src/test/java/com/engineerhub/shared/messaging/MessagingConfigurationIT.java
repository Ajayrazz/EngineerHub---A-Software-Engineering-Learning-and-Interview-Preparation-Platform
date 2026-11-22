package com.engineerhub.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.testsupport.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;

/**
 * Spring silently ignores property names it does not know. Boot 4 renamed the listener retry
 * property ({@code max-attempts} became {@code max-retries}) and the old name did nothing, giving 4
 * attempts instead of 3. These assertions read the RESOLVED values so a mistyped or renamed
 * property fails the build.
 */
@IntegrationTest
class MessagingConfigurationIT {

  @Autowired RabbitProperties rabbit;
  @Autowired MessagingProperties outbox;
  @Autowired CachingConnectionFactory connectionFactory;

  @Test
  void listenerReliabilitySettingsAreActuallyApplied() {
    var simple = rabbit.getListener().getSimple();
    assertThat(simple.getRetry().isEnabled()).isTrue();
    assertThat(simple.getRetry().getMaxRetries()).isEqualTo(2); // => 3 attempts in total
    assertThat(simple.getRetry().getInitialInterval()).isEqualTo(Duration.ofMillis(500));
    assertThat(simple.getRetry().getMultiplier()).isEqualTo(2.0);
    assertThat(simple.getRetry().getMaxInterval()).isEqualTo(Duration.ofSeconds(5));
    assertThat(simple.getDefaultRequeueRejected()).isFalse();
    assertThat(simple.getPrefetch()).isEqualTo(10);
    assertThat(simple.getAcknowledgeMode().name()).isEqualTo("AUTO");
  }

  @Test
  void publisherConfirmsAndReturnsAreEnabled() {
    assertThat(rabbit.getPublisherConfirmType())
        .isEqualTo(CachingConnectionFactory.ConfirmType.CORRELATED);
    assertThat(rabbit.isPublisherReturns()).isTrue();
    assertThat(rabbit.getTemplate().getMandatory()).isTrue();
    assertThat(connectionFactory.isPublisherConfirms()).isTrue();
    assertThat(connectionFactory.isPublisherReturns()).isTrue();
  }

  @Test
  void outboxRelayDefaultsAreTheDocumentedOnes() {
    assertThat(outbox.relay().enabled()).isTrue();
    assertThat(outbox.relay().batchSize()).isEqualTo(50);
    assertThat(outbox.relay().confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(outbox.retention()).isEqualTo(Duration.ofDays(7));
  }
}
