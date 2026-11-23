import Link from "next/link";
import { AuditVerify } from "@/components/admin/audit-verify";
import { AdminShell, Notice, btn, input, when } from "@/components/admin/ui";
import { pageParam, query } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type PageOfEntries = components["schemas"]["PageOfEntries"];

const one = (v: string | string[] | undefined) => (typeof v === "string" ? v.slice(0, 100) : "");

export default async function AuditPage({ searchParams }: PageProps<"/admin/audit">) {
  const sp = await searchParams;
  const ctx = await adminContext("/admin/audit", "audit.read");
  if (!ctx.global.includes("audit.read")) {
    return (
      <AdminShell title="Audit log" active="audit" global={ctx.global}>
        <Notice kind="error">You do not have access to the audit log.</Notice>
      </AdminShell>
    );
  }
  const action = one(sp.action);
  const resourceId = one(sp.resourceId);
  const page = pageParam(sp.page);
  const result = await apiGet<PageOfEntries>(
    ctx,
    `admin/audit-logs${query({ action, resourceId, page, size: 25 })}`,
  );
  const pageLink = (p: number) => `/admin/audit${query({ action, resourceId, page: p })}`;

  return (
    <AdminShell title="Audit log" active="audit" global={ctx.global}>
      <form method="get" className="flex flex-wrap items-end gap-2" role="search">
        <label className="flex flex-col gap-1 text-sm">
          Action
          <input
            name="action"
            defaultValue={action}
            placeholder="e.g. user.status.changed"
            className={input}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Resource id
          <input name="resourceId" defaultValue={resourceId} className={input} />
        </label>
        <button className={btn} type="submit">
          Filter
        </button>
      </form>
      <AuditVerify />
      {!result.ok ? (
        <Notice kind="error">{result.detail}</Notice>
      ) : (
        <>
          <table className="w-full text-left text-sm" data-testid="audit-table">
            <thead>
              <tr className="border-b opacity-70">
                <th className="py-2">When</th>
                <th>Action</th>
                <th>Resource</th>
                <th>Actor</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              {(result.data.items ?? []).map((e) => (
                <tr key={e.id} className="border-b align-top" data-testid="audit-row">
                  <td className="py-2 whitespace-nowrap">{when(e.occurredAt)}</td>
                  <td>{e.action}</td>
                  <td className="break-all">
                    {e.resourceType}:{e.resourceId}
                  </td>
                  <td className="break-all">{e.actorSub}</td>
                  <td>{e.reason ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {(result.data.items ?? []).length === 0 && <p className="text-sm">No entries.</p>}
          <div className="flex gap-3 text-sm">
            {page > 0 && (
              <Link className="underline" href={pageLink(page - 1)}>
                Newer
              </Link>
            )}
            {result.data.hasNext && (
              <Link className="underline" href={pageLink(page + 1)}>
                Older
              </Link>
            )}
          </div>
        </>
      )}
    </AdminShell>
  );
}
