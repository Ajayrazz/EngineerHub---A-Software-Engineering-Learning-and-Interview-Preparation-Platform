import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Notice } from "@/components/admin/ui";
import { CancelSubscription } from "@/components/billing/cancel-subscription";
import { ReceiptLink } from "@/components/billing/receipt-link";
import { RequestRefund } from "@/components/billing/request-refund";
import { ReaderShell } from "@/components/reader/ui";
import {
  accessHeadline,
  formatDate,
  formatInr,
  paymentStatusLabel,
  renewalReminder,
  subscriptionNote,
} from "@/lib/billing/format";
import { reasonLabel, refundStatusLabel } from "@/lib/billing/refund";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Access = components["schemas"]["AccessView"];
type Summary = components["schemas"]["BillingSummary"];
type MyRefund = components["schemas"]["MyRefundView"];
type Policy = components["schemas"]["RefundPolicyView"];

export const metadata: Metadata = { title: "My access · EngineerHub" };

export default async function BillingPage() {
  const session = await readerSession();
  if (!session) redirect("/auth/login?returnTo=/billing");
  const [summary, access, refunds, policy] = await Promise.all([
    readerGet<Summary>("me/billing", session.accessToken),
    readerGet<Access>("me/access", session.accessToken),
    readerGet<MyRefund[]>("me/refund-requests", session.accessToken),
    readerGet<Policy>("billing/refund-policy"),
  ]);
  const reminder = summary.ok ? renewalReminder(summary.data) : null;
  return (
    <ReaderShell username={session.username}>
      <h1 className="text-2xl font-semibold">My access</h1>
      {!summary.ok || !access.ok ? (
        <Notice kind="error">Your access could not be loaded. Please try again shortly.</Notice>
      ) : (
        <>
          <p className="text-lg" data-testid="access-state">
            {accessHeadline(summary.data)}
          </p>
          {reminder && (
            <div
              role="status"
              className="rounded border border-amber-500/60 px-3 py-2 text-sm"
              data-testid="renewal-reminder"
            >
              <p>{reminder}</p>
              <p className="mt-1">
                <Link className="underline" href="/pricing" data-testid="renew-link">
                  Renew
                </Link>
              </p>
            </div>
          )}
          {!summary.data.premium && !reminder && (
            <p className="text-sm">
              <Link className="underline" href="/pricing">
                See pricing
              </Link>
            </p>
          )}
          {summary.data.subscription && (
            <section
              aria-labelledby="subscription-h"
              className="space-y-2"
              data-testid="subscription"
            >
              <h2 id="subscription-h" className="text-lg font-medium">
                Auto-renewing subscription
              </h2>
              <p className="text-sm" data-testid="subscription-state">
                {summary.data.subscription.planName}:{" "}
                {subscriptionNote(summary.data.subscription, summary.data.validThrough)}
              </p>
              {(summary.data.subscription.status === "active" ||
                summary.data.subscription.status === "past_due") &&
                summary.data.subscription.id && (
                  <CancelSubscription
                    csrfToken={session.csrfToken}
                    subscriptionId={summary.data.subscription.id}
                    accessUntil={formatDate(summary.data.validThrough)}
                  />
                )}
            </section>
          )}
          <section aria-labelledby="payments-h" className="space-y-2">
            <h2 id="payments-h" className="text-lg font-medium">
              Payments
            </h2>
            {(summary.data.payments ?? []).length === 0 ? (
              <p className="text-sm opacity-70" data-testid="no-payments">
                No payments yet.
              </p>
            ) : (
              <table className="w-full text-left text-sm" data-testid="payments">
                <thead>
                  <tr className="border-b opacity-70">
                    <th className="py-2">Date</th>
                    <th>Plan</th>
                    <th>Amount (incl. taxes)</th>
                    <th>Status</th>
                    <th>Receipt</th>
                    <th>Refund</th>
                  </tr>
                </thead>
                <tbody>
                  {(summary.data.payments ?? []).map((p) => (
                    <tr key={p.id} className="border-b" data-testid="payment">
                      <td className="py-2">{formatDate(p.capturedAt)}</td>
                      <td>{p.planName}</td>
                      <td>{formatInr(p.amountMinor ?? 0)}</td>
                      <td>{paymentStatusLabel(p.status)}</td>
                      <td>
                        {p.id && (p.documentNumber || summary.data.documentsAvailable) ? (
                          <ReceiptLink
                            apiPath={`me/payments/${encodeURIComponent(p.id)}/document`}
                            label={p.documentNumber ?? "Get receipt"}
                          />
                        ) : (
                          <span className="opacity-70">Not available yet</span>
                        )}
                      </td>
                      <td>
                        {p.id && p.refundRequestable ? (
                          <RequestRefund csrfToken={session.csrfToken} paymentId={p.id} />
                        ) : p.refundRequestStatus ? (
                          <span data-testid="payment-refund-status">
                            {refundStatusLabel(p.refundRequestStatus)}
                          </span>
                        ) : (
                          <span className="opacity-70">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
          <section aria-labelledby="refunds-h" className="space-y-2" data-testid="refunds">
            <h2 id="refunds-h" className="text-lg font-medium">
              Refunds
            </h2>
            {policy.ok && policy.data.policyText ? (
              <div className="rounded border p-3 text-sm" data-testid="refund-policy">
                <p className="whitespace-pre-wrap">{policy.data.policyText}</p>
                {policy.data.requestWindowDays && (
                  <p className="mt-2 opacity-70">
                    Requests are accepted for {policy.data.requestWindowDays} days after payment.
                  </p>
                )}
              </div>
            ) : (
              <p className="text-sm opacity-70" data-testid="no-refund-policy">
                No refund policy has been published yet. You can still send a request from the
                payments table above; a person reviews every request.
              </p>
            )}
            {refunds.ok && refunds.data.length > 0 && (
              <ul className="flex flex-col gap-2 text-sm" data-testid="refund-list">
                {refunds.data.map((r) => (
                  <li key={r.id} className="rounded border p-3" data-testid="refund-request">
                    <div>
                      <strong>{refundStatusLabel(r.status)}</strong>: {r.planName},{" "}
                      {formatInr(r.paymentAmountMinor ?? 0)} ({reasonLabel(r.reason)})
                    </div>
                    {r.refundedMinor ? <div>Refunded: {formatInr(r.refundedMinor)}</div> : null}
                    {r.decisionNote && <div>Note from us: {r.decisionNote}</div>}
                    {r.refundId && r.creditNoteNumber && (
                      <div>
                        Credit note:{" "}
                        <ReceiptLink
                          apiPath={`me/refunds/${encodeURIComponent(r.refundId)}/credit-note`}
                          label={r.creditNoteNumber}
                        />
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
          {(access.data.periods ?? []).length > 0 && (
            <section aria-labelledby="periods-h" className="space-y-2">
              <h2 id="periods-h" className="text-lg font-medium">
                Access periods
              </h2>
              <table className="w-full text-left text-sm" data-testid="periods">
                <thead>
                  <tr className="border-b opacity-70">
                    <th className="py-2">Plan</th>
                    <th>From</th>
                    <th>Until</th>
                    <th>Source</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {(access.data.periods ?? []).map((p) => (
                    <tr key={p.id} className="border-b" data-testid="period">
                      <td className="py-2">{p.planName}</td>
                      <td>{formatDate(p.startsAt)}</td>
                      <td>{formatDate(p.endsAt)}</td>
                      <td>
                        {p.sourceType === "admin_grant"
                          ? "granted by EngineerHub"
                          : p.sourceType === "payment"
                            ? "purchase"
                            : p.sourceType}
                      </td>
                      <td>{p.revoked ? "revoked" : "active or expired"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}
    </ReaderShell>
  );
}
