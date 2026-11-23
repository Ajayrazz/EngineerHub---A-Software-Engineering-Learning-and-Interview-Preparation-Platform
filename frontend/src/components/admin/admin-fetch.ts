"use client";

export type ActionResult = { ok: true } | { ok: false; message: string };

/**
 * Mutations go through the BFF proxy: the browser holds no token, only the session cookie plus the
 * per-session CSRF token that the server rendered into the page.
 */
export async function adminFetch(
  csrfToken: string,
  path: string,
  method: "PUT" | "POST" | "DELETE" | "GET",
  body?: unknown,
): Promise<ActionResult & { data?: unknown }> {
  try {
    const response = await fetch(`/api/proxy/${path}`, {
      method,
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        ...(body === undefined ? {} : { "content-type": "application/json" }),
        ...(method === "GET" ? {} : { "x-csrf-token": csrfToken }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.ok) {
      const text = await response.text();
      return { ok: true, data: text ? JSON.parse(text) : undefined };
    }
    let message = `Request failed (HTTP ${response.status}).`;
    try {
      const problem = (await response.json()) as { detail?: string };
      if (typeof problem.detail === "string") message = problem.detail;
    } catch {
      // generic message
    }
    return { ok: false, message };
  } catch {
    return { ok: false, message: "Network error. Please retry." };
  }
}
