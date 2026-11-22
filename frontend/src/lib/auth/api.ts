import "server-only";
import { getEnv } from "../env";

/** `<API_BASE_URL>/api/v1/<path>` — `path` must already be validated (see toApiPath) or be a constant. */
export function apiUrl(path: string, search = ""): string {
  return `${getEnv().API_BASE_URL.replace(/\/$/, "")}/api/v1/${path}${search}`;
}

/** Server-side call to the Spring API with the user's access token. */
export async function callApi(
  accessToken: string,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${accessToken}`);
  headers.set("Accept", "application/json");
  return fetch(apiUrl(path), {
    ...init,
    headers,
    redirect: "manual",
    cache: "no-store",
    signal: init.signal ?? AbortSignal.timeout(10_000),
  });
}
