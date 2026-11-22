package com.engineerhub.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.engineerhub.shared.id.Ids;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class EventCodecTest {

  private final JsonMapper mapper =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private final EventCodec codec = new EventCodec(mapper);

  record LessonDone(String user, int score) {}

  private EventEnvelope sample() {
    return new EventEnvelope(
        Ids.newId(),
        "lesson.completed",
        2,
        Instant.parse("2026-11-23T01:00:00Z"),
        "lesson",
        "L1",
        "corr-12345678",
        mapper.valueToTree(new LessonDone("u1", 9)));
  }

  @Test
  void roundTripsAnEnvelope() {
    EventEnvelope in = sample();
    EventEnvelope out = codec.decode(codec.encode(in));
    assertThat(out).isEqualTo(in);
    assertThat(codec.payload(out, LessonDone.class)).isEqualTo(new LessonDone("u1", 9));
  }

  @Test
  void toleratesUnknownFieldsForForwardCompatibility() {
    String json =
        "{\"eventId\":\"%s\",\"type\":\"a.b\",\"version\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"aggregateType\":\"t\",\"aggregateId\":\"1\",\"payload\":{\"user\":\"u\",\"score\":1,\"future\":true},\"someNewField\":1}"
            .formatted(Ids.newId());
    EventEnvelope e = codec.decode(json.getBytes(StandardCharsets.UTF_8));
    assertThat(codec.payload(e, LessonDone.class).user()).isEqualTo("u");
  }

  @Test
  void rejectsEverythingThatIsNotAValidEnvelope() {
    String id = Ids.newId().toString();
    String ok =
        "\"eventId\":\""
            + id
            + "\",\"type\":\"a.b\",\"version\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"aggregateType\":\"t\",\"aggregateId\":\"1\",\"payload\":{}";
    String[] bad = {
      "",
      "null",
      "[]",
      "not json",
      "{}",
      "{" + ok.replace("\"payload\":{}", "\"payload\":null") + "}",
      "{" + ok.replace("\"payload\":{}", "\"payload\":[1]") + "}",
      "{" + ok.replace("\"payload\":{}", "\"payload\":\"text\"") + "}",
      "{" + ok.replace("a.b", "NOT VALID") + "}",
      "{" + ok.replace("\"version\":1", "\"version\":0") + "}",
      "{" + ok.replace("\"eventId\":\"" + id + "\",", "") + "}",
      "{" + ok.replace("\"aggregateId\":\"1\",", "") + "}",
      "{" + ok.replace(id, "not-a-uuid") + "}",
    };
    for (String body : bad) {
      assertThatThrownBy(() -> codec.decode(body.getBytes(StandardCharsets.UTF_8)))
          .as(body)
          .isInstanceOf(EventCodec.MalformedEventException.class);
    }
    assertThatThrownBy(() -> codec.decode(null))
        .isInstanceOf(EventCodec.MalformedEventException.class);
    assertThatThrownBy(() -> codec.decode(new byte[EventCodec.MAX_BODY_BYTES + 1]))
        .isInstanceOf(EventCodec.MalformedEventException.class);
  }

  @Test
  void errorMessagesNeverEchoTheOffendingContent() {
    assertThatThrownBy(
            () -> codec.decode("{\"secret\":\"hunter2\"".getBytes(StandardCharsets.UTF_8)))
        .hasMessageNotContaining("hunter2");
  }

  @Test
  void eventTypePatternAcceptsOnlyDottedLowerCaseNames() {
    for (String ok :
        new String[] {"a.b", "lesson.completed", "payment.refund.requested", "x1.y2"}) {
      assertThat(EventCodec.EVENT_TYPE.matcher(ok).matches()).as(ok).isTrue();
    }
    for (String bad :
        new String[] {
          "", "a", "A.b", "a.B", "a..b", ".a", "a.", "a b.c", "a-b.c", "1a.b", "a.b#", "a.*"
        }) {
      assertThat(EventCodec.EVENT_TYPE.matcher(bad).matches()).as(bad).isFalse();
    }
  }
}
