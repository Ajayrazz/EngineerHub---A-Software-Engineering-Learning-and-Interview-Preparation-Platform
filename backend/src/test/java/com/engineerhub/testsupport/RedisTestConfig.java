package com.engineerhub.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** One throw-away Redis (same major as the dev stack) shared by every integration test. */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestConfig {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8")).withExposedPorts(6379);

  static {
    REDIS.start();
  }

  public static GenericContainer<?> container() {
    return REDIS;
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redis() {
    return REDIS;
  }
}
