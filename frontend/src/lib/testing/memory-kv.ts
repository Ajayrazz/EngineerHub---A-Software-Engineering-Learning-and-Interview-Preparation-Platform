import type { KeyValueStore } from "../session/store";

/** In-memory KeyValueStore with a controllable clock, for unit tests. */
export class MemoryKv implements KeyValueStore {
  readonly data = new Map<string, { value: string; expiresAt: number }>();
  constructor(public nowSeconds: () => number = () => Math.floor(Date.now() / 1000)) {}

  private live(key: string) {
    const e = this.data.get(key);
    if (!e) return undefined;
    if (e.expiresAt <= this.nowSeconds()) {
      this.data.delete(key);
      return undefined;
    }
    return e;
  }
  async get(key: string) {
    return this.live(key)?.value ?? null;
  }
  async set(key: string, value: string, ttl: number) {
    this.data.set(key, { value, expiresAt: this.nowSeconds() + ttl });
  }
  async getDel(key: string) {
    const v = this.live(key)?.value ?? null;
    this.data.delete(key);
    return v;
  }
  async del(key: string) {
    this.data.delete(key);
  }
  async expire(key: string, ttl: number) {
    const e = this.live(key);
    if (e) e.expiresAt = this.nowSeconds() + ttl;
  }
  async setNx(key: string, value: string, ttl: number) {
    if (this.live(key)) return false;
    this.data.set(key, { value, expiresAt: this.nowSeconds() + ttl });
    return true;
  }
  async delIfEquals(key: string, value: string) {
    if (this.live(key)?.value === value) {
      this.data.delete(key);
      return true;
    }
    return false;
  }
  ttl(key: string) {
    const e = this.live(key);
    return e ? e.expiresAt - this.nowSeconds() : null;
  }
}
