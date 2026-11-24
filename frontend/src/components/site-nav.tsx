import Link from "next/link";

const LINKS = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/courses", label: "Courses" },
  { href: "/my-courses", label: "My courses" },
  { href: "/problems", label: "Problems" },
  { href: "/my-problems", label: "My problems" },
  { href: "/pricing", label: "Pricing" },
];

export function Logo() {
  return (
    <Link href="/" className="flex items-center gap-2 text-lg font-bold tracking-tight">
      <span
        aria-hidden
        className="grid h-7 w-7 place-items-center rounded-md bg-accent font-mono text-sm text-accent-ink"
      >
        {"</>"}
      </span>
      <span>
        Engineer<span className="text-accent">Hub</span>
      </span>
    </Link>
  );
}

export function SiteNav({ username }: { username?: string | null }) {
  return (
    <header className="sticky top-0 z-30 border-b bg-background/85 backdrop-blur">
      <nav
        aria-label="Main"
        className="mx-auto flex w-full max-w-7xl flex-wrap items-center gap-x-5 gap-y-2 px-6 py-3 text-sm"
      >
        <Logo />
        <span className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted">
          {LINKS.map((l) => (
            <Link key={l.href} href={l.href} className="hover:text-foreground">
              {l.label}
            </Link>
          ))}
        </span>
        <span className="ml-auto flex items-center gap-3">
          {username && (
            <Link href="/billing" className="text-muted hover:text-foreground">
              My access
            </Link>
          )}
          {username ? (
            <Link
              href="/account"
              className="rounded-full border px-3 py-1 hover:border-accent"
              data-testid="nav-user"
            >
              {username}
            </Link>
          ) : (
            <a href="/auth/login" className="btn-primary">
              Log in
            </a>
          )}
        </span>
      </nav>
    </header>
  );
}
