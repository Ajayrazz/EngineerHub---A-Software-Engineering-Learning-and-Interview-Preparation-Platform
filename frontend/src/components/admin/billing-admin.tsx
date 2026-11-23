"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { describePrice, toMinor } from "@/lib/billing/format";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type Plan = components["schemas"]["PlanView"];

/** Plans, entitlements and prices. Prices are tax-inclusive final amounts; they can be switched off, not edited. */
export function BillingAdmin({ csrfToken, plans }: { csrfToken: string; plans: Plan[] }) {
  const router = useRouter();
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");

  async function run(path: string, method: "POST" | "PUT" | "PATCH", body: unknown, ok: string) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, method, body);
    setBusy(false);
    if (!r.ok) {
      setFlash({ kind: "error", text: r.message });
      return false;
    }
    setFlash({ kind: "ok", text: ok });
    router.refresh();
    return true;
  }

  async function create(e: FormEvent) {
    e.preventDefault();
    if (await run("admin/billing/plans", "POST", { code, name }, "Plan created.")) {
      setCode("");
      setName("");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <p className="rounded border p-3 text-sm opacity-80">
        No plans exist until you create them: names and prices are your decision. Amounts are final,
        tax-inclusive prices in rupees. Tax is not configured, so no tax is shown anywhere. Online
        payment arrives in a later milestone; until then access is granted by an admin.
      </p>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}

      <form
        onSubmit={create}
        className="flex flex-wrap items-end gap-3 rounded border p-4"
        aria-label="New plan"
      >
        <label className="flex flex-col gap-1 text-sm">
          Code (lowercase, hyphens)
          <input
            className={input}
            name="plan-code"
            value={code}
            required
            maxLength={40}
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            onChange={(e) => setCode(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Name shown to learners
          <input
            className={`${input} w-64`}
            name="plan-name"
            value={name}
            required
            maxLength={100}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <button className={btn} disabled={busy} type="submit" data-testid="plan-create">
          Create plan
        </button>
      </form>

      {plans.length === 0 && <p className="text-sm">No plans yet.</p>}
      {plans.map((p) => (
        <PlanCard key={p.id} plan={p} busy={busy} run={run} />
      ))}
    </div>
  );
}

function PlanCard({
  plan,
  busy,
  run,
}: {
  plan: Plan;
  busy: boolean;
  run: (
    path: string,
    method: "POST" | "PUT" | "PATCH",
    body: unknown,
    ok: string,
  ) => Promise<boolean>;
}) {
  const [mode, setMode] = useState("ONE_TIME");
  const [unit, setUnit] = useState("month");
  const [amount, setAmount] = useState("");
  const minor = toMinor(amount);
  const premium = (plan.entitlements ?? []).includes("premium_content");
  const base = `admin/billing/plans/${plan.id}`;

  return (
    <section
      className="flex flex-col gap-3 rounded border p-4"
      data-testid="plan-card"
      aria-label={`Plan ${plan.name}`}
    >
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-medium">{plan.name}</h2>
        <code className="text-xs">{plan.code}</code>
        <span className="text-sm" data-testid="plan-active">
          {plan.active ? "active" : "inactive"}
        </span>
        <button
          className={btn}
          disabled={busy}
          onClick={() =>
            void run(
              base,
              "PATCH",
              { active: !plan.active },
              plan.active ? "Plan deactivated." : "Plan activated.",
            )
          }
        >
          {plan.active ? "Deactivate" : "Activate"}
        </button>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={premium}
          disabled={busy}
          onChange={(e) =>
            void run(
              `${base}/entitlements`,
              "PUT",
              { codes: e.target.checked ? ["premium_content"] : [] },
              "Entitlements saved.",
            )
          }
        />
        Includes premium content (lessons and problems)
      </label>
      <ul className="flex flex-col gap-1 text-sm" data-testid="prices">
        {(plan.prices ?? []).map((pr) => (
          <li key={pr.id} className="flex items-center gap-2" data-testid="price-row">
            <span className={pr.active ? "" : "line-through opacity-60"}>{describePrice(pr)}</span>
            <button
              className={btn}
              disabled={busy}
              onClick={() =>
                void run(
                  `${base}/prices/${pr.id}`,
                  "PATCH",
                  { active: !pr.active },
                  pr.active ? "Price switched off." : "Price switched on.",
                )
              }
            >
              {pr.active ? "Switch off" : "Switch on"}
            </button>
          </li>
        ))}
        {(plan.prices ?? []).length === 0 && (
          <li className="opacity-70">No prices: this plan is not shown to learners.</li>
        )}
      </ul>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (minor === null) return;
          void run(
            `${base}/prices`,
            "POST",
            { billingMode: mode, periodUnit: unit, amountInclTaxMinor: minor },
            "Price added.",
          ).then((ok) => ok && setAmount(""));
        }}
      >
        <label className="flex flex-col gap-1 text-sm">
          Type
          <select
            className={input}
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            aria-label="Billing mode"
          >
            <option value="ONE_TIME">one-time pass</option>
            <option value="RECURRING">auto-renewing</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Period
          <select
            className={input}
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
            aria-label="Period"
          >
            <option value="month">month</option>
            <option value="year">year</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Final price (₹, tax-inclusive)
          <input
            className={`${input} w-32`}
            name="price-amount"
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            aria-invalid={amount !== "" && minor === null}
          />
        </label>
        <button
          className={btn}
          disabled={busy || minor === null || minor < 100}
          type="submit"
          data-testid="price-add"
        >
          Add price
        </button>
        {amount !== "" && minor === null && (
          <span className="text-sm text-red-600 dark:text-red-400">
            Enter an amount like 299 or 299.50.
          </span>
        )}
      </form>
    </section>
  );
}
