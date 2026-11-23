package com.engineerhub.shared.limits;

import java.time.Duration;

/** Counts requests per key within a window. */
public interface RateLimiter {

  /**
   * @param retryAfterSeconds when not allowed: seconds until the window resets
   */
  record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {}

  Decision tryAcquire(String key, int limit, Duration window);
}
