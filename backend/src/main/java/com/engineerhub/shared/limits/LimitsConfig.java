package com.engineerhub.shared.limits;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LimitsProperties.class)
class LimitsConfig {

  @Bean
  RateLimitFilter rateLimitFilter(
      RateLimiter limiter, LimitsProperties props, MeterRegistry meters, ObjectMapper mapper) {
    return new RateLimitFilter(limiter, props.rate(), meters, mapper);
  }

  /**
   * The filter runs inside the security chain (after authentication), not as a plain servlet
   * filter.
   */
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }
}
