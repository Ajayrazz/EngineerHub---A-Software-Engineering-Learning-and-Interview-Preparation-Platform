import { createRedisClient } from "../src/lib/session/redis-kv";

/**
 * Starts every E2E run from an empty BFF session store, so leftovers from earlier runs cannot affect
 * assertions. Destructive, therefore restricted to a local Redis and to the `bff:*` namespace.
 */
export default async function globalSetup() {
  const url = process.env.REDIS_URL;
  if (!url) throw new Error("REDIS_URL must be exported (use `make e2e`)");
  const host = new URL(url).hostname;
  if (!["localhost", "127.0.0.1", "::1", "[::1]"].includes(host)) {
    throw new Error(`Refusing to clear sessions on non-local Redis host "${host}"`);
  }
  const redis = createRedisClient(url);
  await redis.connect();
  try {
    const keys = await redis.keys("bff:*");
    if (keys.length > 0) await redis.del(keys);
  } finally {
    await redis.quit();
  }
}
