package com.engineerhub.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.testsupport.IntegrationTest;
import com.engineerhub.testsupport.RabbitTestConfig;
import com.engineerhub.testsupport.TestEventConsumer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The broker really holds the topology we declared: durable exchanges, quorum queues, DLX wiring.
 */
@IntegrationTest
class MessagingTopologyIT {

  @Autowired ObjectMapper mapper;

  private JsonNode api(String path) throws Exception {
    String auth =
        Base64.getEncoder()
            .encodeToString(
                (RabbitTestConfig.ADMIN_USER + ":" + RabbitTestConfig.ADMIN_PASSWORD)
                    .getBytes(StandardCharsets.UTF_8));
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create(RabbitTestConfig.container().getHttpUrl() + "/api" + path))
                    .header("Authorization", "Basic " + auth)
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path).isEqualTo(200);
    return mapper.readTree(response.body());
  }

  @Test
  void exchangesAreDurableAndTypedAsDesigned() throws Exception {
    JsonNode events = api("/exchanges/%2F/" + MessagingTopology.EVENTS_EXCHANGE);
    assertThat(events.get("type").asString()).isEqualTo("topic");
    assertThat(events.get("durable").asBoolean()).isTrue();
    assertThat(events.get("auto_delete").asBoolean()).isFalse();

    JsonNode dlx = api("/exchanges/%2F/" + MessagingTopology.DEAD_LETTER_EXCHANGE);
    assertThat(dlx.get("type").asString()).isEqualTo("direct");
    assertThat(dlx.get("durable").asBoolean()).isTrue();
  }

  @Test
  void consumerQueuesAreDurableQuorumQueuesDeadLetteringToTheDlx() throws Exception {
    JsonNode queue = api("/queues/%2F/" + TestEventConsumer.QUEUE);
    assertThat(queue.get("durable").asBoolean()).isTrue();
    assertThat(queue.get("arguments").get("x-queue-type").asString()).isEqualTo("quorum");
    assertThat(queue.get("arguments").get("x-dead-letter-exchange").asString())
        .isEqualTo(MessagingTopology.DEAD_LETTER_EXCHANGE);
    assertThat(queue.get("arguments").get("x-dead-letter-routing-key").asString())
        .isEqualTo(TestEventConsumer.QUEUE);

    JsonNode dlq =
        api("/queues/%2F/" + MessagingTopology.deadLetterQueueName(TestEventConsumer.QUEUE));
    assertThat(dlq.get("durable").asBoolean()).isTrue();
    assertThat(dlq.get("arguments").get("x-queue-type").asString()).isEqualTo("quorum");
  }

  @Test
  void bindingsConnectEventsToTheConsumerAndDeadLettersToTheDlq() throws Exception {
    JsonNode eventBindings =
        api(
            "/bindings/%2F/e/"
                + MessagingTopology.EVENTS_EXCHANGE
                + "/q/"
                + TestEventConsumer.QUEUE);
    assertThat(eventBindings.get(0).get("routing_key").asString()).isEqualTo("it.#");

    JsonNode dlBindings =
        api(
            "/bindings/%2F/e/"
                + MessagingTopology.DEAD_LETTER_EXCHANGE
                + "/q/"
                + MessagingTopology.deadLetterQueueName(TestEventConsumer.QUEUE));
    assertThat(dlBindings.get(0).get("routing_key").asString()).isEqualTo(TestEventConsumer.QUEUE);
  }
}
