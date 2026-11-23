import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Notice } from "@/components/admin/ui";
import { CancelSubscription } from "@/components/billing/cancel-subscription";
import { ReceiptLink } from "@/components/billing/receipt-link";
import { ReaderShell } from "@/components/reader/ui";
import {
  accessHeadline,
  formatDate,
  formatInr,
  paymentStatusLabel,
  renewalReminder,
  subscriptionNote,
} from "@/lib/billing/format";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Access = components["schemas"]["AccessView"];
type Summary = components["schemas"]["BillingSummary"];

export const metadata: Metadata = { title: "My access · EngineerHub" };

export default async function BillingPage() {
  const session = await readerSession();
  if (!session) redirect("/auth/login?returnTo=/billing");
  const [summary, access] = await Promise.all([
    readerGet<Summary>("me/billing", session.accessToken),
    readerGet<Access>("me/access", session.accessToken),
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
                          <ReceiptLink paymentId={p.id} label={p.documentNumber ?? "Get receipt"} />
                        ) : (
                          <span className="opacity-70">Not available yet</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
