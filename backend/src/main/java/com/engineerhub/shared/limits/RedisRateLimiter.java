package com.engineerhub.shared.limits;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter in Redis, shared by all API instances. One atomic Lua call increments the
 * counter and arms its expiry on first use, so a crash can never leave a counter without a TTL.
 *
 * <p>Trade-off (documented in ADR-036): a fixed window allows up to 2x the limit across a window
 * boundary; acceptable for abuse protection, not for billing-grade metering.
 */
@Component
class RedisRateLimiter implements RateLimiter {

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static final DefaultRedisScript<List> SCRIPT =
      new DefaultRedisScript<>(
          "local c = redis.call('INCR', KEYS[1]) "
              + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
              + "return {c, redis.call('TTL', KEYS[1])}",
          List.class);

  private final StringRedisTemplate redis;

  RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Decision tryAcquire(String key, int limit, Duration window) {
    List<Long> result =
        (List<Long>)
            redis.execute(SCRIPT, List.of(key), String.valueOf(Math.max(1, window.toSeconds())));
    if (result == null || result.size() < 2) {
      throw new IllegalStateException("unexpected rate limiter response");
    }
    long count = result.get(0);
    long ttl = Math.max(1, result.get(1));
    boolean allowed = count <= limit;
    return new Decision(allowed, limit, (int) Math.max(0, limit - count), allowed ? 0 : ttl);
  }
}
