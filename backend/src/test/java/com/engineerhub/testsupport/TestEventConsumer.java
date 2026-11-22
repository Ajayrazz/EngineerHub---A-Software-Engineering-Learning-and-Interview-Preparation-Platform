package com.engineerhub.testsupport;

import com.engineerhub.shared.messaging.EventEnvelope;
import com.engineerhub.shared.messaging.IdempotentEventHandler;
import com.engineerhub.shared.web.CorrelationId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * A recording consumer used only by tests, built exactly like a production consumer (through {@link
 * IdempotentEventHandler}). Payload field {@code failFirst=N} makes the first N handling attempts
 * of that event throw, to exercise retry and dead-lettering.
 */
@Component
public class TestEventConsumer {

  public static final String QUEUE = "it.recorder";
  public static final String CONSUMER = "it.recorder";

  private final IdempotentEventHandler handler;

  /** Every message the listener received, duplicates included. */
  public final AtomicInteger deliveries = new AtomicInteger();

  /** Events whose handler body actually ran to completion (the "effects"). */
  public final List<EventEnvelope> effects = new CopyOnWriteArrayList<>();

  /** Handler invocations per event id (retries count). */
  public final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

  /** Correlation id visible (MDC) inside the handler, per event id. */
  public final Map<UUID, String> mdcSeen = new ConcurrentHashMap<>();

  /** The most recent raw AMQP message. */
  public volatile Message lastMessage;

  TestEventConsumer(IdempotentEventHandler handler) {
    this.handler = handler;
  }

  public void reset() {
    deliveries.set(0);
    effects.clear();
    attempts.clear();
    mdcSeen.clear();
    lastMessage = null;
  }

  @RabbitListener(queues = QUEUE)
  void on(Message message) {
    deliveries.incrementAndGet();
    lastMessage = message;
    handler.handle(CONSUMER, message, this::apply);
  }

  private void apply(EventEnvelope event) {
    int attempt =
        attempts.computeIfAbsent(event.eventId(), k -> new AtomicInteger()).incrementAndGet();
    mdcSeen.put(event.eventId(), String.valueOf(CorrelationId.current()));
    var failFirst = event.payload().get("failFirst");
    if (failFirst != null && attempt <= failFirst.asInt()) {
      throw new IllegalStateException("injected failure, attempt " + attempt);
    }
    effects.add(event);
  }
}
