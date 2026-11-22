package com.engineerhub.shared.messaging;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.stereotype.Component;

/**
 * Fail-fast for broker configuration, like the database and Keycloak settings: the application
 * refuses to start rather than silently falling back to RabbitMQ's well-known {@code guest/guest}
 * on localhost, and only allows an unencrypted connection to a local broker.
 */
@Component
class RabbitConnectionGuard implements InitializingBean {

  private static final List<String> LOCAL = List.of("localhost", "127.0.0.1", "::1", "[::1]");

  private final RabbitConnectionDetails details;
  private final RabbitProperties properties;

  RabbitConnectionGuard(RabbitConnectionDetails details, RabbitProperties properties) {
    this.details = details;
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    if (details.getAddresses().isEmpty()
        || details.getAddresses().stream().anyMatch(a -> a.host() == null || a.host().isBlank())) {
      throw new IllegalStateException("RabbitMQ host is not configured (set RABBITMQ_HOST)");
    }
    String user = details.getUsername();
    if (user == null
        || user.isBlank()
        || details.getPassword() == null
        || details.getPassword().isBlank()) {
      throw new IllegalStateException(
          "RabbitMQ credentials are not configured (set RABBITMQ_USER and RABBITMQ_PASSWORD)");
    }
    if ("guest".equalsIgnoreCase(user)) {
      throw new IllegalStateException("The default RabbitMQ 'guest' account must not be used");
    }
    boolean allLocal = details.getAddresses().stream().allMatch(a -> LOCAL.contains(a.host()));
    if (!allLocal && !properties.getSsl().determineEnabled()) {
      throw new IllegalStateException(
          "RabbitMQ over plain TCP is only allowed for localhost (set RABBITMQ_SSL=true)");
    }
  }
}
