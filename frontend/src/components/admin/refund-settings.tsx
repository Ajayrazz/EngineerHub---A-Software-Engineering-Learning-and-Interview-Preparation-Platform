"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { formatInr, toMinor } from "@/lib/billing/format";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type Settings = components["schemas"]["RefundSettingsView"];

/**
 * The owner's refund policy and rules. Nothing is pre-filled: the policy text, request window and approval
 * threshold are the owner's decision (Pending Decision P-6) and stay empty until written here.
 */
export function RefundSettings({ csrfToken, settings }: { csrfToken: string; settings: Settings }) {
  const router = useRouter();
  const [policy, setPolicy] = useState(settings.policyText ?? "");
  const [windowDays, setWindowDays] = useState(settings.requestWindowDays?.toString() ?? "");
  const [revoke, setRevoke] = useState(settings.revokeAccessOnRefund ?? true);
  const [threshold, setThreshold] = useState(
    settings.approvalThresholdMinor ? String(settings.approvalThresholdMinor / 100) : "",
  );
  const [dups, setDups] = useState(settings.autoDetectDuplicates ?? true);
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const days = windowDays.trim() === "" ? null : Number(windowDays);
  const minor = threshold.trim() === "" ? null : toMinor(threshold);
  const invalid =
    (days !== null && (!Number.isInteger(days) || days < 1 || days > 3660)) ||
    (threshold.trim() !== "" && (minor === null || minor < 100));

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, "admin/billing/refund-settings", "PUT", {
      policyText: policy,
      requestWindowDays: days,
      revokeAccessOnRefund: revoke,
      approvalThresholdMinor: minor,
      autoDetectDuplicates: dups,
    });
    setBusy(false);
    if (!r.ok) return setFlash({ kind: "error", text: r.message });
    setFlash({ kind: "ok", text: "Refund settings saved." });
    router.refresh();
  }

  return (
    <form
      onSubmit={save}
      className="flex flex-col gap-3 rounded border p-4"
      aria-label="Refund settings"
      data-testid="refund-settings"
    >
      <h2 className="text-lg font-medium">Refund policy and rules</h2>
      <p className="text-sm opacity-80">
        This is your policy, shown to learners as written. Nothing is pre-filled and nothing here is
        legal advice; have the final wording reviewed before going live.
      </p>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
      <label className="flex flex-col gap-1 text-sm">
        Policy text shown to learners (plain text)
        <textarea
          className={input}
          name="policy-text"
          rows={6}
          maxLength={10000}
          value={policy}
          onChange={(e) => setPolicy(e.target.value)}
        />
      </label>
      <div className="flex flex-wrap gap-4">
        <label className="flex flex-col gap-1 text-sm">
          Request window in days (empty = no limit)
          <input
            className={`${input} w-40`}
            name="window-days"
            inputMode="numeric"
            value={windowDays}
            onChange={(e) => setWindowDays(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Second approver from (rupees, empty = never)
          <input
            className={`${input} w-40`}
            name="threshold"
            inputMode="decimal"
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
          />
        </label>
      </div>
      {minor !== null && minor >= 100 && (
        <p className="text-sm opacity-80">
          Refunds of {formatInr(minor)} or more need a second admin, different from the reviewer.
        </p>
      )}
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={revoke} onChange={(e) => setRevoke(e.target.checked)} />
        End the access a payment bought when it is refunded in full
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={dups} onChange={(e) => setDups(e.target.checked)} />
        Flag payments that look like duplicates
      </label>
      {invalid && (
        <p role="alert" className="text-sm" data-testid="refund-settings-problem">
          The window must be 1 to 3660 days and the threshold at least 1 rupee (for example 500 or
          499.50).
        </p>
      )}
      <div>
        <button
          className={btn}
          disabled={busy || invalid}
          type="submit"
          data-testid="refund-settings-save"
        >
          Save refund settings
        </button>
      </div>
    </form>
  );
}
