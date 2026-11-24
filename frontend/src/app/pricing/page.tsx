import type { Metadata } from "next";
import { BuyButton } from "@/components/billing/buy-button";
import { SubscribeButton } from "@/components/billing/subscribe-button";
import { PageHeader, ReaderShell } from "@/components/reader/ui";
import { describePrice } from "@/lib/billing/format";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Plan = components["schemas"]["PlanView"];
type Config = components["schemas"]["PaymentsConfig"];

export const metadata: Metadata = {
  title: "Pricing · EngineerHub",
  description: "Plans and prices for premium content.",
};

export default async function PricingPage() {
  const session = await readerSession();
  const [plans, config] = await Promise.all([
    readerGet<Plan[]>("plans"),
    readerGet<Config>("billing/config"),
  ]);
  const enabled = config.ok && config.data.paymentsEnabled === true;
  return (
    <ReaderShell username={session?.username} wide>
      <PageHeader
        title="Pricing"
        lead="Simple plans in rupees. Free lessons and problems stay open to everyone."
      />
      {enabled ? (
        <p
          className="rounded-xl border bg-warn/10 border-warn/50 p-3 text-sm"
          data-testid="test-mode-note"
        >
          <strong>Test mode.</strong> Payments run against the payment provider&apos;s test
          environment: no real money is charged. The amount you see is the final amount you pay.
        </p>
      ) : (
        <p className="rounded-xl border bg-surface/70 p-3 text-sm" data-testid="payments-note">
          Online payment is not available yet. Prices below are final amounts; when payments open,
          the amount you see is the amount you pay.
        </p>
      )}
      {!plans.ok ? (
        <p role="alert" className="text-sm">
          Pricing could not be loaded. Please try again shortly.
        </p>
      ) : plans.data.length === 0 ? (
        <p className="text-sm" data-testid="no-plans">
          Pricing has not been published yet. Free lessons and problems stay open to everyone.
        </p>
      ) : (
        <ul className="grid gap-5 md:grid-cols-2 lg:grid-cols-3" data-testid="plans">
          {plans.data.map((p) => (
            <li key={p.id} className="glass card-hover flex flex-col gap-3 p-6" data-testid="plan">
              <h2 className="text-xl font-bold">{p.name}</h2>
              {(p.entitlements ?? []).includes("premium_content") && (
                <p className="text-sm">Includes all premium lessons and problems.</p>
              )}
              <ul className="flex flex-col gap-3 text-sm">
                {(p.prices ?? []).map((price) => (
                  <li
                    key={price.id}
                    className="flex flex-col gap-2 rounded-lg border bg-background/40 p-3"
                    data-testid="price"
                  >
                    <span>{describePrice(price)}</span>
                    {enabled && price.billingMode === "ONE_TIME" && price.id && (
                      <BuyButton csrfToken={session?.csrfToken ?? null} planPriceId={price.id} />
                    )}
                    {enabled && price.billingMode === "RECURRING" && price.id && (
                      <SubscribeButton
                        csrfToken={session?.csrfToken ?? null}
                        planPriceId={price.id}
                      />
                    )}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </ReaderShell>
  );
}
