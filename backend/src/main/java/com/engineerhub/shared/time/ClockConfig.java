package com.engineerhub.shared.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single injectable time source (UTC). Code must take a {@link Clock}, never call {@code now()}
 * directly.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
