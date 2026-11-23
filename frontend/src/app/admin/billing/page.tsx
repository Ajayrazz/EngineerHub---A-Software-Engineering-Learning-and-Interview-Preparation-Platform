import { BillingAdmin } from "@/components/admin/billing-admin";
import { AdminShell, Notice } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type Plan = components["schemas"]["PlanView"];

export default async function BillingAdminPage() {
  const ctx = await adminContext("/admin/billing", "billing.manage");
  const plans = await apiGet<Plan[]>(ctx, "admin/billing/plans");
  return (
    <AdminShell title="Billing" active="billing" global={ctx.global} scoped={ctx.scoped}>
      {!plans.ok ? (
        <Notice kind="error">{plans.detail}</Notice>
      ) : (
        <BillingAdmin csrfToken={ctx.csrfToken} plans={plans.data} />
      )}
    </AdminShell>
  );
}
