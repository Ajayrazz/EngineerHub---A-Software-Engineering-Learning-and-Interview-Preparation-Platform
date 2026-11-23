/**
 * Content-Security-Policy builder (pure, unit-tested). Every page gets a per-request nonce; `strict-dynamic`
 * lets nonce'd scripts load their chunks while blocking injected inline scripts.
 *
 * The payment checkout page is the ONLY page that may talk to Razorpay: its script is loaded by our nonce'd
 * code (strict-dynamic), and it needs frames and XHR to Razorpay's own origins. These origins are not from an
 * authoritative Razorpay list: they must be confirmed with a real test-mode checkout (watch the browser console
 * for CSP violations) before payments are enabled for users.
 */
export const RAZORPAY_ORIGINS = [
  "https://checkout.razorpay.com",
  "https://api.razorpay.com",
  "https://cdn.razorpay.com",
  "https://lumberjack.razorpay.com",
] as const;

export function buildCsp(opts: {
  nonce: string;
  isDev: boolean;
  issuerOrigin: string | null;
  secure: boolean;
  checkout: boolean;
}): string {
  const razorpay = opts.checkout ? RAZORPAY_ORIGINS.join(" ") : "";
  const withRazorpay = (base: string) => (razorpay ? `${base} ${razorpay}` : base);
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${opts.nonce}' 'strict-dynamic'${opts.isDev ? " 'unsafe-eval'" : ""}`,
    `style-src 'self' 'nonce-${opts.nonce}'${opts.isDev ? " 'unsafe-inline'" : ""}`,
    withRazorpay("img-src 'self' blob: data:"),
    "font-src 'self'",
    withRazorpay("connect-src 'self'"),
    // frames are otherwise forbidden (default-src 'self' would allow same-origin frames; we have none)
    ...(razorpay ? [`frame-src ${razorpay}`] : ["frame-src 'none'"]),
    "object-src 'none'",
    "base-uri 'self'",
    `form-action 'self'${opts.issuerOrigin ? ` ${opts.issuerOrigin}` : ""}`,
    "frame-ancestors 'none'",
    ...(opts.secure ? ["upgrade-insecure-requests"] : []),
  ].join("; ");
}

/** Only the pricing page loads the payment checkout. */
export function isCheckoutPath(pathname: string): boolean {
  return pathname === "/pricing";
}
