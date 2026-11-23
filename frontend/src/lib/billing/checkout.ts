import type { components } from "@/lib/api/schema";

export type CheckoutView = components["schemas"]["CheckoutView"];

export const CHECKOUT_SCRIPT = "https://checkout.razorpay.com/v1/checkout.js";

/**
 * Options for Razorpay Checkout, built ONLY from what the server returned for this purchase (amount, currency,
 * order, public key). Nothing here is user input and no secret or personal data is passed to the provider.
 */
export function razorpayOptions(c: CheckoutView) {
  return {
    key: c.keyId ?? "",
    amount: c.amountMinor ?? 0,
    currency: c.currency ?? "INR",
    order_id: c.orderId ?? "",
    name: "EngineerHub",
    description: c.planName ?? "",
  };
}

export function newIdempotencyKey(): string {
  return `buy-${crypto.randomUUID()}`;
}

export type Stage =
  | "idle"
  | "quoting"
  | "ready"
  | "starting"
  | "paying"
  | "confirming"
  | "waiting"
  | "paid"
  | "failed";

/** Statuses after which polling stops. */
export function isFinal(status: string | undefined): boolean {
  return (
    status === "paid" || status === "failed" || status === "needs_review" || status === "expired"
  );
}

export function statusMessage(status: string | undefined): string {
  switch (status) {
    case "paid":
      return "Payment confirmed. Your premium access is active.";
    case "failed":
      return "The payment did not go through. You have not been charged; you can try again.";
    case "needs_review":
      return "We received your payment but need to review it before activating access. We will look into it; please keep your payment reference.";
    case "expired":
      return "This purchase expired. Please start again.";
    default:
      return "Your payment is being confirmed. Access appears as soon as the payment provider confirms it.";
  }
}

/** Poll schedule: quick at first, then slower; about 90 s in total. */
export function pollDelaysMs(): number[] {
  return [...Array(10).fill(1000), ...Array(10).fill(2000), ...Array(12).fill(5000)].slice(0, 32);
}

export type SubscriptionCheckout = components["schemas"]["SubscriptionCheckout"];

/** Razorpay Checkout options for a subscription, built only from what the server returned. */
export function razorpaySubscriptionOptions(c: SubscriptionCheckout) {
  return {
    key: c.keyId ?? "",
    subscription_id: c.providerSubscriptionId ?? "",
    name: "EngineerHub",
    description: c.planName ?? "",
  };
}

/**
 * What polling a subscription tells us. "paid" means the server recorded a verified charge (the only moment access
 * exists); the other settled outcomes stop polling.
 */
export function subscriptionOutcome(v: {
  status?: string;
  paid?: boolean;
}): "paid" | "needs_review" | "failed" | null {
  if (v.paid) return "paid";
  if (v.status === "needs_review") return "needs_review";
  if (v.status === "failed" || v.status === "cancelled" || v.status === "completed")
    return "failed";
  return null;
}

export function subscriptionMessage(
  outcome: "paid" | "needs_review" | "failed" | "waiting",
): string {
  switch (outcome) {
    case "paid":
      return "Subscription active. Your premium access is on.";
    case "needs_review":
      return "We received a payment that does not match this plan and need to review it before activating access. Please keep your payment reference.";
    case "failed":
      return "The subscription could not be started. You have not been charged; you can try again.";
    default:
      return "Your subscription is being confirmed. Access appears as soon as the payment provider confirms the first payment.";
  }
}
