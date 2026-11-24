import Link from "next/link";
import type { ReactNode } from "react";

export const btn =
  "rounded-full border px-3.5 py-1.5 text-sm hover:border-accent hover:text-accent disabled:opacity-50";
export const input = "rounded-lg border bg-surface px-2.5 py-1.5 text-sm";

export function AdminShell({
  title,
  active,
  global,
  scoped = [],
  children,
}: {
  title: string;
  active:
    | "home"
    | "users"
    | "courses"
    | "problems"
    | "billing"
    | "refunds"
    | "reconciliation"
    | "live-gate"
    | "audit";
  global: readonly string[];
  scoped?: readonly string[];
  children: ReactNode;
}) {
  const link = (href: string, label: string, key: typeof active) => (
    <Link
      href={href}
      className={`rounded-lg px-2.5 py-1.5 text-sm ${active === key ? "bg-accent/15 font-medium text-accent" : "text-muted hover:bg-surface-2 hover:text-foreground"}`}
    >
      {label}
    </Link>
  );
  return (
    <>
      <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 p-6 lg:flex-row">
        <nav
          aria-label="Admin"
          className="glass flex shrink-0 flex-row flex-wrap items-center gap-1 self-start p-3 lg:sticky lg:top-20 lg:w-56 lg:flex-col lg:items-stretch"
        >
          <span className="mb-1 mr-2 w-fit rounded-md bg-accent px-2 py-0.5 text-xs font-bold uppercase tracking-wide text-accent-ink">
            Admin
          </span>
          {link("/admin", "Overview", "home")}
          {global.includes("user.read") && link("/admin/users", "Users", "users")}
          {(global.includes("course.edit") || scoped.includes("course.edit")) &&
            link("/admin/courses", "Courses", "courses")}
          {(global.includes("problem.edit") || scoped.includes("problem.edit")) &&
            link("/admin/problems", "Problems", "problems")}
          {global.includes("billing.manage") && link("/admin/billing", "Billing", "billing")}
          {global.includes("billing.manage") && link("/admin/refunds", "Refunds", "refunds")}
          {global.includes("billing.manage") &&
            link("/admin/reconciliation", "Reconciliation", "reconciliation")}
          {global.includes("billing.manage") &&
            link("/admin/live-gate", "Live readiness", "live-gate")}
          {global.includes("audit.read") && link("/admin/audit", "Audit log", "audit")}
          <Link
            href="/account"
            className="mt-2 rounded-lg px-2.5 py-1 text-sm text-muted hover:text-foreground lg:border-t lg:pt-3"
          >
            Account
          </Link>
        </nav>
        <main className="flex min-w-0 flex-1 flex-col gap-6">
          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          {children}
        </main>
      </div>
      <footer className="border-t">
        <p className="mx-auto w-full max-w-7xl px-6 py-4 text-xs text-muted">
          EngineerHub administration · actions are checked by the API and recorded in the audit log.
        </p>
      </footer>
    </>
  );
}

export function Notice({ kind, children }: { kind: "error" | "ok"; children: ReactNode }) {
  return (
    <p
      role={kind === "error" ? "alert" : "status"}
      className={`rounded border px-3 py-2 text-sm ${kind === "error" ? "border-danger/60 bg-danger/10" : "border-accent/60 bg-accent/10"}`}
    >
      {children}
    </p>
  );
}

export function when(iso?: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toISOString().replace("T", " ").slice(0, 19) + " UTC";
}
