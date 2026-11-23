"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { btn } from "@/components/admin/ui";
import {
  newIdempotencyKey,
  pollDelaysMs,
  razorpaySubscriptionOptions,
  subscriptionMessage,
  subscriptionOutcome,
  type Stage,
  type SubscriptionCheckout,
} from "@/lib/billing/checkout";
import { formatInr, subscribeQuoteText } from "@/lib/billing/format";
import { loadCheckout } from "@/lib/billing/razorpay";
import type { components } from "@/lib/api/schema";

type Quote = components["schemas"]["QuoteView"];
type Sub = components["schemas"]["SubscriptionView"];

/**
 * Subscribe flow for one auto-renewing price: show the final amount and how it repeats first, create the
 * subscription, open Razorpay Checkout, tell the server what the browser saw (a hint only) and then WAIT for the
 * server to report a webhook-confirmed charge. Access is never assumed from anything the browser reports.
 */
export function SubscribeButton({
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
        Log in to subscribe
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

  async function subscribe() {
    setStage("starting");
    setMessage(null);
    const r = await fetch("/api/proxy/billing/subscriptions", {
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
      return setMessage(problem.detail ?? "The subscription could not be started.");
    }
    const checkout = (await r.json()) as SubscriptionCheckout;
    try {
      const Razorpay = await loadCheckout();
      const rz = new Razorpay({
        ...razorpaySubscriptionOptions(checkout),
        handler: (resp: {
          razorpay_subscription_id?: string;
          razorpay_payment_id?: string;
          razorpay_signature?: string;
        }) => void afterCheckout(checkout, resp),
        modal: { ondismiss: () => setStage((s) => (s === "paying" ? "idle" : s)) },
      });
      rz.on("payment.failed", () => {
        setStage("idle");
        setMessage(subscriptionMessage("failed"));
      });
      setStage("paying");
      rz.open();
    } catch {
      setStage("idle");
      setMessage("The payment window could not be opened. Please try again.");
    }
  }

  async function afterCheckout(
    checkout: SubscriptionCheckout,
    resp: {
      razorpay_subscription_id?: string;
      razorpay_payment_id?: string;
      razorpay_signature?: string;
    },
  ) {
    setStage("confirming");
    // best effort: the server's answer changes nothing about access
    await fetch(`/api/proxy/billing/subscriptions/${checkout.subscriptionId}/confirm`, {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "content-type": "application/json", "x-csrf-token": token },
      body: JSON.stringify({
        razorpaySubscriptionId: resp.razorpay_subscription_id,
        razorpayPaymentId: resp.razorpay_payment_id,
        razorpaySignature: resp.razorpay_signature,
      }),
    }).catch(() => undefined);

    setStage("waiting");
    for (const delay of pollDelaysMs()) {
      await new Promise((r) => setTimeout(r, delay));
      const s = await fetch(`/api/proxy/billing/subscriptions/${checkout.subscriptionId}`, {
        credentials: "same-origin",
        cache: "no-store",
      }).catch(() => null);
      if (!s?.ok) continue;
      const outcome = subscriptionOutcome((await s.json()) as Sub);
      if (outcome) {
        setMessage(subscriptionMessage(outcome));
        setStage(outcome === "paid" ? "paid" : "failed");
        if (outcome === "paid") router.push("/billing");
        return;
      }
    }
    setMessage(subscriptionMessage("waiting"));
    setStage("waiting");
  }

  const busy =
    stage === "quoting" || stage === "starting" || stage === "paying" || stage === "confirming";
  return (
    <div className="flex flex-col gap-2" data-testid="subscribe">
      {stage === "ready" && quote ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm" data-testid="subscribe-quote">
            {subscribeQuoteText(quote)} Test mode: no real money is charged.
          </p>
          <div className="flex gap-2">
            <button className={btn} onClick={() => void subscribe()} data-testid="subscribe-pay">
              Subscribe and pay {formatInr(quote.amountInclTaxMinor ?? 0)}
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
          data-testid="subscribe-start"
        >
          {stage === "waiting" ? "Confirming payment…" : "Subscribe"}
        </button>
      )}
      {message && (
        <p role="status" className="text-sm" data-testid="subscribe-message">
          {message}
        </p>
      )}
    </div>
  );
}
