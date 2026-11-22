package com.engineerhub.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One throw-away PostgreSQL 18 (same major as the dev stack) shared by every integration test in
 * the JVM; Spring Boot wires the datasource to it automatically via {@link ServiceConnection}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  static {
    POSTGRES.start(); // stopped by Testcontainers' Ryuk when the JVM exits
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return POSTGRES;
  }
}
