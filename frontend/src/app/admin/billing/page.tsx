import { BillingAdmin } from "@/components/admin/billing-admin";
import { TaxAdmin } from "@/components/admin/tax-admin";
import { AdminShell, Notice } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type Plan = components["schemas"]["PlanView"];
type Tax = components["schemas"]["TaxSettingsView"];

export default async function BillingAdminPage() {
  const ctx = await adminContext("/admin/billing", "billing.manage");
  const [plans, tax] = await Promise.all([
    apiGet<Plan[]>(ctx, "admin/billing/plans"),
    apiGet<Tax>(ctx, "admin/billing/tax"),
  ]);
  return (
    <AdminShell title="Billing" active="billing" global={ctx.global} scoped={ctx.scoped}>
      {!plans.ok ? (
        <Notice kind="error">{plans.detail}</Notice>
      ) : (
        <div className="flex flex-col gap-8">
          <BillingAdmin csrfToken={ctx.csrfToken} plans={plans.data} />
          {tax.ok ? (
            <TaxAdmin csrfToken={ctx.csrfToken} tax={tax.data} />
          ) : (
            <Notice kind="error">{tax.detail}</Notice>
          )}
        </div>
      )}
    </AdminShell>
  );
}
