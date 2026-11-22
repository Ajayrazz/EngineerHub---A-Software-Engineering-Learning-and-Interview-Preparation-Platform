package com.engineerhub.shared.messaging;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Encodes/decodes {@link EventEnvelope}s and validates them: nothing malformed reaches a handler.
 */
@Component
public class EventCodec {

  /** {@code <domain>.<event>[.<more>]}, lower-case, digits allowed after the first character. */
  static final Pattern EVENT_TYPE = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$");

  /** Bound the size of what a consumer will parse (events are small facts, not documents). */
  static final int MAX_BODY_BYTES = 256 * 1024;

  private final ObjectMapper mapper;

  EventCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  byte[] encode(EventEnvelope envelope) {
    return mapper.writeValueAsBytes(envelope);
  }

  /**
   * @throws MalformedEventException if the body is not a valid envelope (a poison message).
   */
  public EventEnvelope decode(byte[] body) {
    if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
      throw new MalformedEventException("event body missing or too large");
    }
    EventEnvelope e;
    try {
      e = mapper.readValue(body, EventEnvelope.class);
    } catch (JacksonException ex) {
      throw new MalformedEventException("event body is not a valid envelope");
    }
    if (e == null
        || e.eventId() == null
        || e.type() == null
        || !EVENT_TYPE.matcher(e.type()).matches()
        || e.version() < 1
        || e.occurredAt() == null
        || e.aggregateType() == null
        || e.aggregateId() == null
        || e.payload() == null
        || !e.payload().isObject()) { // JSON null / array / scalar payloads are not allowed
      throw new MalformedEventException("event envelope is missing required fields");
    }
    return e;
  }

  /** Converts an envelope's payload to the consumer's own DTO (unknown fields are ignored). */
  public <T> T payload(EventEnvelope envelope, Class<T> type) {
    try {
      return mapper.treeToValue(envelope.payload(), type);
    } catch (JacksonException ex) {
      throw new MalformedEventException("event payload does not match " + type.getSimpleName());
    }
  }

  /** Raised for messages that can never succeed; they are dead-lettered without retries. */
  public static class MalformedEventException extends RuntimeException {
    MalformedEventException(String message) {
      super(message);
    }
  }
}
