# 21 — Payments, Subscriptions, Tax & Refunds (Razorpay, India-first)

Status: **Design; M6.1 to M6.5 implemented (M6.5: tax configuration and receipts/invoices, see ADR-053; credit notes wait for refunds) (M6.4: subscriptions, see ADR-052; like M6.2 only against a test double and signed simulations) (M6.2 only against a test double and signed simulations: see ADR-050 for what is verified) (plans, tax-inclusive prices, access periods, entitlement decision, admin grants; see ADR-049). Payments, Razorpay, webhooks, tax, invoices and refunds are not implemented.** Razorpay-specific details come from my knowledge of its public API and **must be verified against current Razorpay docs at the start of Phase 5**. Items marked **Pending Decision** are deliberately not assumed.

Updated 2026-11-23 per product-owner decisions: all four purchase options, tax-inclusive pricing, configurable GST, test-mode-only until legal/KYC finalized, configurable refund policy with admin workflow.

## 1. Scope (confirmed)
- Provider: **Razorpay only** (INR, India). Stripe not in v1; the provider interface keeps it possible later.
- Purchase options users can choose between:

| Option | Billing mode | Razorpay primitive (to verify) | Ends when |
|---|---|---|---|
| Monthly pass | `ONE_TIME` | Order + Checkout | period end (no renewal) |
| Annual pass | `ONE_TIME` | Order + Checkout | period end (no renewal) |
| Monthly auto-renewing | `RECURRING` | Subscription on a monthly Plan | user cancels / payments fail / expiry |
| Annual auto-renewing | `RECURRING` | Subscription on an annual Plan | same |

- Users can: see validity & next renewal date & payment status, cancel auto-renew, renew/repurchase, request a refund, download invoices.
- **No Stripe, no live-mode charging** until the Live Readiness Gate (§9) passes.

## 2. One billing core, no duplicated business logic

Key idea: **every successful payment, regardless of mode, produces the same domain fact — "paid for plan P covering period [start, end)" — and entitlements are derived only from those periods.** Modes differ only in *how a payment is initiated and renewed*.

```
catalog:   Plan ──< PlanPrice(billing_mode, interval[month|year], amount_incl_tax_minor, currency)
purchase:  PurchaseIntent(user, plan_price, mode)  ──►  provider order/subscription
payments:  Payment (captured)  ──►  [PaymentCapturedHandler]  ──►  AccessPeriod(user, plan, start, end, source_payment_id)
access:    EntitlementService = union of AccessPeriods where start ≤ now < end (+ grace) 
```

Single handler `PaymentCapturedHandler` is used by both the ONE_TIME webhook (`payment.captured`/`order.paid`) and the RECURRING webhook (`subscription.charged`): it (1) dedupes, (2) validates amount/currency vs the intent/plan snapshot, (3) creates the `Payment` row, (4) appends an `AccessPeriod`, (5) emits `PaymentSucceeded`/`AccessGranted`, (6) triggers invoice generation. Mode-specific code is confined to `provider/` adapters and a small `RenewalPolicy` strategy:

| Strategy | Renewal | Cancellation | Failure |
|---|---|---|---|
| `OneTimeRenewalPolicy` | none; show "Renew" CTA & expiry reminders | n/a (access just expires) | order failed → no access |
| `RecurringRenewalPolicy` | provider charges each cycle → webhook | `cancel_at_cycle_end` via provider API | retries by provider → `PAST_DUE` → grace → `EXPIRED` |

### Package layout
```
payment/
  api/        BillingFacade: quote(), startPurchase(), cancelAutoRenew(), requestRefund(), getBillingSummary()
  internal/
    provider/ PaymentProvider (interface) + RazorpayProvider
    purchase/ PurchaseService, Quote (price, tax breakdown, final payable)
    core/     PaymentCapturedHandler, AccessPeriodService, RenewalPolicy impls
    webhook/  WebhookController, WebhookEventStore, handlers
    tax/      TaxCalculator, TaxRule config
    invoice/  InvoiceService, numbering, PDF worker
    refund/   RefundRequestService, RefundPolicyConfig
    recon/    Nightly reconciliation
subscription/ (module)  Subscription state, EntitlementService, expiry sweeper
```

## 3. State machines

**Purchase intent:** `CREATED → PAYMENT_PENDING → PAID | FAILED | EXPIRED | ABANDONED`.
**Payment:** `CREATED → AUTHORIZED → CAPTURED → PARTIALLY_REFUNDED | REFUNDED`; or `FAILED`.
**Subscription (RECURRING only):** `CREATED → AUTHENTICATED/PENDING → ACTIVE ⇄ PAST_DUE → (GRACE) → EXPIRED`; `ACTIVE → CANCEL_SCHEDULED → CANCELLED(at cycle end)`; `ACTIVE → COMPLETED` if a fixed number of cycles is used; provider states `halted/paused` map into `PAST_DUE`/`PAUSED`.
**Access period:** immutable once created; revoked only by explicit admin/refund action (`revoked_at`, `revoked_reason`).

All transitions are monotonic and guarded (`UPDATE … WHERE status IN (allowed)`); late/out-of-order provider events are stored and ignored if they'd regress state.

### User-facing status mapping (what the UI must show)
| Internal | Shown as | Shows |
|---|---|---|
| ACTIVE (ONE_TIME) | Active until *date* | Valid-through date, "Renew" button within N days of expiry |
| ACTIVE (RECURRING) | Active — renews on *date* | Next renewal date, amount, "Cancel auto-renew" |
| CANCEL_SCHEDULED | Active until *date* — will not renew | "Resume" if provider supports (verify) else "Buy again" |
| PAST_DUE | Payment failed — update payment method | Retry/grace info, deadline, update-payment CTA |
| EXPIRED | Expired on *date* | Re-subscribe CTA |
| PAYMENT_PENDING | Payment processing | Poll; never grant access on client signal |

### Failed recurring payments (design; durations Pending Decision P-2)
Provider retries per its schedule (verify) → on failure emit `RenewalFailed` → email + in-app notice → status `PAST_DUE`. Access continues through a **configurable grace period** (default proposal: 3 days, **Pending Decision P-2**) then entitlements lapse (`EXPIRED`). If the provider later succeeds, `PaymentCapturedHandler` appends a period that picks up from `max(previous_end, now)` per overlap rule P-1.

## 4. Overlapping/stacked purchases & switching — **Pending Decision P-1, P-3**
- **P-1 Overlap:** when a user buys while already active, either (a) *extend* (new period starts at current end) or (b) *block* purchase of the same plan while active. Proposed default: (a) extend for ONE_TIME; block starting a second RECURRING subscription for the same plan while one is active.
- **P-3 Switching/proration** (monthly↔annual, pass↔recurring): proposed v1 = **no proration**; changes take effect at next renewal/expiry, and cancelling auto-renew then buying a pass is allowed. Not final.
The data model (periods) supports any of these without migration.

## 5. Pricing and GST — tax-inclusive (confirmed), rates NOT assumed

- `plan_prices.amount_incl_tax_minor` is the **final payable amount** shown everywhere (pricing page, checkout, Razorpay order/plan amount, invoice total). Users see the final payable amount **before** they pay (the `Quote` endpoint returns price, tax breakdown if applicable, discount, final amount; the UI's pay button shows that number).
- Tax extraction from inclusive price (when tax is enabled): `tax = round(incl − incl / (1 + rate))`, `net = incl − tax` using integer minor units with a single documented rounding rule (half-up on the tax component; to confirm with accountant). CGST/SGST split vs IGST by place of supply.
- **No default GST rate is shipped.** `tax_rules` starts empty. Admin must create rules (rate, SAC code, validity dates).
- **Tax modes (admin setting `billing.tax_mode`):**
  - `DISABLED` — no tax computed or shown; document type "Receipt" (used if the business is not GST-registered). **No tax wording on documents.**
  - `ENABLED` — tax computed from `tax_rules`; document type "Tax Invoice" with breakdown; requires seller GSTIN/legal name/address/state/SAC.
  - Initial state: **`UNCONFIGURED`** → checkout refuses live charges (test mode allowed with a visible "TEST" banner).
- Place of supply needs the customer's **state** when `ENABLED` (collected at checkout); optional customer GSTIN.
- Invoices: gap-free sequential numbers per financial year (row-locked `invoice_sequences`), PDF generated async to private object storage, owner-downloadable; **credit note** on refund. Tax breakdown shown on invoices and payment summaries **where required** (i.e. in `ENABLED` mode).
- Historical correctness: each payment/invoice stores an immutable **snapshot** of price, tax rule id/rate, seller details, so later config edits never alter old invoices.
- **Pending Decision P-4 (accountant):** GST registration status, applicable rate/SAC for the service, rounding rule, invoice format/numbering rules, financial-year handling, treatment of refunds/credit notes, whether OIDAR/other rules apply.

## 6. Razorpay integration & security (unchanged principles, extended)
1. **Never trust the browser:** client-returned `order_id|payment_id|signature` is verified server-side (HMAC-SHA256 with key secret, constant-time) only to speed up UX; **the verified webhook is the source of truth for granting access.**
2. **Webhook:** verify `X-Razorpay-Signature` as HMAC-SHA256 over the **raw body** with the webhook secret; reject on mismatch; fast ack — persist event then process asynchronously.
3. **Idempotency:** `webhook_events(provider, event_id PK)` insert-or-ignore; unique `provider_payment_ref`; unique `(payment_id)` on access-period source; Idempotency-Key header on purchase start.
4. **Amount/currency validation** against the immutable intent/plan snapshot; mismatch → flagged `NEEDS_REVIEW`, no access.
5. **Server-side pricing only.** Client sends `planPriceId`, never an amount.
6. **Recurring specifics (verify):** first payment requires customer authentication/mandate (e-mandate/UPI AutoPay per RBI rules, with per-transaction limits and pre-debit notification requirements — confirm exact rules and limits). Razorpay subscription plans have a fixed amount → our tax-inclusive price maps 1:1. **Coupons on recurring plans are not supported in v1 (Pending Decision P-5)**; one-time passes may use server-validated coupons.
7. **Reconciliation:** nightly job compares provider payments/subscriptions vs ours; drift → alert + `NEEDS_REVIEW` queue.
8. **Secrets:** key id/secret/webhook secret via environment/secret store (portable; see §10), separate test vs live; rotation runbook.
9. **PCI:** card data never touches us (Razorpay Checkout).
10. **Mode guard:** `RAZORPAY_MODE=test|live`; live requires `billing.live_ready=true` (§9) — enforced in code, not convention.

## 7. Refunds — configurable policy + admin workflow

### Proposed policy text (configurable; **final wording Pending Decision P-6 / legal review**)
1. Duplicate payments: refundable after verification.
2. Money deducted but access not activated: investigated; refund (or activation) as appropriate.
3. Service not deliverable due to a verified technical issue: reviewed for refund.
4. After activation and access granted, no refund for change of mind, subject to applicable consumer-protection law.
5. Cancelling auto-renew stops future renewals; **does not refund the current period**.
6. Legally required refunds are honored.

### Configuration (admin `settings`, versioned; edited with audit)
`refund.policy_text_md` (shown to users), `refund.reasons[]` (enabled reasons + whether auto-eligible for review), `refund.request_window_days` (default none/pending), `refund.revoke_access_on_refund` (default true for full refunds), `refund.approval_threshold_minor` (above → second approver), `refund.auto_detect_duplicates`.

### Workflow
```
User files request (in-app: reason, details, payment) ──► REQUESTED
  ──► UNDER_REVIEW (assigned admin; notes; evidence)
  ──► APPROVED ──► PROCESSING (provider refund API) ──► PROCESSED (credit note issued, access revoked/adjusted per policy)
  └─► REJECTED (reason shown to user)         └─► FAILED (provider error; retry/manual)
```
- Admin console: queue with filters (status/reason/age), payment & webhook timeline, user's access periods, one-click approve/reject (full or partial), second-approver for amounts above threshold, refund history per payment.
- Records: `refund_requests` + `refunds` + audit log entries for every transition; refunds reconcile with provider.
- **Auto-flags** (not auto-refunds): duplicate captures for the same intent/plan within a window; captured payment with no access period after N minutes (money-taken-not-activated) → appear in the review queue with a "suggested: activate / refund".
- Refund webhooks (`refund.processed` etc., verify) update status idempotently.
- Subscription cancellation and refund are independent actions.

## 8. Entitlements & access
`EntitlementService.has(userId, code)`: derived from non-revoked `access_periods` with `start ≤ now < end + grace` → plan → `plan_entitlements`. Cached in Redis (event-invalidated, ≤5 min TTL) but **expiry also checked at read time**. Plans/entitlement codes editable by admin without deploy. Free plan is the implicit default.

## 9. Live Readiness Gate (hard block on live payments)
`billing.live_ready` can only be set true (by SUPER_ADMIN, audited) when all are recorded as done: legal entity finalized; Razorpay KYC/live account approved; bank details; tax mode confirmed by accountant (P-4); refund & cancellation policy, ToS, Privacy text published; live keys + webhook secret installed; webhook endpoint verified in live; invoice seller details entered (**real data only — no placeholders/fabricated legal info**); test-mode end-to-end checklist signed. Until then: test mode only, visible "Test payments" banner.

## 10. Testing (mandatory, Razorpay test mode only until the Gate)
Per mode (pass/recurring × monthly/annual): happy path; failed payment; abandoned checkout; client-verify vs webhook race (single grant); forged/duplicate/out-of-order webhooks; amount mismatch; expiry boundary & grace; renewal success/failure sequence; cancel-at-cycle-end; resume/rebuy; overlap rule; refund approve/reject/partial/provider-failure; invoice numbering under concurrency; tax inclusive maths (property tests for rounding & totals = incl price); tax mode DISABLED/ENABLED/UNCONFIGURED behavior; live-gate enforcement; reconciliation drift; entitlement cache invalidation.

## 11. Pending Decisions (payments)
| ID | Topic | Proposed default (not final) |
|---|---|---|
| P-1 | Overlapping purchases | Extend for passes; block duplicate active recurring |
| P-2 | Grace period after failed renewal | 3 days |
| P-3 | Plan switching / proration | None in v1 |
| P-4 | GST registration, rate, SAC, rounding, invoice rules | **Accountant** — no default rate shipped |
| P-5 | Coupons on recurring plans | Not in v1 |
| P-6 | Final refund policy wording & request window | Proposed text above; legal review |
| P-7 | Plan prices (INR) and any free-trial | Not set |
| P-8 | Legal entity type & Razorpay KYC | Not assumed |
