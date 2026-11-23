import Link from "next/link";
import { AdminShell, Notice, btn, input, when } from "@/components/admin/ui";
import { pageParam, query } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type UserPage = components["schemas"]["UserPage"];
const STATUSES = ["active", "suspended"];

export default async function UsersPage({ searchParams }: PageProps<"/admin/users">) {
  const sp = await searchParams;
  const ctx = await adminContext("/admin/users");
  const q = typeof sp.q === "string" ? sp.q.slice(0, 100) : "";
  const status = typeof sp.status === "string" && STATUSES.includes(sp.status) ? sp.status : "";
  const page = pageParam(sp.page);
  const result = await apiGet<UserPage>(ctx, `admin/users${query({ q, status, page, size: 20 })}`);

  const pageLink = (p: number) => `/admin/users${query({ q, status, page: p })}`;
  return (
    <AdminShell title="Users" active="users" global={ctx.global}>
      <form method="get" className="flex flex-wrap items-end gap-2" role="search">
        <label className="flex flex-col gap-1 text-sm">
          Search
          <input
            name="q"
            defaultValue={q}
            maxLength={100}
            placeholder="username or email"
            className={input}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Status
          <select name="status" defaultValue={status} className={input}>
            <option value="">any</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <button className={btn} type="submit">
          Search
        </button>
      </form>

      {!result.ok ? (
        <Notice kind="error">{result.detail}</Notice>
      ) : (
        <>
          <table className="w-full text-left text-sm" data-testid="users-table">
            <thead>
              <tr className="border-b opacity-70">
                <th className="py-2">Username</th>
                <th>Email</th>
                <th>Status</th>
                <th>Last login</th>
              </tr>
            </thead>
            <tbody>
              {(result.data.items ?? []).map((u) => (
                <tr key={u.id} className="border-b" data-testid="user-row">
                  <td className="py-2">
                    <Link className="underline" href={`/admin/users/${u.id}`}>
                      {u.username}
                    </Link>
                  </td>
                  <td>{u.email ?? "—"}</td>
                  <td>{u.status}</td>
                  <td>{when(u.lastLoginAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {(result.data.items ?? []).length === 0 && <p className="text-sm">No users found.</p>}
          <div className="flex items-center gap-3 text-sm">
            <span>
              {result.data.total ?? 0} user(s) · page {page + 1}
            </span>
            {page > 0 && (
              <Link className="underline" href={pageLink(page - 1)}>
                Previous
              </Link>
            )}
            {(page + 1) * 20 < (result.data.total ?? 0) && (
              <Link className="underline" href={pageLink(page + 1)}>
                Next
              </Link>
            )}
          </div>
        </>
      )}
    </AdminShell>
  );
}
