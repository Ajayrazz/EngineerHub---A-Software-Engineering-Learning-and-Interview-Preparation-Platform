# 23 — Payments test matrix (docs/21 §10)

Every item the payments design required to be tested, mapped to the tests that cover it, with the gaps stated. Checked on 2026-11-24 against the code at the end of M6.7.

**Read this first:** every test below runs against a **provider test double** (the real signature code, a fake HTTP call). Nothing has been exercised against Razorpay itself, so "covered" means "the server side behaves as designed when the provider behaves as we believe it does". What is unverified against the real provider is listed per feature in ADR-050, 052, 054 and 055.

Test classes live in `backend/src/test/java/com/engineerhub/billing/internal/` unless noted. `E2E` = `frontend/e2e/billing.spec.ts`.

| docs/21 §10 item | Passes (one-time) | Auto-renewing | Status |
|---|---|---|---|
| **Happy path**, monthly | `PurchaseIT.aVerifiedCapturedPaymentGrantsOneMonthOfAccessExactlyOnce` | `SubscriptionIT.aVerifiedChargeGrantsAccessOnceAndRenewalsExtendFromTheEndOfThePeriod` | covered |
| **Happy path**, annual | `PurchaseIT.anAnnualPassLastsAYearAndASecondPurchaseExtendsFromTheEndOfTheFirst` | `SubscriptionIT.anAnnualSubscriptionsChargeGrantsAYearNotAMonth` | covered |
| **Failed payment** | `PurchaseIT.aFailedAttemptCanStillBeFollowedByASuccessfulPaymentOnTheSameOrder`, `aProviderFailureLeavesAFailedIntentAndAClearError` | `SubscriptionIT.lifecycleEventsNeverRegressAndNeverGrant` (pending, past_due, retry succeeds), `aProviderFailureLeavesNoLiveSubscriptionAndTheUserCanRetry` | covered |
| **Abandoned checkout** | `ReconciliationIT.anAbandonedPurchaseExpiresButAPaymentThatStillArrivesIsHonoured` | `ReconciliationIT.anAbandonedSubscriptionCheckoutDoesNotBlockStartingAgain`, `theNightlyRunExpiresSubscriptionCheckoutsNobodyFinishedButNeverOnesThatWerePaid` | covered (M6.7 added the expiry; before it nothing ever expired) |
| **Client verify vs webhook race (single grant)** | `PurchaseIT.theBrowserCallbackAndTheWebhookRacingGrantExactlyOnce`, `confirmingVerifiesTheSignatureButNeverGrantsAccess` | `SubscriptionIT.confirmChecksTheSignatureAgainstTheStoredSubscriptionAndNeverGrants` | covered |
| **Forged webhooks** | `PurchaseIT.invalidMissingOrTamperedSignaturesAreRejectedAndChangeNothing`, `RazorpaySignaturesTest` (independent HMAC vectors) | `SubscriptionIT.aForgedOrUnsignedSubscriptionEventChangesNothing` | covered |
| **Duplicate webhooks** | `PurchaseIT.aVerifiedCapturedPaymentGrantsOneMonthOfAccessExactlyOnce` | `SubscriptionIT` (redelivery and replay under another event id) | covered |
| **Out-of-order webhooks** | `PurchaseIT.unknownOrdersAndNonCapturedPaymentsAreAcknowledgedAndIgnored` | `SubscriptionIT.lifecycleEventsNeverRegressAndNeverGrant`, `aChargeThatArrivesAfterCancellationStillBecomesAccess...` | covered |
| **Amount / currency mismatch** | `PurchaseIT.amountOrCurrencyMismatchesGrantNothingAndFlagTheIntent` | `SubscriptionIT.aChargeWithTheWrongAmountOrCurrencyGrantsNothing...` | covered |
| **Expiry boundary** | `CoverageEndTest`, `BillingIT` (expired, not-yet-started, revoked periods) | same code path | covered |
| **Grace period** | n/a | **not built**: access ends at the paid-through date (Pending Decision P-2) | **gap by design** |
| **Renewal success / failure sequence** | n/a | `SubscriptionIT` (renewals chain; pending, halted, cancelled, completed) | covered |
| **Cancel at cycle end** | n/a | `SubscriptionIT.cancelStopsRenewalAtCycleEndKeepsPaidAccessAndIsIdempotent`, `aProviderFailureWhileCancellingLeavesTheSubscriptionRunning`; E2E | covered |
| **Resume** | n/a | **not built**: a cancelled subscription cannot be resumed, the user buys again (P-3) | **gap by design** |
| **Rebuy** | `PurchaseIT.buyingAgainAfterAPassExpiredStartsNowNotFromTheOldEnd` | `SubscriptionIT.lifecycleEventsNeverRegressAndNeverGrant` (subscribe again after the end) | covered |
| **Overlap rule (P-1)** | `PurchaseIT.anAnnualPass...ExtendsFromTheEndOfTheFirst` | `SubscriptionIT.onlyOneLiveSubscriptionPerUserAndPlan`, `CoverageEndTest` | covered (the rule itself is a proposed default) |
| **Refund: approve, reject, partial, provider failure** | `RefundIT.aFullRefundEndsAccess...`, `rejectingIsFinal...`, `partialRefundsAddUpButNeverExceedWhatWasPaid`, `aProviderFailureChangesNothingAndARetryNeedsAnExplicitCheck`, `aLargeRefundNeedsASecondApprover...`, `twoAdminsApprovingAtOnceRefundOnlyOnce`, `aPendingRefundIsFinishedOnceByTheSignedWebhook` | `RefundIT.refundingASubscriptionPaymentDoesNotCancelTheSubscription` | covered |
| **Invoice numbering under concurrency** | `DocumentIT.concurrentIssuesOfOnePaymentCreateOneDocumentAndConsumeOneNumber`, `numbersAreConsecutiveWithoutGaps`, `issuedDocumentsCannotBeChangedOrDeletedEvenWithDirectSql` | same | covered |
| **Tax-inclusive maths (property)** | `TaxMathTest.propertyAgainstAnIndependentBigDecimalReference` (100,000 cases, net + tax = total), boundaries | same | covered |
| **Tax mode UNCONFIGURED / DISABLED / ENABLED** | `DocumentIT` (nothing issued; receipts without tax wording; invoices from rules; no applicable rule) | same | covered (what ENABLED must contain legally is P-4) |
| **Live-gate enforcement** | `LiveGateIT` (permissions, machine prerequisites, all-or-nothing, un-tick closes, open gate still does not lift the refusal); `RazorpaySignaturesTest.liveModeAndLiveKeysAreRefusedAtStartup` | n/a | covered |
| **Reconciliation drift** | `ReconcilerTest` (every kind and every quiet case), `ReconciliationIT` (each kind, both fixes, dedupe, outage, concurrency) | n/a | covered |
| **Entitlement cache invalidation** | there is **no cache**: access is derived from the database on every request (ADR-049), so revocation and expiry apply on the next request; `BillingIT` proves it | n/a | **not applicable** |

## Also covered, beyond §10
Authorization of every billing endpoint (401 / 403 tables in each `*IT`), ownership isolation (another user's payment, subscription, refund, credit note or document looks missing), no personal data sent to the provider, idempotency keys, provider failures and retries, credit notes (`RefundIT`, `DocumentPdfTest`), browser flows (`E2E`), and the OpenAPI contract (`ApiSchemaNamesArchTest` fails if two API types share a name, which would silently overwrite one in the generated client).

## Gaps that matter
0. **Update 2026-11-24:** order, plan and subscription creation and the payment listing were run for real against Razorpay's test API, and a full one-time purchase (Checkout, signed webhook, access) succeeded with a domestic test card (docs/19, ADR-050 note). Refund request shape, `refund.processed` webhooks and the listings are verified, and a full-amount refund through the app has succeeded against the real provider. A subscription through Checkout and its webhooks are verified, and cancellation is now immediate at the provider (ADR-052 note). Renewal and failed-renewal charges, the failure payload, and UPI/e-mandate flows are unverified.
1. **Everything else provider-facing is unverified** (real request and response shapes, real webhook payloads, retry behaviour, Checkout CSP origins). The whole matrix must be re-run against Razorpay's test mode once you provide test keys.
2. **Grace period** (P-2) and **resume** (P-3) are decisions, not defects: nothing is built, so nothing is tested.
3. **Tax invoice legal content** (CGST/SGST/IGST, customer state, rounding rule, numbering rules) awaits P-4.
4. **No load, soak or failure-injection run** of the webhook receiver under real provider retry storms.
