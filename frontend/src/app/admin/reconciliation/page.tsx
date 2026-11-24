import { ReconciliationAdmin } from "@/components/admin/reconciliation-admin";
import { AdminShell, Notice } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type Overview = components["schemas"]["ReconciliationOverview"];

export default async function ReconciliationPage() {
  const ctx = await adminContext("/admin/reconciliation", "billing.manage");
  const overview = await apiGet<Overview>(ctx, "admin/billing/reconciliation");
  return (
    <AdminShell
      title="Reconciliation"
      active="reconciliation"
      global={ctx.global}
      scoped={ctx.scoped}
    >
      {!overview.ok ? (
        <Notice kind="error">{overview.detail}</Notice>
      ) : (
        <ReconciliationAdmin csrfToken={ctx.csrfToken} overview={overview.data} />
      )}
    </AdminShell>
  );
}
