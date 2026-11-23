import "server-only";
import { cache } from "react";
import { notFound, redirect } from "next/navigation";
import type { components } from "@/lib/api/schema";
import { callApi } from "@/lib/auth/api";
import { currentSession, withFreshToken } from "@/lib/auth/current";
import { RefreshUnavailableError } from "@/lib/auth/refresh";
import { canEnterAdmin, holds } from "./policy";

export type AdminContext = {
  accessToken: string;
  csrfToken: string;
  username: string;
  global: string[];
  /** Capabilities the caller holds on specific resources only (e.g. instructors). */
  scoped: string[];
  /** Scoped grants, e.g. the courses an instructor may edit. */
  grants: { permission: string; resourceType: string; resourceId: string }[];
};

export type ApiResult<T> = { ok: true; data: T } | { ok: false; status: number; detail: string };

/**
 * Gate for every admin page (layouts do not re-render on navigation, so each page calls this itself):
 * anonymous -> login; signed in without admin permissions -> 404 (the area is not advertised).
 */
export const adminContext = cache(
  async (returnTo: string, need?: string): Promise<AdminContext> => {
    const auth = await currentSession();
    if (!auth) redirect(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
    let fresh;
    try {
      fresh = await withFreshToken(auth);
    } catch (error) {
      if (error instanceof RefreshUnavailableError) {
        throw new Error("Sign-in services are temporarily unavailable. Please retry in a moment.");
      }
      throw error;
    }
    if (!fresh) redirect(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);

    const response = await callApi(fresh.session.accessToken, "me/permissions");
    if (response.status === 401) redirect(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
    const perms = response.ok
      ? ((await response.json()) as components["schemas"]["PermissionsResponse"])
      : null;
    const global = perms?.global ?? [];
    const scoped = perms?.scopedCapabilities ?? [];
    if (!canEnterAdmin(global, scoped)) notFound();
    if (need && !holds(global, scoped, need)) notFound(); // each page states the capability it needs
    return {
      accessToken: fresh.session.accessToken,
      csrfToken: fresh.session.csrfToken,
      username: fresh.session.username,
      global,
      scoped,
      grants: (perms?.grants ?? []).map((g) => ({
        permission: g.permission ?? "",
        resourceType: g.resourceType ?? "",
        resourceId: g.resourceId ?? "",
      })),
    };
  },
);

/** GET against the API as the signed-in staff member. Never throws on HTTP errors. */
export async function apiGet<T>(ctx: AdminContext, path: string): Promise<ApiResult<T>> {
  try {
    const response = await callApi(ctx.accessToken, path);
    if (response.ok) return { ok: true, data: (await response.json()) as T };
    let detail = `Request failed (HTTP ${response.status}).`;
    try {
      const body = (await response.json()) as { detail?: string };
      if (typeof body.detail === "string") detail = body.detail;
    } catch {
      // keep the generic message
    }
    return { ok: false, status: response.status, detail };
  } catch {
    return { ok: false, status: 0, detail: "The API could not be reached." };
  }
}
