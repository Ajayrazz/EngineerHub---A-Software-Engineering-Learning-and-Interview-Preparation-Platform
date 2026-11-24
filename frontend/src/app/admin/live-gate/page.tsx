import { LiveGateAdmin } from "@/components/admin/live-gate-admin";
import { AdminShell, Notice } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type Gate = components["schemas"]["LiveGateView"];

export default async function LiveGatePage() {
  const ctx = await adminContext("/admin/live-gate", "billing.manage");
  const gate = await apiGet<Gate>(ctx, "admin/billing/live-gate");
  return (
    <AdminShell title="Live readiness" active="live-gate" global={ctx.global} scoped={ctx.scoped}>
      {!gate.ok ? (
        <Notice kind="error">{gate.detail}</Notice>
      ) : (
        <LiveGateAdmin
          csrfToken={ctx.csrfToken}
          gate={gate.data}
          canChange={ctx.global.includes("settings.manage")}
        />
      )}
    </AdminShell>
  );
}
