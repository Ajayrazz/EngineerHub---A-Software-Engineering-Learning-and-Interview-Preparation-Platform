"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { components } from "@/lib/api/schema";
import { formatInr } from "@/lib/billing/format";
import { kindLabel, needsAttention, runStatusLabel } from "@/lib/billing/reconciliation";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input, when } from "./ui";

type Overview = components["schemas"]["ReconciliationOverview"];

/**
 * Runs and open findings. A run never changes money records: the two fixes (record a lost payment, finish a refund
 * the provider already processed) are applied only by a click here, and everything else is acknowledged with a note.
 */
export function ReconciliationAdmin({
  csrfToken,
  overview,
}: {
  csrfToken: string;
  overview: Overview;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});

  async function act(path: string, body: unknown, ok: string) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, "POST", body);
    setBusy(false);
    setFlash(r.ok ? { kind: "ok", text: ok } : { kind: "error", text: r.message });
    router.refresh();
  }

  const runs = overview.runs ?? [];
  const findings = overview.openFindings ?? [];
  return (
    <div className="flex flex-col gap-6">
      <p className="rounded border p-3 text-sm opacity-80">
        Compares what the payment provider lists for the last 48 hours with what we recorded, every
        night and whenever you press the button. It reports differences; it never changes a payment,
        a refund or anyone&apos;s access by itself. It also expires purchases nobody completed.
      </p>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
      <div>
        <button
          className={btn}
          disabled={busy}
          onClick={() =>
            void act("admin/billing/reconciliation/run", undefined, "The run finished.")
          }
          data-testid="reconcile-run"
        >
          Run now
        </button>
      </div>

      <section aria-label="Open findings" className="flex flex-col gap-2" data-testid="findings">
        <h2 className="text-lg font-medium">Open findings</h2>
        {findings.length === 0 ? (
          <p className="text-sm opacity-70" data-testid="no-findings">
            Nothing to look at.
          </p>
        ) : (
          findings.map((f) => (
            <div
              key={f.id}
              className="flex flex-col gap-2 rounded border p-3 text-sm"
              data-testid="finding"
            >
              <div>
                <strong>{kindLabel(f.kind)}</strong>: {f.providerRef}
                {f.amountMinor && f.currency === "INR" ? ` (${formatInr(f.amountMinor)})` : ""}
              </div>
              <div className="opacity-80">{f.detail}</div>
              <div className="flex flex-wrap items-end gap-2">
                {f.applicable && (
                  <button
                    className={btn}
                    disabled={busy}
                    onClick={() =>
                      void act(
                        `admin/billing/reconciliation/findings/${f.id}/apply`,
                        undefined,
                        "Applied.",
                      )
                    }
                    data-testid="finding-apply"
                  >
                    Apply the fix
                  </button>
                )}
                <input
                  className={`${input} w-72`}
                  placeholder="What you checked (5+ characters)"
                  value={notes[f.id ?? ""] ?? ""}
                  maxLength={500}
                  onChange={(e) => setNotes({ ...notes, [f.id ?? ""]: e.target.value })}
                  aria-label="Note for this finding"
                />
                <button
                  className={btn}
                  disabled={busy || (notes[f.id ?? ""] ?? "").trim().length < 5}
                  onClick={() =>
                    void act(
                      `admin/billing/reconciliation/findings/${f.id}/resolve`,
                      { note: notes[f.id ?? ""] },
                      "Acknowledged.",
                    )
                  }
                  data-testid="finding-resolve"
                >
                  Acknowledge
                </button>
              </div>
            </div>
          ))
        )}
      </section>

      <section aria-label="Recent runs" className="flex flex-col gap-2">
        <h2 className="text-lg font-medium">Recent runs</h2>
        {runs.length === 0 ? (
          <p className="text-sm opacity-70" data-testid="no-runs">
            No run yet.
          </p>
        ) : (
          <table className="w-full text-left text-sm" data-testid="runs">
            <thead>
              <tr className="border-b opacity-70">
                <th className="py-2">Started</th>
                <th>By</th>
                <th>Result</th>
                <th>Provider</th>
                <th>Ours</th>
                <th>New findings</th>
                <th>Expired</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((r) => (
                <tr key={r.id} className="border-b" data-testid="run">
                  <td className="py-2">{when(r.startedAt)}</td>
                  <td>{r.trigger}</td>
                  <td className={needsAttention(r.status) ? "font-medium" : ""}>
                    {runStatusLabel(r.status)}
                    {r.note ? <span className="block text-xs opacity-70">{r.note}</span> : null}
                  </td>
                  <td>{r.providerPayments}</td>
                  <td>{r.localPayments}</td>
                  <td>{r.findingsNew}</td>
                  <td>{r.intentsExpired}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
