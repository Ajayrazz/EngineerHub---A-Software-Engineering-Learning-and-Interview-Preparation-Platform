import "server-only";
import { cache } from "react";
import type { components } from "@/lib/api/schema";
import { callApi } from "@/lib/auth/api";
import { currentSession, withFreshToken } from "@/lib/auth/current";
import { canEnterAdmin } from "./policy";

export type StaffInfo = {
  username: string;
  roles: string[];
  global: string[];
  scoped: string[];
};

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: "Super admin",
  ADMIN: "Administrator",
  CONTENT_MANAGER: "Content manager",
  PROBLEM_SETTER: "Problem setter",
  MODERATOR: "Moderator",
  INSTRUCTOR: "Instructor",
};

/** The most senior role, in plain words, for badges and greetings. */
export function roleLabel(roles: readonly string[]): string {
  for (const r of Object.keys(ROLE_LABELS)) if (roles.includes(r)) return ROLE_LABELS[r];
  return "Staff";
}

/**
 * Who is this, in staff terms? `null` for anonymous visitors and for learners. UI hints only: the API
 * re-checks every call, so this never grants anything. Cached per request.
 */
export const staffInfo = cache(async (): Promise<StaffInfo | null> => {
  const auth = await currentSession();
  if (!auth) return null;
  try {
    const fresh = await withFreshToken(auth);
    if (!fresh) return null;
    const token = fresh.session.accessToken;
    const [permsRes, whoRes] = await Promise.all([
      callApi(token, "me/permissions"),
      callApi(token, "system/whoami"),
    ]);
    if (!permsRes.ok) return null;
    const perms = (await permsRes.json()) as components["schemas"]["PermissionsResponse"];
    const global = perms.global ?? [];
    const scoped = perms.scopedCapabilities ?? [];
    if (!canEnterAdmin(global, scoped)) return null;
    const who = whoRes.ok ? ((await whoRes.json()) as components["schemas"]["WhoAmI"]) : null;
    return { username: fresh.session.username, roles: who?.roles ?? [], global, scoped };
  } catch {
    return null; // an outage must not break public pages; the admin pages report it themselves
  }
});
