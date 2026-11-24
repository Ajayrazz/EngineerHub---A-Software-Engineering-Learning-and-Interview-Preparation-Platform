import Link from "next/link";
import { BookOpen, Code2, CreditCard, ScrollText, Users, type LucideIcon } from "lucide-react";
import { AdminShell } from "@/components/admin/ui";
import { adminContext } from "@/lib/admin/server";

export default async function AdminHome() {
  const ctx = await adminContext("/admin");
  const can = (g: string) => ctx.global.includes(g);
  const cards = [
    can("user.read") && {
      href: "/admin/users",
      title: "Users",
      icon: Users,
      text: "Search accounts, change status, staff roles and scoped grants.",
    },
    (can("course.edit") || ctx.scoped.includes("course.edit")) && {
      href: "/admin/courses",
      title: "Courses",
      icon: BookOpen,
      text: "Write, preview and publish lessons.",
    },
    (can("problem.edit") || ctx.scoped.includes("problem.edit")) && {
      href: "/admin/problems",
      title: "Problems",
      icon: Code2,
      text: "Author problems and test cases.",
    },
    can("billing.manage") && {
      href: "/admin/billing",
      title: "Billing",
      icon: CreditCard,
      text: "Plans, prices and premium access.",
    },
    can("audit.read") && {
      href: "/admin/audit",
      title: "Audit log",
      icon: ScrollText,
      text: "Who did what, with chain verification.",
    },
  ].filter(Boolean) as { href: string; title: string; icon: LucideIcon; text: string }[];
  return (
    <AdminShell title="Administration" active="home" global={ctx.global} scoped={ctx.scoped}>
      <p data-testid="admin-welcome" className="text-sm opacity-80">
        Signed in as {ctx.username}. Everything you do here is checked again by the API and recorded
        in the audit log.
      </p>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((c) => (
          <li key={c.href} className="glass card-hover p-5">
            <Link className="flex items-center gap-2 text-lg font-semibold" href={c.href}>
              <c.icon size={18} aria-hidden className="text-accent-2" />
              {c.title}
            </Link>
            <p className="mt-2 text-sm text-muted">{c.text}</p>
          </li>
        ))}
      </ul>
    </AdminShell>
  );
}
