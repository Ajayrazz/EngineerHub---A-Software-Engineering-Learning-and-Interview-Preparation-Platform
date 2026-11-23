import { createClient } from "redis";
import type { KeyValueStore } from "./store";

/** The one place a Redis client is constructed (so its exact type is derived, never hand-written). */
export function createRedisClient(url: string) {
  return createClient({
    url,
    socket: { reconnectStrategy: (retries) => Math.min(retries * 100, 3000) },
  });
}
export type RedisClient = ReturnType<typeof createRedisClient>;

// Compare-and-delete must be atomic so one request can never release another's lock.
const RELEASE_LOCK_LUA = `if redis.call("get", KEYS[1]) == ARGV[1] then return redis.call("del", KEYS[1]) else return 0 end`;

// INCR + arm the expiry on first use in one atomic call, so a counter can never outlive its window.
const INCR_LUA = `local c = redis.call("incr", KEYS[1]) if c == 1 then redis.call("expire", KEYS[1], ARGV[1]) end return {c, redis.call("ttl", KEYS[1])}`;

/** Adapts a node-redis client to {@link KeyValueStore}. */
export function redisKeyValueStore(client: RedisClient): KeyValueStore {
  return {
    get: (key) => client.get(key),
    set: async (key, value, ttlSeconds) => {
      await client.set(key, value, { EX: ttlSeconds });
    },
    getDel: (key) => client.getDel(key),
    del: async (key) => {
      await client.del(key);
    },
    expire: async (key, ttlSeconds) => {
      await client.expire(key, ttlSeconds);
    },
    setNx: async (key, value, ttlSeconds) =>
      (await client.set(key, value, { NX: true, EX: ttlSeconds })) === "OK",
    incr: async (key, ttlSeconds) => {
      const [count, ttl] = (await client.eval(INCR_LUA, {
        keys: [key],
        arguments: [String(ttlSeconds)],
      })) as [number, number];
      return { count: Number(count), ttl: Math.max(1, Number(ttl)) };
    },
    delIfEquals: async (key, value) =>
      Number(await client.eval(RELEASE_LOCK_LUA, { keys: [key], arguments: [value] })) === 1,
  };
}
