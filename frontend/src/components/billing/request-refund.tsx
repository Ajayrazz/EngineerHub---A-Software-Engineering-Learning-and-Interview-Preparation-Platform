"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { btn, input } from "@/components/admin/ui";
import { REASONS } from "@/lib/billing/refund";

/** Asks for a refund of one payment. A request is always possible: a person decides, not this form. */
export function RequestRefund({ csrfToken, paymentId }: { csrfToken: string; paymentId: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState(REASONS[0].value);
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const r = await fetch("/api/proxy/billing/refund-requests", {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "content-type": "application/json", "x-csrf-token": csrfToken },
      body: JSON.stringify({ paymentId, reason, details }),
    }).catch(() => null);
    setBusy(false);
    if (!r?.ok) {
      const problem = (await r?.json().catch(() => ({}))) as { detail?: string } | undefined;
      setError(problem?.detail ?? "The request could not be sent. Please try again.");
      return;
    }
    setOpen(false);
    router.refresh();
  }

  if (!open) {
    return (
      <button className="underline" onClick={() => setOpen(true)} data-testid="refund-open">
        Request a refund
      </button>
    );
  }
  return (
    <form
      onSubmit={submit}
      className="flex flex-col gap-2"
      aria-label="Refund request"
      data-testid="refund-form"
    >
      <label className="flex flex-col gap-1 text-sm">
        Why?
        <select
          className={input}
          name="refund-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        >
          {REASONS.map((r) => (
            <option key={r.value} value={r.value}>
              {r.label}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Details (optional)
        <textarea
          className={input}
          name="refund-details"
          rows={3}
          maxLength={2000}
          value={details}
          onChange={(e) => setDetails(e.target.value)}
        />
      </label>
      <div className="flex gap-2">
        <button className={btn} disabled={busy} type="submit" data-testid="refund-send">
          Send request
        </button>
        <button className={btn} type="button" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
      {error && (
        <p role="alert" className="text-sm" data-testid="refund-error">
          {error}
        </p>
      )}
    </form>
  );
}
