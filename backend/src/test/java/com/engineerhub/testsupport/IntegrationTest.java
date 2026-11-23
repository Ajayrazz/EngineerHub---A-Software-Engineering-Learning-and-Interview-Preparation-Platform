package com.engineerhub.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context (real security chain, real Flyway migrations, real PostgreSQL 18 and
 * RabbitMQ in Docker). Name such classes {@code *IT}: they run in {@code mvn verify}, not in the
 * fast unit phase.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  PostgresTestConfig.class,
  RabbitTestConfig.class,
  RedisTestConfig.class,
  TestMessagingConfig.class
})
public @interface IntegrationTest {}
