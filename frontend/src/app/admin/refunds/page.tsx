import Link from "next/link";
import { RefundSettings } from "@/components/admin/refund-settings";
import { AdminShell, Notice, btn, input, when } from "@/components/admin/ui";
import { query } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";
import { formatInr } from "@/lib/billing/format";
import { reasonLabel } from "@/lib/billing/refund";

type Row = components["schemas"]["AdminRefundRow"];
type Flag = components["schemas"]["RefundFlag"];
type Settings = components["schemas"]["RefundSettingsView"];

const STATUSES = ["requested", "under_review", "processing", "failed", "processed", "rejected"];

export default async function RefundsPage({ searchParams }: PageProps<"/admin/refunds">) {
  const sp = await searchParams;
  const ctx = await adminContext("/admin/refunds", "billing.manage");
  const status = typeof sp.status === "string" && STATUSES.includes(sp.status) ? sp.status : "";
  const [queue, flags, settings] = await Promise.all([
    apiGet<Row[]>(ctx, `admin/billing/refunds${query({ status })}`),
    apiGet<Flag[]>(ctx, "admin/billing/refund-flags"),
    apiGet<Settings>(ctx, "admin/billing/refund-settings"),
  ]);
  return (
    <AdminShell title="Refunds" active="refunds" global={ctx.global} scoped={ctx.scoped}>
      <form method="get" className="flex items-end gap-2" role="search">
        <label className="flex flex-col gap-1 text-sm">
          Status
          <select name="status" defaultValue={status} className={input}>
            <option value="">all</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.replace("_", " ")}
              </option>
            ))}
          </select>
        </label>
        <button className={btn} type="submit">
          Filter
        </button>
      </form>

      {!queue.ok ? (
        <Notice kind="error">{queue.detail}</Notice>
      ) : queue.data.length === 0 ? (
        <p className="text-sm" data-testid="no-refund-requests">
          No refund requests{status ? ` with status ${status.replace("_", " ")}` : ""}.
        </p>
      ) : (
        <table className="w-full text-left text-sm" data-testid="refund-queue">
          <thead>
            <tr className="border-b opacity-70">
              <th className="py-2">Requested</th>
              <th>User</th>
              <th>Plan</th>
              <th>Paid</th>
              <th>Refundable</th>
              <th>Reason</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {queue.data.map((r) => (
              <tr key={r.id} className="border-b" data-testid="refund-row">
                <td className="py-2">
                  <Link className="underline" href={`/admin/refunds/${r.id}`}>
                    {when(r.createdAt)}
                  </Link>
                </td>
                <td>{r.username}</td>
                <td>{r.planName}</td>
                <td>{formatInr(r.paymentAmountMinor ?? 0)}</td>
                <td>{formatInr(r.refundableMinor ?? 0)}</td>
                <td>{reasonLabel(r.reason)}</td>
                <td>{(r.status ?? "").replace("_", " ")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <section
        className="flex flex-col gap-2"
        aria-label="Flagged payments"
        data-testid="refund-flags"
      >
        <h2 className="text-lg font-medium">Flagged payments</h2>
        <p className="text-sm opacity-80">
          Suggestions only: nothing is refunded or activated automatically.
        </p>
        {!flags.ok ? (
          <Notice kind="error">{flags.detail}</Notice>
        ) : flags.data.length === 0 ? (
          <p className="text-sm opacity-70">Nothing is flagged.</p>
        ) : (
          <ul className="flex flex-col gap-2 text-sm">
            {flags.data.map((f) => (
              <li key={`${f.paymentId}-${f.kind}`} className="rounded border p-3">
                <strong>{(f.kind ?? "").replace("_", " ")}</strong>: {f.username}, {f.planName},{" "}
                {formatInr(f.amountMinor ?? 0)} on {when(f.capturedAt)}
                <div className="opacity-80">{f.suggestion}</div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {settings.ok ? (
        <RefundSettings csrfToken={ctx.csrfToken} settings={settings.data} />
      ) : (
        <Notice kind="error">{settings.detail}</Notice>
      )}
    </AdminShell>
  );
}
