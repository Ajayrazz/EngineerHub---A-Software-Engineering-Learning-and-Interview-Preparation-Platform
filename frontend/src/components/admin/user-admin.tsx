"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { canEditRole } from "@/lib/admin/policy";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input, when } from "./ui";

type GrantView = components["schemas"]["GrantView"];

type Props = {
  csrfToken: string;
  global: string[];
  userId: string;
  status: string;
  roles: string[] | null;
  rolesProblem: string | null;
  allRoles: string[];
  grants: GrantView[];
  grantsProblem: string | null;
};

type Flash = { kind: "ok" | "error"; text: string } | null;

function useAction(csrfToken: string) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<Flash>(null);
  async function run(
    path: string,
    method: "PUT" | "POST" | "DELETE",
    body: unknown,
    success: string,
  ) {
    setBusy(true);
    setFlash(null);
    const result = await adminFetch(csrfToken, path, method, body);
    setBusy(false);
    if (result.ok) {
      setFlash({ kind: "ok", text: success });
      router.refresh();
    } else {
      setFlash({ kind: "error", text: result.message });
    }
    return result.ok;
  }
  return { busy, flash, run };
}

export function UserAdmin(p: Props) {
  return (
    <div className="flex flex-col gap-8">
      {p.global.includes("user.suspend") && <StatusPanel {...p} />}
      <RolesPanel {...p} />
      <GrantsPanel {...p} />
    </div>
  );
}

function StatusPanel({ csrfToken, userId, status }: Props) {
  const { busy, flash, run } = useAction(csrfToken);
  const [reason, setReason] = useState("");
  const next = status === "suspended" ? "active" : "suspended";
  const changeable = status === "active" || status === "suspended";

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (
      await run(`admin/users/${userId}/status`, "PUT", { status: next, reason }, "Status updated.")
    ) {
      setReason("");
    }
  }
  return (
    <section aria-label="Account status" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Account status</h2>
      {!changeable ? (
        <p className="text-sm">This account is {status} and cannot be changed here.</p>
      ) : (
        <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1 text-sm">
            Reason (recorded in the audit log)
            <input
              className={`${input} w-80`}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              minLength={5}
              maxLength={500}
              required
              name="status-reason"
            />
          </label>
          <button className={btn} disabled={busy} type="submit" data-testid="status-submit">
            {next === "suspended" ? "Suspend account" : "Reactivate account"}
          </button>
        </form>
      )}
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
    </section>
  );
}

function RolesPanel({ csrfToken, global, userId, roles, rolesProblem, allRoles }: Props) {
  const { busy, flash, run } = useAction(csrfToken);
  const [selected, setSelected] = useState<string[]>(roles ?? []);
  const [reason, setReason] = useState("");
  const mayEditAny = global.includes("role.grant.standard");

  if (roles === null) {
    return (
      <section aria-label="Staff roles" className="flex flex-col gap-3">
        <h2 className="text-lg font-medium">Staff roles</h2>
        <Notice kind="error">{rolesProblem ?? "Roles are unavailable."}</Notice>
      </section>
    );
  }
  const dirty = [...selected].sort().join() !== [...roles].sort().join();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (
      await run(
        `admin/users/${userId}/roles`,
        "PUT",
        { roles: selected, reason },
        "Roles updated. They apply to the user's next sign-in or token refresh (up to 5 minutes).",
      )
    ) {
      setReason("");
    }
  }
  return (
    <section aria-label="Staff roles" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Staff roles</h2>
      <form onSubmit={submit} className="flex flex-col gap-3">
        <fieldset className="flex flex-wrap gap-4" disabled={!mayEditAny || busy}>
          <legend className="sr-only">Roles</legend>
          {allRoles.map((role) => {
            const editable = canEditRole(global, role);
            return (
              <label key={role} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  name="role"
                  value={role}
                  checked={selected.includes(role)}
                  disabled={!editable}
                  onChange={(e) =>
                    setSelected((s) =>
                      e.target.checked ? [...s, role] : s.filter((r) => r !== role),
                    )
                  }
                />
                {role}
              </label>
            );
          })}
        </fieldset>
        {mayEditAny && (
          <div className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col gap-1 text-sm">
              Reason (recorded in the audit log)
              <input
                className={`${input} w-80`}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                minLength={5}
                maxLength={500}
                required
                name="roles-reason"
              />
            </label>
            <button
              className={btn}
              disabled={busy || !dirty}
              type="submit"
              data-testid="roles-submit"
            >
              Save roles
            </button>
          </div>
        )}
      </form>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
    </section>
  );
}

function GrantsPanel({ csrfToken, global, userId, grants, grantsProblem }: Props) {
  const { busy, flash, run } = useAction(csrfToken);
  const [permission, setPermission] = useState("course.edit");
  const [resourceType, setResourceType] = useState("course");
  const [resourceId, setResourceId] = useState("");
  const mayGrant = global.includes("role.grant.standard");

  async function add(e: FormEvent) {
    e.preventDefault();
    if (
      await run(
        `admin/users/${userId}/grants`,
        "POST",
        { permission, resourceType, resourceId },
        "Grant added.",
      )
    ) {
      setResourceId("");
    }
  }
  return (
    <section aria-label="Scoped grants" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Scoped grants</h2>
      {grantsProblem ? (
        <Notice kind="error">{grantsProblem}</Notice>
      ) : grants.length === 0 ? (
        <p className="text-sm">No scoped grants.</p>
      ) : (
        <table className="w-full text-left text-sm" data-testid="grants-table">
          <thead>
            <tr className="border-b opacity-70">
              <th className="py-2">Permission</th>
              <th>Resource</th>
              <th>Granted</th>
              <th>Expires</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {grants.map((g) => (
              <tr key={g.id} className="border-b" data-testid="grant-row">
                <td className="py-2">{g.permission}</td>
                <td>
                  {g.resourceType}:{g.resourceId}
                </td>
                <td>{when(g.grantedAt)}</td>
                <td>{when(g.expiresAt)}</td>
                <td>
                  {mayGrant && (
                    <button
                      className={btn}
                      disabled={busy}
                      onClick={() =>
                        run(
                          `admin/users/${userId}/grants/${g.id}`,
                          "DELETE",
                          undefined,
                          "Grant revoked.",
                        )
                      }
                      aria-label={`Revoke ${g.permission} on ${g.resourceType}:${g.resourceId}`}
                    >
                      Revoke
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {mayGrant && (
        <form onSubmit={add} className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1 text-sm">
            Permission
            <input
              className={input}
              value={permission}
              onChange={(e) => setPermission(e.target.value)}
              required
              name="grant-permission"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Resource type
            <input
              className={input}
              value={resourceType}
              onChange={(e) => setResourceType(e.target.value)}
              required
              name="grant-type"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Resource id
            <input
              className={input}
              value={resourceId}
              onChange={(e) => setResourceId(e.target.value)}
              required
              name="grant-resource"
            />
          </label>
          <button className={btn} disabled={busy} type="submit" data-testid="grant-submit">
            Add grant
          </button>
        </form>
      )}
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
    </section>
  );
}
