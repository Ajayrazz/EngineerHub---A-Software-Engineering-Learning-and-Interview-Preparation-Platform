"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { btn } from "@/components/admin/ui";

/** Two-step "stop renewing": nothing is refunded and paid access is untouched, and the page says so before asking. */
export function CancelSubscription({
  csrfToken,
  subscriptionId,
  accessUntil,
}: {
  csrfToken: string;
  subscriptionId: string;
  accessUntil: string;
}) {
  const router = useRouter();
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    const r = await fetch(`/api/proxy/billing/subscriptions/${subscriptionId}/cancel`, {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "x-csrf-token": csrfToken },
    }).catch(() => null);
    setBusy(false);
    if (!r?.ok) {
      setError("Auto-renew could not be turned off right now. Nothing changed; please try again.");
      return;
    }
    setAsking(false);
    router.refresh();
  }

  if (!asking) {
    return (
      <button className={btn} onClick={() => setAsking(true)} data-testid="cancel-start">
        Cancel auto-renew
      </button>
    );
  }
  return (
    <div className="flex flex-col gap-2" data-testid="cancel-confirm">
      <p className="text-sm">
        Stop renewing? Your access continues until {accessUntil}. The current period is not
        refunded.
      </p>
      <div className="flex gap-2">
        <button
          className={btn}
          disabled={busy}
          onClick={() => void confirm()}
          data-testid="cancel-yes"
        >
          Yes, stop renewing
        </button>
        <button className={btn} disabled={busy} onClick={() => setAsking(false)}>
          Keep it
        </button>
      </div>
      {error && (
        <p role="alert" className="text-sm" data-testid="cancel-error">
          {error}
        </p>
      )}
    </div>
  );
}
