package com.engineerhub.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * One throw-away RabbitMQ (same version as the dev stack, management plugin on) shared by every
 * integration test in the JVM. A non-"guest" admin is used because the application refuses to start
 * with the default account.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestConfig {

  public static final String ADMIN_USER = "eh_test";
  public static final String ADMIN_PASSWORD = "eh_test_pw";

  private static final RabbitMQContainer RABBIT =
      new RabbitMQContainer("rabbitmq:4.3.8-management")
          .withAdminUser(ADMIN_USER)
          .withAdminPassword(ADMIN_PASSWORD);

  static {
    RABBIT.start(); // stopped by Testcontainers' Ryuk when the JVM exits
  }

  public static RabbitMQContainer container() {
    return RABBIT;
  }

  @Bean
  @ServiceConnection
  RabbitMQContainer rabbit() {
    return RABBIT;
  }
}
