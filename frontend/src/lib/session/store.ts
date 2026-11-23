import {
  decrypt,
  encrypt,
  isValidSessionId,
  keyFor,
  newSessionId,
  type SessionKeys,
} from "./crypto";
import type { LoginTransaction, Session } from "./types";

/** The few Redis operations we need, so the logic is testable without a Redis server. */
export interface KeyValueStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string, ttlSeconds: number): Promise<void>;
  /** Atomic read-and-delete (single use values). */
  getDel(key: string): Promise<string | null>;
  del(key: string): Promise<void>;
  expire(key: string, ttlSeconds: number): Promise<void>;
  /** SET NX EX: true if this call created the key. */
  setNx(key: string, value: string, ttlSeconds: number): Promise<boolean>;
  /** Atomically increment a counter; the TTL is armed when it is created. Returns the new count and the seconds left. */
  incr(key: string, ttlSeconds: number): Promise<{ count: number; ttl: number }>;
  /** Delete only if the value still equals `value` (safe lock release). */
  delIfEquals(key: string, value: string): Promise<boolean>;
}

export type SessionStoreOptions = {
  idleTtlSeconds: number;
  absoluteTtlSeconds: number;
  now?: () => number; // epoch seconds
};

const SESSION_NS = "session";
const LOGIN_NS = "login";
const LOCK_NS = "lock";
const LOGIN_TTL_SECONDS = 300;

export class SessionStore {
  private readonly now: () => number;

  constructor(
    private readonly kv: KeyValueStore,
    private readonly keys: SessionKeys,
    private readonly options: SessionStoreOptions,
  ) {
    this.now = options.now ?? (() => Math.floor(Date.now() / 1000));
  }

  private sessionKey(sid: string) {
    return keyFor(this.keys.macKey, SESSION_NS, sid);
  }

  /** Creates a session under a brand-new random id (never reuses a client-supplied id: no fixation). */
  async create(session: Session): Promise<string> {
    const sid = newSessionId();
    await this.write(sid, session, this.options.idleTtlSeconds);
    return sid;
  }

  /**
   * Loads a session, enforcing the absolute lifetime, and slides the idle timeout.
   * Anything malformed, tampered with, expired or missing yields `null`.
   */
  async load(sid: string | undefined): Promise<Session | null> {
    if (!isValidSessionId(sid)) return null;
    const key = this.sessionKey(sid);
    const raw = await this.kv.get(key);
    if (!raw) return null;
    let session: Session;
    try {
      session = JSON.parse(decrypt(this.keys.encKey, raw, key)) as Session;
    } catch {
      await this.kv.del(key); // corrupt/tampered entry: drop it
      return null;
    }
    const remainingAbsolute = session.createdAt + this.options.absoluteTtlSeconds - this.now();
    if (remainingAbsolute <= 0) {
      await this.kv.del(key);
      return null;
    }
    await this.kv.expire(key, Math.min(this.options.idleTtlSeconds, remainingAbsolute));
    return session;
  }

  /** Replaces a session's contents (e.g. after a token refresh), keeping its id and absolute deadline. */
  async save(sid: string, session: Session): Promise<void> {
    const remainingAbsolute = session.createdAt + this.options.absoluteTtlSeconds - this.now();
    if (remainingAbsolute <= 0) {
      await this.destroy(sid);
      return;
    }
    await this.write(sid, session, Math.min(this.options.idleTtlSeconds, remainingAbsolute));
  }

  async destroy(sid: string | undefined): Promise<void> {
    if (!isValidSessionId(sid)) return;
    await this.kv.del(this.sessionKey(sid));
  }

  // ---- login transactions (state -> PKCE verifier, nonce, returnTo) ----

  async putLoginTransaction(state: string, txn: LoginTransaction): Promise<void> {
    const key = keyFor(this.keys.macKey, LOGIN_NS, state);
    await this.kv.set(key, encrypt(this.keys.encKey, JSON.stringify(txn), key), LOGIN_TTL_SECONDS);
  }

  /** Single use: a second call with the same state returns null (replay protection). */
  async takeLoginTransaction(state: string): Promise<LoginTransaction | null> {
    if (!/^[A-Za-z0-9_-]{20,128}$/.test(state)) return null;
    const key = keyFor(this.keys.macKey, LOGIN_NS, state);
    const raw = await this.kv.getDel(key);
    if (!raw) return null;
    try {
      return JSON.parse(decrypt(this.keys.encKey, raw, key)) as LoginTransaction;
    } catch {
      return null;
    }
  }

  // ---- per-session refresh lock ----

  /** Returns true if the caller now owns the refresh lock for this session. */
  acquireRefreshLock(sid: string, owner: string, ttlSeconds = 10): Promise<boolean> {
    return this.kv.setNx(keyFor(this.keys.macKey, LOCK_NS, sid), owner, ttlSeconds);
  }

  releaseRefreshLock(sid: string, owner: string): Promise<boolean> {
    return this.kv.delIfEquals(keyFor(this.keys.macKey, LOCK_NS, sid), owner);
  }

  private async write(sid: string, session: Session, ttlSeconds: number) {
    const key = this.sessionKey(sid);
    await this.kv.set(key, encrypt(this.keys.encKey, JSON.stringify(session), key), ttlSeconds);
  }
}
