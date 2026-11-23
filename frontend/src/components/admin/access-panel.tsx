"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { formatDate } from "@/lib/billing/format";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type Plan = components["schemas"]["PlanView"];
type Access = components["schemas"]["AccessView"];

/** Complimentary access for one user (audited); the only source of access until payments exist. */
export function AccessPanel({
  csrfToken,
  userId,
  plans,
  access,
}: {
  csrfToken: string;
  userId: string;
  plans: Plan[];
  access: Access;
}) {
  const router = useRouter();
  const usable = plans.filter((p) => p.active && (p.entitlements ?? []).length > 0);
  const [planId, setPlanId] = useState(usable[0]?.id ?? "");
  const [days, setDays] = useState(30);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  async function call(path: string, body: unknown, ok: string) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, "POST", body);
    setBusy(false);
    if (!r.ok) return (setFlash({ kind: "error", text: r.message }), false);
    setFlash({ kind: "ok", text: ok });
    router.refresh();
    return true;
  }

  async function grant(e: FormEvent) {
    e.preventDefault();
    if (
      await call(
        `admin/billing/users/${userId}/access-grants`,
        { planId, days, reason },
        "Access granted.",
      )
    )
      setReason("");
  }

  return (
    <section aria-label="Premium access" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Premium access</h2>
      <p className="text-sm" data-testid="access-summary">
        {access.premium ? `Premium until ${formatDate(access.validThrough)}` : "No premium access"}
      </p>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
      <ul className="flex flex-col gap-1 text-sm" data-testid="access-periods">
        {(access.periods ?? []).map((p) => (
          <li key={p.id} className="flex flex-wrap items-center gap-2" data-testid="access-period">
            <span>
              {p.planName}: {formatDate(p.startsAt)} → {formatDate(p.endsAt)} ({p.sourceType})
              {p.revoked ? " · revoked" : ""}
            </span>
            {!p.revoked && (
              <button
                className={btn}
                disabled={busy}
                onClick={() => {
                  const why = window.prompt(
                    "Reason for revoking (recorded in the audit log, at least 5 characters)",
                  );
                  if (why && why.trim().length >= 5)
                    void call(
                      `admin/billing/access-periods/${p.id}/revoke`,
                      { reason: why.trim() },
                      "Access revoked.",
                    );
                }}
              >
                Revoke
              </button>
            )}
          </li>
        ))}
      </ul>
      {usable.length === 0 ? (
        <p className="text-sm opacity-70">
          Create an active plan that includes premium content under Billing first.
        </p>
      ) : (
        <form onSubmit={grant} className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1 text-sm">
            Plan
            <select
              className={input}
              value={planId}
              onChange={(e) => setPlanId(e.target.value)}
              aria-label="Plan"
            >
              {usable.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Days
            <input
              className={`${input} w-24`}
              type="number"
              min={1}
              max={3660}
              value={days}
              onChange={(e) => setDays(Number(e.target.value))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Reason (recorded in the audit log)
            <input
              className={`${input} w-72`}
              name="grant-reason"
              value={reason}
              minLength={5}
              maxLength={500}
              required
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <button className={btn} disabled={busy} type="submit" data-testid="grant-access">
            Grant access
          </button>
        </form>
      )}
    </section>
  );
}
