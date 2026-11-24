"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { adminActions } from "@/lib/billing/refund";
import { formatInr, toMinor } from "@/lib/billing/format";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

/** Review, approve (full or partial), reject and retry. The server decides what is allowed; this only hides the rest. */
export function RefundActions({
  csrfToken,
  id,
  status,
  refundableMinor,
}: {
  csrfToken: string;
  id: string;
  status: string;
  refundableMinor: number;
}) {
  const router = useRouter();
  const can = adminActions(status);
  const [note, setNote] = useState("");
  const [amount, setAmount] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  const minor = amount.trim() === "" ? null : toMinor(amount);
  const amountBad =
    amount.trim() !== "" && (minor === null || minor < 1 || minor > refundableMinor);
  const noteOk = note.trim().length >= 5;

  async function act(path: string, body: unknown, ok: string) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, `admin/billing/refunds/${id}/${path}`, "POST", body);
    setBusy(false);
    setFlash(r.ok ? { kind: "ok", text: ok } : { kind: "error", text: r.message });
    router.refresh();
  }

  if (!can.review && !can.approve && !can.reject && !can.retry) {
    return <p className="text-sm opacity-70">Nothing more can be done with this request.</p>;
  }
  return (
    <div className="flex flex-col gap-3" data-testid="refund-actions">
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}
      {can.review && (
        <div>
          <button
            className={btn}
            disabled={busy}
            onClick={() => void act("review", undefined, "Review started.")}
            data-testid="refund-review"
          >
            Start review
          </button>
        </div>
      )}
      {(can.approve || can.reject) && (
        <label className="flex flex-col gap-1 text-sm">
          Note (at least 5 characters; shown to the learner if rejected)
          <input
            className={input}
            name="decision-note"
            value={note}
            maxLength={1000}
            onChange={(e) => setNote(e.target.value)}
          />
        </label>
      )}
      {can.approve && (
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Amount in rupees (empty = everything still refundable: {formatInr(refundableMinor)})
            <input
              className={`${input} w-48`}
              name="refund-amount"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
          <button
            className={btn}
            disabled={busy || !noteOk || amountBad}
            onClick={() =>
              void act(
                "approve",
                { amountMinor: minor ?? undefined, note },
                "Approved; the refund was sent to the payment provider.",
              )
            }
            data-testid="refund-approve"
          >
            Approve and refund
          </button>
        </div>
      )}
      {amountBad && <p className="text-sm">Enter an amount up to {formatInr(refundableMinor)}.</p>}
      {can.reject && (
        <div>
          <button
            className={btn}
            disabled={busy || !noteOk}
            onClick={() => void act("reject", { note }, "Request rejected.")}
            data-testid="refund-reject"
          >
            Reject
          </button>
        </div>
      )}
      {can.retry && (
        <div className="flex flex-col gap-2">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              data-testid="refund-confirm-check"
            />
            I checked the payment provider: no refund exists there for this payment
          </label>
          <div>
            <button
              className={btn}
              disabled={busy || !confirmed}
              onClick={() => void act("retry", { confirmNotRefundedAtProvider: true }, "Retried.")}
              data-testid="refund-retry"
            >
              Retry the refund
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
