"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { btn } from "@/components/admin/ui";
import {
  CHECKOUT_SCRIPT,
  isFinal,
  newIdempotencyKey,
  pollDelaysMs,
  razorpayOptions,
  statusMessage,
  type CheckoutView,
  type Stage,
} from "@/lib/billing/checkout";
import { formatInr } from "@/lib/billing/format";
import type { components } from "@/lib/api/schema";

type Quote = components["schemas"]["QuoteView"];
type Intent = components["schemas"]["IntentView"];

type RazorpayCtor = new (options: Record<string, unknown>) => {
  open(): void;
  on(event: string, cb: (response: unknown) => void): void;
};
declare global {
  interface Window {
    Razorpay?: RazorpayCtor;
  }
}

function loadCheckout(): Promise<RazorpayCtor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  return new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = CHECKOUT_SCRIPT;
    s.async = true;
    s.onload = () =>
      window.Razorpay ? resolve(window.Razorpay) : reject(new Error("checkout unavailable"));
    s.onerror = () => reject(new Error("checkout could not be loaded"));
    document.head.appendChild(s);
  });
}

/**
 * Buy flow for one price: show the server's final amount first, create the order, open Razorpay Checkout, tell the
 * server about the browser callback (a UX hint only) and then WAIT for the server to report the webhook-confirmed
 * status. Access is never assumed from anything the browser reports.
 */
export function BuyButton({
  csrfToken,
  planPriceId,
}: {
  csrfToken: string | null;
  planPriceId: string;
}) {
  const router = useRouter();
  const [stage, setStage] = useState<Stage>("idle");
  const [quote, setQuote] = useState<Quote | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const idempotencyKey = useRef(newIdempotencyKey());

  if (!csrfToken) {
    return (
      <a className="text-sm underline" href="/auth/login?returnTo=/pricing">
        Log in to buy
      </a>
    );
  }
  const token = csrfToken;

  async function showQuote() {
    setStage("quoting");
    setMessage(null);
    const r = await fetch(
      `/api/proxy/billing/quote?planPriceId=${encodeURIComponent(planPriceId)}`,
      { credentials: "same-origin", cache: "no-store" },
    );
    if (!r.ok) {
      setStage("idle");
      return setMessage("This price could not be loaded.");
    }
    const q = (await r.json()) as Quote;
    setQuote(q);
    if (!q.purchasable) {
      setStage("idle");
      return setMessage(q.note ?? "This price is not available.");
    }
    setStage("ready");
  }

  async function pay() {
    setStage("starting");
    setMessage(null);
    const r = await fetch("/api/proxy/billing/purchases", {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        "content-type": "application/json",
        "x-csrf-token": token,
        "idempotency-key": idempotencyKey.current,
      },
      body: JSON.stringify({ planPriceId }),
    });
    if (!r.ok) {
      setStage("idle");
      const problem = (await r.json().catch(() => ({}))) as { detail?: string };
      return setMessage(problem.detail ?? "The purchase could not be started.");
    }
    const checkout = (await r.json()) as CheckoutView;
    try {
      const Razorpay = await loadCheckout();
      const rz = new Razorpay({
        ...razorpayOptions(checkout),
        handler: (resp: {
          razorpay_order_id?: string;
          razorpay_payment_id?: string;
          razorpay_signature?: string;
        }) => void afterCheckout(checkout, resp),
        modal: { ondismiss: () => setStage((s) => (s === "paying" ? "idle" : s)) },
      });
      rz.on("payment.failed", () => {
        setStage("idle");
        setMessage("The payment did not go through. You have not been charged; you can try again.");
      });
      setStage("paying");
      rz.open();
    } catch {
      setStage("idle");
      setMessage("The payment window could not be opened. Please try again.");
    }
  }

  async function afterCheckout(
    checkout: CheckoutView,
    resp: { razorpay_order_id?: string; razorpay_payment_id?: string; razorpay_signature?: string },
  ) {
    setStage("confirming");
    // best effort: tells the server what the browser saw; the server's answer changes nothing about access
    await fetch(`/api/proxy/billing/purchases/${checkout.intentId}/confirm`, {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "content-type": "application/json", "x-csrf-token": token },
      body: JSON.stringify({
        razorpayOrderId: resp.razorpay_order_id,
        razorpayPaymentId: resp.razorpay_payment_id,
        razorpaySignature: resp.razorpay_signature,
      }),
    }).catch(() => undefined);

    setStage("waiting");
    for (const delay of pollDelaysMs()) {
      await new Promise((r) => setTimeout(r, delay));
      const s = await fetch(`/api/proxy/billing/purchases/${checkout.intentId}`, {
        credentials: "same-origin",
        cache: "no-store",
      }).catch(() => null);
      if (!s?.ok) continue;
      const intent = (await s.json()) as Intent;
      if (isFinal(intent.status)) {
        setMessage(statusMessage(intent.status));
        setStage(intent.status === "paid" ? "paid" : "failed");
        if (intent.status === "paid") router.push("/billing");
        return;
      }
    }
    setMessage(statusMessage("payment_pending"));
    setStage("waiting");
  }

  const busy =
    stage === "quoting" || stage === "starting" || stage === "paying" || stage === "confirming";
  return (
    <div className="flex flex-col gap-2" data-testid="buy">
      {stage === "ready" && quote ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm" data-testid="quote-amount">
            You will pay <strong>{formatInr(quote.amountInclTaxMinor ?? 0)}</strong> (final price).
            Test mode: no real money is charged.
          </p>
          <div className="flex gap-2">
            <button className={btn} onClick={() => void pay()} data-testid="pay">
              Pay {formatInr(quote.amountInclTaxMinor ?? 0)}
            </button>
            <button className={btn} onClick={() => setStage("idle")}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <button
          className={btn}
          disabled={busy || stage === "waiting"}
          onClick={() => void showQuote()}
          data-testid="buy-start"
        >
          {stage === "waiting" ? "Confirming payment…" : "Buy"}
        </button>
      )}
      {message && (
        <p role="status" className="text-sm" data-testid="buy-message">
          {message}
        </p>
      )}
    </div>
  );
}
