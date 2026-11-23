package com.engineerhub.analytics.internal;

import com.engineerhub.shared.messaging.EventEnvelope;
import com.engineerhub.shared.messaging.IdempotentEventHandler;
import com.engineerhub.shared.messaging.MessagingTopology;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Consumes the learning module's events (through the idempotent handler) and updates the
 * statistics.
 */
@Configuration(proxyBeanMethods = false)
class AnalyticsQueues {

  static final String QUEUE = "analytics.progress";

  @Bean
  Declarables analyticsProgressQueue() {
    return MessagingTopology.consumerQueue(QUEUE, "learning.#");
  }
}

@Component
class ProgressEventListener {

  private static final Logger log = LoggerFactory.getLogger(ProgressEventListener.class);
  static final String CONSUMER = "analytics.progress";

  private final IdempotentEventHandler handler;
  private final AnalyticsService service;

  ProgressEventListener(IdempotentEventHandler handler, AnalyticsService service) {
    this.handler = handler;
    this.service = service;
  }

  @RabbitListener(queues = AnalyticsQueues.QUEUE)
  void on(Message message) {
    handler.handle(CONSUMER, message, this::apply);
  }

  private void apply(EventEnvelope e) {
    switch (e.type()) {
      case "learning.lesson.completed" ->
          service.onLessonCompleted(
              uuid(e, "userId"), uuid(e, "lessonId"), uuid(e, "courseId"), e.occurredAt());
      case "learning.course.completed" ->
          service.onCourseCompleted(uuid(e, "userId"), uuid(e, "courseId"), e.occurredAt());
      default ->
          log.debug(
              "Ignoring event {}",
              e.type()); // forward compatible: new learning.* events are not an error
    }
  }

  private static UUID uuid(EventEnvelope e, String field) {
    var node = e.payload() == null ? null : e.payload().get(field);
    if (node == null || node.isNull()) {
      throw new IllegalArgumentException("event " + e.eventId() + " lacks " + field);
    }
    return UUID.fromString(node.asString());
  }
}
