import { notFound } from "next/navigation";
import { AccessPanel } from "@/components/admin/access-panel";
import { UserAdmin } from "@/components/admin/user-admin";
import { AdminShell, Notice, when } from "@/components/admin/ui";
import { isUuid } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type UserView = components["schemas"]["UserView"];
type RolesView = components["schemas"]["RolesView"];
type GrantView = components["schemas"]["GrantView"];
type RoleView = components["schemas"]["RoleView"];
type PlanView = components["schemas"]["PlanView"];
type AccessView = components["schemas"]["AccessView"];

export default async function UserPage({ params }: PageProps<"/admin/users/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const ctx = await adminContext(`/admin/users/${id}`, "user.read");

  const canBilling = ctx.global.includes("billing.manage");
  const [user, roles, grants, catalog, plans, access] = await Promise.all([
    apiGet<UserView>(ctx, `admin/users/${id}`),
    apiGet<RolesView>(ctx, `admin/users/${id}/roles`),
    apiGet<GrantView[]>(ctx, `admin/users/${id}/grants`),
    apiGet<RoleView[]>(ctx, "admin/roles"),
    canBilling ? apiGet<PlanView[]>(ctx, "admin/billing/plans") : Promise.resolve(null),
    canBilling
      ? apiGet<AccessView>(ctx, `admin/billing/users/${id}/access`)
      : Promise.resolve(null),
  ]);
  if (!user.ok) {
    if (user.status === 404) notFound();
    return (
      <AdminShell title="User" active="users" global={ctx.global}>
        <Notice kind="error">{user.detail}</Notice>
      </AdminShell>
    );
  }
  const u = user.data;
  return (
    <AdminShell title={u.username ?? "User"} active="users" global={ctx.global}>
      <dl className="grid grid-cols-[9rem_1fr] gap-y-2 text-sm" data-testid="user-details">
        <dt className="opacity-60">User id</dt>
        <dd>{u.id}</dd>
        <dt className="opacity-60">Email</dt>
        <dd>
          {u.email ?? "—"} {u.emailVerified ? "(verified)" : "(unverified)"}
        </dd>
        <dt className="opacity-60">Status</dt>
        <dd data-testid="user-status">{u.status}</dd>
        <dt className="opacity-60">Created</dt>
        <dd>{when(u.createdAt)}</dd>
        <dt className="opacity-60">Last login</dt>
        <dd>{when(u.lastLoginAt)}</dd>
      </dl>
      {plans?.ok && access?.ok && (
        <AccessPanel
          csrfToken={ctx.csrfToken}
          userId={id}
          plans={plans.data}
          access={access.data}
        />
      )}
      <UserAdmin
        csrfToken={ctx.csrfToken}
        global={ctx.global}
        userId={u.id ?? id}
        status={u.status ?? ""}
        roles={roles.ok ? (roles.data.roles ?? []) : null}
        rolesProblem={roles.ok ? null : roles.detail}
        allRoles={catalog.ok ? catalog.data.map((r) => r.role ?? "").filter(Boolean) : []}
        grants={grants.ok ? grants.data : []}
        grantsProblem={grants.ok ? null : grants.detail}
      />
    </AdminShell>
  );
}
