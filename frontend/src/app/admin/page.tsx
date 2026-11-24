import Link from "next/link";
import {
  BookOpen,
  Code2,
  CreditCard,
  FilePlus2,
  ListChecks,
  PlusCircle,
  ScrollText,
  Users,
  type LucideIcon,
} from "lucide-react";
import { AdminShell } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import { roleLabel, staffInfo } from "@/lib/admin/staff";
import type { components } from "@/lib/api/schema";

type Course = components["schemas"]["CourseListItem"];
type Problem = components["schemas"]["ProblemListItem"];
type UserPage = components["schemas"]["UserPage"];

function Stat({
  icon: Icon,
  label,
  value,
  note,
  testid,
}: {
  icon: LucideIcon;
  label: string;
  value: number | string;
  note?: string;
  testid?: string;
}) {
  return (
    <div className="glass p-5" data-testid={testid}>
      <p className="flex items-center gap-2 text-sm text-muted">
        <Icon size={16} aria-hidden className="text-accent-2" />
        {label}
      </p>
      <p className="mt-1 text-4xl font-bold tracking-tight">{value}</p>
      {note && <p className="mt-1 text-xs text-muted">{note}</p>}
    </div>
  );
}

export default async function AdminHome() {
  const ctx = await adminContext("/admin");
  const staff = await staffInfo();
  const can = (g: string) => ctx.global.includes(g);
  const canCourses = can("course.edit") || ctx.scoped.includes("course.edit");
  const canProblems = can("problem.edit") || ctx.scoped.includes("problem.edit");

  const [courses, problems, users] = await Promise.all([
    canCourses ? apiGet<Course[]>(ctx, "admin/courses") : null,
    canProblems ? apiGet<Problem[]>(ctx, "admin/problems") : null,
    can("user.read") ? apiGet<UserPage>(ctx, "admin/users?size=1") : null,
  ]);
  const cs = courses?.ok ? courses.data : [];
  const ps = problems?.ok ? problems.data : [];
  const draftCourses = cs.filter((c) => c.status === "draft").length;
  const draftProblems = ps.filter((p) => p.status === "draft").length;
  const unverified = ps.filter((p) => p.validationStatus !== "verified").length;

  const actions = [
    canCourses && {
      href: "/admin/courses",
      title: "Add a course",
      icon: FilePlus2,
      text: "Create a course, then add sections, lessons, quizzes and visualizations.",
    },
    canProblems && {
      href: "/admin/problems",
      title: "Add a problem",
      icon: PlusCircle,
      text: "Write the statement, hints and test cases, then publish.",
    },
    can("user.read") && {
      href: "/admin/users",
      title: "Manage users",
      icon: Users,
      text: "Search accounts, change status, roles and scoped grants.",
    },
    can("billing.manage") && {
      href: "/admin/billing",
      title: "Plans and billing",
      icon: CreditCard,
      text: "Plans, prices, premium access, refunds and reconciliation.",
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
      <p data-testid="admin-welcome" className="text-sm text-muted">
        Signed in as {ctx.username}
        {staff ? ` (${roleLabel(staff.roles)})` : ""}. Everything you do here is checked again by
        the API and recorded in the audit log.
      </p>

      <section aria-label="Overview" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {courses && (
          <Stat
            icon={BookOpen}
            label="Courses"
            value={cs.length}
            note={`${draftCourses} draft`}
            testid="stat-courses"
          />
        )}
        {problems && (
          <Stat
            icon={Code2}
            label="Problems"
            value={ps.length}
            note={`${draftProblems} draft · ${unverified} unverified`}
            testid="stat-problems"
          />
        )}
        {users?.ok && (
          <Stat icon={Users} label="Users" value={users.data.total ?? 0} testid="stat-users" />
        )}
        {(draftCourses > 0 || draftProblems > 0) && (
          <Stat
            icon={ListChecks}
            label="Waiting to publish"
            value={draftCourses + draftProblems}
            note="drafts you can finish"
          />
        )}
      </section>

      <section aria-label="What you can do" className="flex flex-col gap-3">
        <h2 className="text-xl font-semibold">What you can do</h2>
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {actions.map((c) => (
            <li key={c.href} className="glass card-hover p-5">
              <Link className="flex items-center gap-2 text-lg font-semibold" href={c.href}>
                <c.icon size={18} aria-hidden className="text-accent-2" />
                {c.title}
              </Link>
              <p className="mt-2 text-sm text-muted">{c.text}</p>
            </li>
          ))}
        </ul>
        {!can("user.read") && !can("billing.manage") && (
          <p className="text-sm text-muted">
            Your role covers content only. Users, billing and the audit log are for administrators.
          </p>
        )}
      </section>
    </AdminShell>
  );
}
