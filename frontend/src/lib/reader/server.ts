import "server-only";
import { currentSession, withFreshToken } from "@/lib/auth/current";
import { RefreshUnavailableError } from "@/lib/auth/refresh";
import { apiUrl } from "@/lib/auth/api";

export type ReaderSession = { accessToken: string; csrfToken: string; username: string } | null;

export type ReaderResult<T> =
  { ok: true; data: T } | { ok: false; status: number; code: string; detail: string };

/**
 * The reader is public: a signed-in visitor gets their token attached (premium access, progress), an
 * anonymous or session-less one still reads free content. An identity-provider outage degrades to
 * anonymous reading instead of failing the page.
 */
export async function readerSession(): Promise<ReaderSession> {
  const auth = await currentSession();
  if (!auth) return null;
  try {
    const fresh = await withFreshToken(auth);
    if (!fresh) return null;
    return {
      accessToken: fresh.session.accessToken,
      csrfToken: fresh.session.csrfToken,
      username: fresh.session.username,
    };
  } catch (error) {
    if (error instanceof RefreshUnavailableError) return null;
    throw error;
  }
}

/** GET against the API; `path` must be built from validated pieces (slugs/uuids) by the caller. */
export async function readerGet<T>(path: string, token?: string): Promise<ReaderResult<T>> {
  try {
    const headers = new Headers({ Accept: "application/json" });
    if (token) headers.set("Authorization", `Bearer ${token}`);
    const response = await fetch(apiUrl(path), {
      headers,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
    if (response.ok) return { ok: true, data: (await response.json()) as T };
    let code = "ERROR";
    let detail = `Request failed (HTTP ${response.status}).`;
    try {
      const body = (await response.json()) as { code?: string; detail?: string };
      if (typeof body.code === "string") code = body.code;
      if (typeof body.detail === "string") detail = body.detail;
    } catch {
      // keep generic values
    }
    return { ok: false, status: response.status, code, detail };
  } catch {
    return {
      ok: false,
      status: 0,
      code: "UNAVAILABLE",
      detail: "The service could not be reached.",
    };
  }
}

export const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
