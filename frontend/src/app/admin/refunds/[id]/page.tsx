import Link from "next/link";
import { notFound } from "next/navigation";
import { RefundActions } from "@/components/admin/refund-actions";
import { AdminShell, Notice, when } from "@/components/admin/ui";
import { isUuid } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";
import { formatInr } from "@/lib/billing/format";
import { reasonLabel } from "@/lib/billing/refund";

type Detail = components["schemas"]["AdminRefundDetail"];

export default async function RefundDetailPage({ params }: PageProps<"/admin/refunds/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const ctx = await adminContext(`/admin/refunds/${id}`, "billing.manage");
  const result = await apiGet<Detail>(ctx, `admin/billing/refunds/${id}`);
  return (
    <AdminShell title="Refund request" active="refunds" global={ctx.global} scoped={ctx.scoped}>
      <p className="text-sm">
        <Link className="underline" href="/admin/refunds">
          Back to the queue
        </Link>
      </p>
      {!result.ok ? (
        <Notice kind="error">{result.detail}</Notice>
      ) : (
        (() => {
          const d = result.data;
          const r = d.request;
          return (
            <div className="flex flex-col gap-4" data-testid="refund-detail">
              <dl className="grid grid-cols-[12rem_1fr] gap-y-1 text-sm">
                <dt className="opacity-70">User</dt>
                <dd>{r?.username}</dd>
                <dt className="opacity-70">Plan</dt>
                <dd>{r?.planName}</dd>
                <dt className="opacity-70">Paid</dt>
                <dd>
                  {formatInr(r?.paymentAmountMinor ?? 0)} on {when(d.paymentCapturedAt)} (
                  {d.paymentStatus?.replace("_", " ")})
                </dd>
                <dt className="opacity-70">Still refundable</dt>
                <dd data-testid="refundable">{formatInr(r?.refundableMinor ?? 0)}</dd>
                <dt className="opacity-70">Reason</dt>
                <dd>{reasonLabel(r?.reason)}</dd>
                <dt className="opacity-70">Details from the user</dt>
                <dd className="whitespace-pre-wrap">{d.details || "—"}</dd>
                <dt className="opacity-70">Status</dt>
                <dd data-testid="refund-status">
                  {(r?.status ?? "").replace("_", " ")}
                  {d.reviewStarted ? " (review started)" : ""}
                </dd>
                <dt className="opacity-70">Receipt or invoice issued</dt>
                <dd>
                  {d.hasDocument ? "yes" : "no (a credit note is only issued when there is one)"}
                </dd>
                {d.approvalThresholdMinor ? (
                  <>
                    <dt className="opacity-70">Second approver from</dt>
                    <dd>{formatInr(d.approvalThresholdMinor)}</dd>
                  </>
                ) : null}
                {d.decisionNote ? (
                  <>
                    <dt className="opacity-70">Decision note</dt>
                    <dd>{d.decisionNote}</dd>
                  </>
                ) : null}
              </dl>
              {(d.flags ?? []).map((f) => (
                <Notice key={f.kind} kind="error">
                  Flag: {f.suggestion}
                </Notice>
              ))}
              {(d.attempts ?? []).length > 0 && (
                <ul className="text-sm" data-testid="refund-attempts">
                  {(d.attempts ?? []).map((a) => (
                    <li key={a.id}>
                      Attempt: {formatInr(a.amountMinor ?? 0)}, {a.status}, {when(a.createdAt)}
                    </li>
                  ))}
                </ul>
              )}
              <RefundActions
                csrfToken={ctx.csrfToken}
                id={id}
                status={r?.status ?? ""}
                refundableMinor={r?.refundableMinor ?? 0}
              />
            </div>
          );
        })()
      )}
    </AdminShell>
  );
}
