import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Notice } from "@/components/admin/ui";
import { ReaderShell } from "@/components/reader/ui";
import { formatDate } from "@/lib/billing/format";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Access = components["schemas"]["AccessView"];

export const metadata: Metadata = { title: "My access · EngineerHub" };

export default async function BillingPage() {
  const session = await readerSession();
  if (!session) redirect("/auth/login?returnTo=/billing");
  const r = await readerGet<Access>("me/access", session.accessToken);
  return (
    <ReaderShell username={session.username}>
      <h1 className="text-2xl font-semibold">My access</h1>
      {!r.ok ? (
        <Notice kind="error">Your access could not be loaded. Please try again shortly.</Notice>
      ) : (
        <>
          <p className="text-lg" data-testid="access-state">
            {r.data.premium
              ? `Premium access until ${formatDate(r.data.validThrough)}`
              : "Free plan"}
          </p>
          {!r.data.premium && (
            <p className="text-sm">
              <Link className="underline" href="/pricing">
                See pricing
              </Link>
            </p>
          )}
          {(r.data.periods ?? []).length > 0 && (
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
                {(r.data.periods ?? []).map((p) => (
                  <tr key={p.id} className="border-b" data-testid="period">
                    <td className="py-2">{p.planName}</td>
                    <td>{formatDate(p.startsAt)}</td>
                    <td>{formatDate(p.endsAt)}</td>
                    <td>
                      {p.sourceType === "admin_grant" ? "granted by EngineerHub" : p.sourceType}
                    </td>
                    <td>{p.revoked ? "revoked" : "active or expired"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </ReaderShell>
  );
}
