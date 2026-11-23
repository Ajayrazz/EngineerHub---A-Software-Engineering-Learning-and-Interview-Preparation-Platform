"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { bpsToPercent, percentToBps, settingsProblems } from "@/lib/billing/tax";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type Tax = components["schemas"]["TaxSettingsView"];

const MODES: { value: string; label: string; help: string }[] = [
  {
    value: "UNCONFIGURED",
    label: "Not configured",
    help: "No receipts or invoices are issued. Payments and access work as usual.",
  },
  {
    value: "DISABLED",
    label: "No tax: receipts",
    help: "Receipts are issued and never mention tax. Use this only if you are not charging or collecting tax.",
  },
  {
    value: "ENABLED",
    label: "Tax invoices",
    help: "Tax invoices with the tax split out of the final price, from the rules below.",
  },
];

/**
 * Tax configuration. Everything here is the owner's decision (with an accountant): the system ships no rate, no
 * wording and no registration details. Changes apply to documents issued afterwards; issued documents never change.
 */
export function TaxAdmin({ csrfToken, tax }: { csrfToken: string; tax: Tax }) {
  const router = useRouter();
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState(tax.mode ?? "UNCONFIGURED");
  const [seller, setSeller] = useState({
    legalName: tax.sellerLegalName ?? "",
    address: tax.sellerAddress ?? "",
    state: tax.sellerState ?? "",
    gstin: tax.sellerGstin ?? "",
    sac: tax.sacCode ?? "",
  });
  const [ruleName, setRuleName] = useState("");
  const [ruleRate, setRuleRate] = useState("");
  const [ruleFrom, setRuleFrom] = useState("");
  const [ruleTo, setRuleTo] = useState("");

  const rules = tax.rules ?? [];
  const activeRules = rules.filter((r) => r.active).length;
  const problems = settingsProblems(mode, seller, activeRules);
  const bps = percentToBps(ruleRate);

  async function run(path: string, method: "PUT" | "POST" | "PATCH", body: unknown, ok: string) {
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

  async function save(e: FormEvent) {
    e.preventDefault();
    await run(
      "admin/billing/tax",
      "PUT",
      {
        mode,
        sellerLegalName: seller.legalName,
        sellerAddress: seller.address,
        sellerState: seller.state,
        sellerGstin: seller.gstin.trim().toUpperCase(),
        sacCode: seller.sac.trim(),
      },
      "Tax settings saved. They apply to documents issued from now on.",
    );
  }

  async function addRule(e: FormEvent) {
    e.preventDefault();
    if (bps === null) return;
    const ok = await run(
      "admin/billing/tax/rules",
      "POST",
      { name: ruleName, rateBps: bps, validFrom: ruleFrom, validTo: ruleTo || undefined },
      "Rule added.",
    );
    if (ok) {
      setRuleName("");
      setRuleRate("");
      setRuleFrom("");
      setRuleTo("");
    }
  }

  const field = (key: keyof typeof seller, label: string, max: number, name: string) => (
    <label className="flex flex-col gap-1 text-sm">
      {label}
      <input
        className={`${input} w-72`}
        name={name}
        value={seller[key]}
        maxLength={max}
        onChange={(e) => setSeller({ ...seller, [key]: e.target.value })}
      />
    </label>
  );

  return (
    <section
      className="flex flex-col gap-4 rounded border p-4"
      aria-label="Tax and documents"
      data-testid="tax-admin"
    >
      <h2 className="text-lg font-medium">Tax and documents</h2>
      <p className="rounded border p-3 text-sm opacity-80">
        This is configuration, not tax advice: rates, registration numbers, codes and wording come
        from you and your accountant. The tax invoice shows one tax line; it does not yet split
        CGST/SGST/IGST or collect the customer&apos;s state, so do not use &quot;Tax invoices&quot;
        for real customers until that is settled. While payments run in test mode every document
        says it is not valid.
      </p>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}

      <form onSubmit={save} className="flex flex-col gap-3" aria-label="Tax settings">
        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium">Documents issued for payments</legend>
          {MODES.map((m) => (
            <label key={m.value} className="flex flex-col text-sm">
              <span>
                <input
                  type="radio"
                  name="tax-mode"
                  value={m.value}
                  checked={mode === m.value}
                  onChange={() => setMode(m.value)}
                />{" "}
                {m.label}
              </span>
              <span className="ml-5 opacity-70">{m.help}</span>
            </label>
          ))}
        </fieldset>
        <div className="flex flex-wrap gap-3">
          {field("legalName", "Seller legal name", 200, "seller-name")}
          {field("address", "Seller address", 500, "seller-address")}
          {field("state", "Seller state", 60, "seller-state")}
          {field("gstin", "GSTIN (15 characters)", 15, "seller-gstin")}
          {field("sac", "Service code (SAC)", 8, "seller-sac")}
        </div>
        {problems.length > 0 && (
          <ul className="list-disc pl-5 text-sm" data-testid="tax-problems">
            {problems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
        <div>
          <button
            className={btn}
            disabled={busy || problems.length > 0}
            type="submit"
            data-testid="tax-save"
          >
            Save tax settings
          </button>
        </div>
      </form>

      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-medium">Tax rules (used only for tax invoices)</h3>
        {rules.length === 0 ? (
          <p className="text-sm" data-testid="no-tax-rules">
            No rules. None is assumed: add the rate your accountant gives you.
          </p>
        ) : (
          <table className="w-full text-left text-sm" data-testid="tax-rules">
            <thead>
              <tr className="border-b opacity-70">
                <th className="py-2">Name</th>
                <th>Rate</th>
                <th>From</th>
                <th>To</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id} className="border-b" data-testid="tax-rule">
                  <td className="py-2">{r.name}</td>
                  <td>{bpsToPercent(r.rateBps ?? 0)}</td>
                  <td>{r.validFrom}</td>
                  <td>{r.validTo ?? "open"}</td>
                  <td>{r.active ? "active" : "off"}</td>
                  <td>
                    <button
                      className={btn}
                      disabled={busy}
                      onClick={() =>
                        void run(
                          `admin/billing/tax/rules/${r.id}`,
                          "PATCH",
                          { active: !r.active },
                          r.active ? "Rule switched off." : "Rule switched on.",
                        )
                      }
                    >
                      {r.active ? "Switch off" : "Switch on"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <form
          onSubmit={addRule}
          className="flex flex-wrap items-end gap-3"
          aria-label="New tax rule"
        >
          <label className="flex flex-col gap-1 text-sm">
            Name on the invoice
            <input
              className={`${input} w-48`}
              name="rule-name"
              value={ruleName}
              required
              maxLength={60}
              onChange={(e) => setRuleName(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Rate (%)
            <input
              className={`${input} w-24`}
              name="rule-rate"
              value={ruleRate}
              required
              inputMode="decimal"
              onChange={(e) => setRuleRate(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Valid from
            <input
              className={input}
              type="date"
              name="rule-from"
              value={ruleFrom}
              required
              onChange={(e) => setRuleFrom(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Valid until (optional)
            <input
              className={input}
              type="date"
              name="rule-to"
              value={ruleTo}
              onChange={(e) => setRuleTo(e.target.value)}
            />
          </label>
          <button
            className={btn}
            disabled={busy || bps === null || !ruleName.trim() || !ruleFrom}
            type="submit"
            data-testid="rule-add"
          >
            Add rule
          </button>
        </form>
        {ruleRate !== "" && bps === null && (
          <p className="text-sm">Enter a rate like 18 or 5.25 (between 0.01 and 50).</p>
        )}
      </div>
    </section>
  );
}
