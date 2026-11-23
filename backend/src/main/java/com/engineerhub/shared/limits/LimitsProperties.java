package com.engineerhub.shared.limits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code engineerhub.limits.*}: request-size cap and per-principal rate limits. */
@Validated
@ConfigurationProperties("engineerhub.limits")
record LimitsProperties(@Min(1024) long maxRequestBytes, @Valid @NotNull Rate rate) {

  record Rate(
      boolean enabled,
      @NotNull Duration window,
      @Min(1) int readPerWindow,
      @Min(1) int writePerWindow,
      @Min(1) int adminPerWindow) {}
}
