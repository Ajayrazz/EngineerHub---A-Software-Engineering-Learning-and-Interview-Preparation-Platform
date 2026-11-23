import Link from "next/link";
import { AdminShell } from "@/components/admin/ui";
import { adminContext } from "@/lib/admin/server";

export default async function AdminHome() {
  const ctx = await adminContext("/admin");
  return (
    <AdminShell title="Administration" active="home" global={ctx.global} scoped={ctx.scoped}>
      <p data-testid="admin-welcome" className="text-sm opacity-80">
        Signed in as {ctx.username}. Everything you do here is checked again by the API and recorded
        in the audit log.
      </p>
      <ul className="list-disc space-y-1 pl-6 text-sm">
        {ctx.global.includes("user.read") && (
          <li>
            <Link className="underline" href="/admin/users">
              Users
            </Link>{" "}
            — search accounts, change status, staff roles and scoped grants
          </li>
        )}
        {(ctx.global.includes("course.edit") || ctx.scoped.includes("course.edit")) && (
          <li>
            <Link className="underline" href="/admin/courses">
              Courses
            </Link>{" "}
            — write, preview and publish lessons
          </li>
        )}
        {(ctx.global.includes("problem.edit") || ctx.scoped.includes("problem.edit")) && (
          <li>
            <Link className="underline" href="/admin/problems">
              Problems
            </Link>{" "}
            — author problems and test cases
          </li>
        )}
        {ctx.global.includes("billing.manage") && (
          <li>
            <Link className="underline" href="/admin/billing">
              Billing
            </Link>{" "}
            — plans, prices and premium access
          </li>
        )}
        {ctx.global.includes("audit.read") && (
          <li>
            <Link className="underline" href="/admin/audit">
              Audit log
            </Link>{" "}
            — who did what, and chain verification
          </li>
        )}
      </ul>
    </AdminShell>
  );
}
