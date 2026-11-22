package com.engineerhub.shared.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code engineerhub.outbox.*}: relay behaviour and retention of published outbox rows. */
@Validated
@ConfigurationProperties("engineerhub.outbox")
record MessagingProperties(@Valid @NotNull Relay relay, @NotNull Duration retention) {

  record Relay(
      boolean enabled,
      @NotNull Duration pollInterval,
      @Min(1) @Max(1000) int batchSize,
      @NotNull Duration confirmTimeout) {}
}
