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
      className={`rounded-lg px-2.5 py-1 text-sm ${active === key ? "bg-accent/15 font-medium text-accent" : "text-muted hover:bg-surface-2 hover:text-foreground"}`}
    >
      {label}
    </Link>
  );
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 p-8">
      <nav aria-label="Admin" className="flex items-center gap-2 border-b pb-3">
        <span className="mr-2 rounded-md bg-accent px-2 py-0.5 text-xs font-bold uppercase tracking-wide text-accent-ink">
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
        <Link href="/account" className="ml-auto text-sm opacity-70 hover:underline">
          Account
        </Link>
      </nav>
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children}
    </main>
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
