package com.engineerhub.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * The app must refuse to start with incomplete security configuration (fail fast, no insecure
 * defaults).
 */
class SecurityPropertiesTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SecurityProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(Config.class);

  @Test
  void startsWithACompleteConfiguration() {
    runner
        .withPropertyValues(
            "engineerhub.security.jwt.issuer-uri=https://idp.example/realms/x",
            "engineerhub.security.jwt.audience=engineerhub-api",
            "engineerhub.security.content-security-policy=default-src 'none'")
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @Test
  void failsWithoutAnIssuer() {
    runner
        .withPropertyValues(
            "engineerhub.security.jwt.issuer-uri=",
            "engineerhub.security.jwt.audience=engineerhub-api",
            "engineerhub.security.content-security-policy=default-src 'none'")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void failsWithoutAnAudience() {
    runner
        .withPropertyValues(
            "engineerhub.security.jwt.issuer-uri=https://idp.example/realms/x",
            "engineerhub.security.jwt.audience=",
            "engineerhub.security.content-security-policy=default-src 'none'")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void blankCorsOriginsMeansNoCors() {
    runner
        .withPropertyValues(
            "engineerhub.security.jwt.issuer-uri=https://idp.example/realms/x",
            "engineerhub.security.jwt.audience=engineerhub-api",
            "engineerhub.security.content-security-policy=default-src 'none'",
            "engineerhub.security.cors.allowed-origins=")
        .run(
            ctx ->
                assertThat(ctx.getBean(SecurityProperties.class).cors().allowedOrigins())
                    .isEmpty());
  }
}
