import type { Metadata } from "next";
import { ReaderShell } from "@/components/reader/ui";
import { describePrice } from "@/lib/billing/format";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Plan = components["schemas"]["PlanView"];

export const metadata: Metadata = {
  title: "Pricing · EngineerHub",
  description: "Plans and prices for premium content.",
};

export default async function PricingPage() {
  const session = await readerSession();
  const plans = await readerGet<Plan[]>("plans");
  return (
    <ReaderShell username={session?.username}>
      <h1 className="text-2xl font-semibold">Pricing</h1>
      <p className="rounded border p-3 text-sm" data-testid="payments-note">
        Online payment is not available yet. Prices below are final amounts; when payments open, the
        amount you see is the amount you pay.
      </p>
      {!plans.ok ? (
        <p role="alert" className="text-sm">
          Pricing could not be loaded. Please try again shortly.
        </p>
      ) : plans.data.length === 0 ? (
        <p className="text-sm" data-testid="no-plans">
          Pricing has not been published yet. Free lessons and problems stay open to everyone.
        </p>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2" data-testid="plans">
          {plans.data.map((p) => (
            <li key={p.id} className="flex flex-col gap-2 rounded border p-4" data-testid="plan">
              <h2 className="text-lg font-medium">{p.name}</h2>
              {(p.entitlements ?? []).includes("premium_content") && (
                <p className="text-sm">Includes all premium lessons and problems.</p>
              )}
              <ul className="flex flex-col gap-1 text-sm">
                {(p.prices ?? []).map((price) => (
                  <li key={price.id} data-testid="price">
                    {describePrice(price)}
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
