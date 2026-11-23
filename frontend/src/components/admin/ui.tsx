import Link from "next/link";
import type { ReactNode } from "react";

export const btn =
  "rounded border px-3 py-1.5 text-sm hover:bg-black/5 disabled:opacity-50 dark:hover:bg-white/10";
export const input = "rounded border bg-transparent px-2 py-1.5 text-sm";

export function AdminShell({
  title,
  active,
  global,
  scoped = [],
  children,
}: {
  title: string;
  active: "home" | "users" | "courses" | "audit";
  global: readonly string[];
  scoped?: readonly string[];
  children: ReactNode;
}) {
  const link = (href: string, label: string, key: typeof active) => (
    <Link
      href={href}
      className={`rounded px-2 py-1 text-sm ${active === key ? "bg-black/10 font-medium dark:bg-white/15" : "hover:bg-black/5 dark:hover:bg-white/10"}`}
    >
      {label}
    </Link>
  );
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 p-8">
      <nav aria-label="Admin" className="flex items-center gap-2 border-b pb-3">
        <span className="mr-2 font-semibold">Admin</span>
        {link("/admin", "Overview", "home")}
        {global.includes("user.read") && link("/admin/users", "Users", "users")}
        {(global.includes("course.edit") || scoped.includes("course.edit")) &&
          link("/admin/courses", "Courses", "courses")}
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
      className={`rounded border px-3 py-2 text-sm ${kind === "error" ? "border-red-500/60" : "border-green-600/60"}`}
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
