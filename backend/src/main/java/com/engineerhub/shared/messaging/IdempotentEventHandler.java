package com.engineerhub.shared.messaging;

import com.engineerhub.shared.idempotency.IdempotencyService;
import com.engineerhub.shared.web.CorrelationId;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps every event consumer to turn at-least-once delivery into exactly-once <em>effect</em>.
 *
 * <p>Usage in a module's listener:
 *
 * <pre>{@code
 * @RabbitListener(queues = "analytics.lesson-events")
 * void on(Message message) {
 *   handler.handle("analytics", message, event -> { ... update tables ... });
 * }
 * }</pre>
 *
 * <p>The idempotency ledger row and the handler's database changes commit or roll back together. If
 * the handler throws, the transaction rolls back (ledger row included), the container retries, and
 * after the configured attempts the message is dead-lettered. Side effects <em>outside</em> the
 * database (e-mail, HTTP calls) are not covered: make those idempotent themselves or trigger them
 * via a further event.
 */
@Service
public class IdempotentEventHandler {

  private static final Logger log = LoggerFactory.getLogger(IdempotentEventHandler.class);

  private final EventCodec codec;
  private final IdempotencyService idempotency;

  IdempotentEventHandler(EventCodec codec, IdempotencyService idempotency) {
    this.codec = codec;
    this.idempotency = idempotency;
  }

  /**
   * @param consumer stable logical name of the consuming component (ledger namespace)
   * @return {@code true} if {@code work} ran, {@code false} if this event was already processed
   * @throws AmqpRejectAndDontRequeueException for malformed messages (dead-lettered, never "fixed"
   *     by retries)
   */
  @Transactional
  public boolean handle(String consumer, Message message, Consumer<EventEnvelope> work) {
    EventEnvelope event;
    try {
      event = codec.decode(message.getBody());
    } catch (EventCodec.MalformedEventException e) {
      throw new AmqpRejectAndDontRequeueException("malformed event: " + e.getMessage(), e);
    }

    String previous = CorrelationId.current();
    if (event.correlationId() != null) {
      MDC.put(
          CorrelationId.MDC_KEY,
          event.correlationId()); // logs of this handler join the originating request's trace
    }
    try {
      if (!idempotency.firstDelivery(consumer, event.eventId())) {
        log.debug("Duplicate event {} ({}) ignored by {}", event.eventId(), event.type(), consumer);
        return false;
      }
      work.accept(event);
      return true;
    } finally {
      if (previous == null) {
        MDC.remove(CorrelationId.MDC_KEY);
      } else {
        MDC.put(CorrelationId.MDC_KEY, previous);
      }
    }
  }
}
