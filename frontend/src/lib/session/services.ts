import "server-only";
import { getEnv } from "../env";
import { deriveKeys } from "./crypto";
import { createRedisClient, redisKeyValueStore, type RedisClient } from "./redis-kv";
import { SessionStore } from "./store";

const g = globalThis as unknown as { __ehRedis?: Promise<RedisClient>; __ehStore?: SessionStore };

/** One shared Redis connection per server process (survives dev hot-reloads). */
function getRedis(): Promise<RedisClient> {
  if (!g.__ehRedis) {
    const client = createRedisClient(getEnv().REDIS_URL);
    // Never log the URL (it contains the password); the error object does not include it.
    client.on("error", (err: Error) => {
      console.error(JSON.stringify({ level: "error", event: "redis_error", message: err.message }));
    });
    g.__ehRedis = client
      .connect()
      .then(() => client)
      .catch((err) => {
        g.__ehRedis = undefined; // allow a retry on the next request
        throw err;
      });
  }
  return g.__ehRedis;
}

export async function getSessionStore(): Promise<SessionStore> {
  if (!g.__ehStore) {
    const env = getEnv();
    g.__ehStore = new SessionStore(
      redisKeyValueStore(await getRedis()),
      deriveKeys(env.SESSION_SECRET),
      {
        idleTtlSeconds: env.SESSION_IDLE_TTL_SECONDS,
        absoluteTtlSeconds: env.SESSION_ABSOLUTE_TTL_SECONDS,
      },
    );
  }
  return g.__ehStore;
}
