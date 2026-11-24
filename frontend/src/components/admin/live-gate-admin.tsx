"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { components } from "@/lib/api/schema";
import { remainingItems } from "@/lib/billing/reconciliation";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type Gate = components["schemas"]["LiveGateView"];

/**
 * The Live Readiness Gate. Each tick is the owner's attestation (the system cannot verify a legal entity or a KYC
 * approval); three items also have a check the system makes. Only a super administrator can change anything. Opening
 * the gate does NOT enable live payments: this build refuses live mode whatever is recorded here.
 */
export function LiveGateAdmin({
  csrfToken,
  gate,
  canChange,
}: {
  csrfToken: string;
  gate: Gate;
  canChange: boolean;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>(
    Object.fromEntries((gate.items ?? []).map((i) => [i.code ?? "", i.note ?? ""])),
  );

  async function call(path: string, method: "PUT" | "POST", body: unknown, ok: string) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, method, body);
    setBusy(false);
    setFlash(r.ok ? { kind: "ok", text: ok } : { kind: "error", text: r.message });
    router.refresh();
  }

  const items = gate.items ?? [];
  const left = remainingItems(items);
  return (
    <div className="flex flex-col gap-4" data-testid="live-gate">
      <p className="rounded border p-3 text-sm" data-testid="gate-enforcement">
        {gate.enforcement}
      </p>
      <p className="text-lg" data-testid="gate-state">
        {gate.liveReady
          ? "Gate open: every item is attested."
          : `Gate closed: ${left.length} of ${items.length} items still open.`}
      </p>
      {!canChange && (
        <p className="text-sm opacity-80" data-testid="gate-readonly">
          Only a super administrator can change the gate. You can see where it stands.
        </p>
      )}
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
      <ul className="flex flex-col gap-2">
        {items.map((i) => (
          <li
            key={i.code}
            className="flex flex-col gap-1 rounded border p-3 text-sm"
            data-testid="gate-item"
          >
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={!!i.done}
                disabled={!canChange || busy}
                aria-label={i.label}
                onChange={(e) =>
                  void call(
                    `admin/billing/live-gate/items/${i.code}`,
                    "PUT",
                    { done: e.target.checked, note: notes[i.code ?? ""] },
                    e.target.checked ? "Attested." : "Un-ticked; the gate is closed.",
                  )
                }
              />
              <span>{i.label}</span>
            </label>
            {i.check && (
              <span className="opacity-80" data-testid="gate-check">
                {i.check.ok ? "Check passes" : "Check fails"}: {i.check.message}
              </span>
            )}
            {canChange && (
              <input
                className={`${input} w-96`}
                placeholder="Note (who confirmed, where it is recorded)"
                aria-label={`Note for ${i.label}`}
                value={notes[i.code ?? ""] ?? ""}
                maxLength={500}
                onChange={(e) => setNotes({ ...notes, [i.code ?? ""]: e.target.value })}
              />
            )}
            {!canChange && i.note && <span className="opacity-80">Note: {i.note}</span>}
          </li>
        ))}
      </ul>
      {canChange && (
        <div className="flex gap-2">
          <button
            className={btn}
            disabled={busy || gate.liveReady || left.length > 0}
            onClick={() =>
              void call(
                "admin/billing/live-gate/ready",
                "POST",
                { ready: true },
                "The gate is open.",
              )
            }
            data-testid="gate-open"
          >
            Open the gate
          </button>
          <button
            className={btn}
            disabled={busy || !gate.liveReady}
            onClick={() =>
              void call(
                "admin/billing/live-gate/ready",
                "POST",
                { ready: false },
                "The gate is closed.",
              )
            }
            data-testid="gate-close"
          >
            Close the gate
          </button>
        </div>
      )}
    </div>
  );
}
