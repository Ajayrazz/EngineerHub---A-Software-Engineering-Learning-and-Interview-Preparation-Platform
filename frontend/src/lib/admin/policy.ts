/**
 * UI hints only. The API is the authority: every call below is re-checked there, and a hidden or
 * disabled control here never replaces a server-side check.
 */
export const PRIVILEGED_ROLES = ["SUPER_ADMIN", "ADMIN"] as const;

/** Who gets to see the admin area at all (others get a plain 404, so its existence is not advertised). */
export function canEnterAdmin(global: readonly string[], scoped: readonly string[] = []): boolean {
  return (
    global.includes("user.read") ||
    global.includes("audit.read") ||
    global.includes("course.edit") ||
    scoped.includes("course.edit")
  );
}

/** Does the caller hold `permission` globally (or, for course.edit only, scoped to some course)? */
export function holds(
  global: readonly string[],
  scoped: readonly string[],
  permission: string,
): boolean {
  return (
    global.includes(permission) || (permission === "course.edit" && scoped.includes(permission))
  );
}

/** May the actor toggle this staff role? Administrator roles need the super-admin permission. */
export function canEditRole(global: readonly string[], role: string): boolean {
  return (PRIVILEGED_ROLES as readonly string[]).includes(role)
    ? global.includes("role.grant.all")
    : global.includes("role.grant.standard");
}

/** Builds a query string, dropping empty values. */
export function query(params: Record<string, string | number | undefined | null>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && String(v).trim() !== "") q.set(k, String(v));
  }
  const s = q.toString();
  return s ? `?${s}` : "";
}

/** A user id from the URL must look like a UUID before it is placed into an API path. */
export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

/** Clamps a page number coming from the URL. */
export function pageParam(value: string | string[] | undefined): number {
  const n = Number(Array.isArray(value) ? value[0] : value);
  return Number.isInteger(n) && n >= 0 && n < 100_000 ? n : 0;
}
