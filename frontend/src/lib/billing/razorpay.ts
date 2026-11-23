import { CHECKOUT_SCRIPT } from "@/lib/billing/checkout";

export type RazorpayCtor = new (options: Record<string, unknown>) => {
  open(): void;
  on(event: string, cb: (response: unknown) => void): void;
};

declare global {
  interface Window {
    Razorpay?: RazorpayCtor;
  }
}

/** Loads Razorpay Checkout once (browser only). The CSP admits it on /pricing only (ADR-050). */
export function loadCheckout(): Promise<RazorpayCtor> {
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
