import Link from "next/link";
import { Logo } from "@/components/site-nav";

const COLUMNS: { title: string; links: { href: string; label: string }[] }[] = [
  {
    title: "Learn",
    links: [
      { href: "/courses", label: "All courses" },
      { href: "/my-courses", label: "My learning" },
      { href: "/dashboard", label: "Progress dashboard" },
    ],
  },
  {
    title: "Practice",
    links: [
      { href: "/problems", label: "Problem bank" },
      { href: "/problems/random", label: "Random problem" },
      { href: "/my-problems", label: "My problems" },
    ],
  },
  {
    title: "Account",
    links: [
      { href: "/pricing", label: "Plans and pricing" },
      { href: "/billing", label: "Billing and receipts" },
      { href: "/account", label: "Account settings" },
    ],
  },
  {
    title: "Company",
    links: [
      { href: "/#faq", label: "FAQ" },
      { href: "/#features", label: "What you get" },
      { href: "/#how-it-works", label: "How it works" },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="border-t bg-surface" data-testid="site-footer">
      <div className="mx-auto grid w-full max-w-7xl gap-10 px-6 py-14 md:grid-cols-[1.4fr_repeat(4,1fr)]">
        <div className="space-y-3">
          <Logo />
          <p className="max-w-xs text-sm text-muted">
            Courses, practice problems and interview preparation for software engineers, built to be
            clear, honest and effective.
          </p>
        </div>
        {COLUMNS.map((c) => (
          <nav key={c.title} aria-label={c.title} className="space-y-3 text-sm">
            <h2 className="font-semibold">{c.title}</h2>
            <ul className="space-y-2 text-muted">
              {c.links.map((l) => (
                <li key={l.href}>
                  <Link href={l.href} className="hover:text-foreground">
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}
      </div>
      <div className="border-t">
        <p className="mx-auto w-full max-w-7xl px-6 py-5 text-xs text-muted">
          © {new Date().getUTCFullYear()} EngineerHub. All rights reserved. Payments run in test
          mode until the platform goes live.
        </p>
      </div>
    </footer>
  );
}
