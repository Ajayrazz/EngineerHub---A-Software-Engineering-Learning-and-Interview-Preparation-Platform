import Link from "next/link";
import type { ReactNode } from "react";

export function ReaderShell({
  children,
  username,
  wide = false,
}: {
  children: ReactNode;
  username?: string | null;
  wide?: boolean;
}) {
  return (
    <>
      <header className="border-b">
        <nav
          aria-label="Main"
          className="mx-auto flex w-full max-w-6xl items-center gap-4 px-6 py-3 text-sm"
        >
          <Link href="/" className="font-semibold">
            EngineerHub
          </Link>
          <Link href="/courses" className="hover:underline">
            Courses
          </Link>
          <Link href="/my-courses" className="hover:underline">
            My courses
          </Link>
          <Link href="/problems" className="hover:underline">
            Problems
          </Link>
          <Link href="/my-problems" className="hover:underline">
            My problems
          </Link>
          <span className="ml-auto flex items-center gap-3">
            {username ? (
              <Link href="/account" className="hover:underline" data-testid="nav-user">
                {username}
              </Link>
            ) : (
              <a href="/auth/login" className="hover:underline">
                Log in
              </a>
            )}
          </span>
        </nav>
      </header>
      <main
        className={`mx-auto flex w-full flex-1 flex-col gap-6 p-6 ${wide ? "max-w-6xl" : "max-w-4xl"}`}
      >
        {children}
      </main>
    </>
  );
}

export function TierBadge({ tier, preview }: { tier?: string; preview?: boolean }) {
  if (tier !== "premium") return null;
  return (
    <span
      className="rounded border px-1.5 py-0.5 text-xs"
      title={preview ? "Free preview" : "Premium"}
    >
      {preview ? "premium · free preview" : "premium"}
    </span>
  );
}

export function ProgressBar({ percent, label }: { percent: number; label: string }) {
  return (
    <div
      role="progressbar"
      aria-valuenow={percent}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
      className="h-2 w-full rounded bg-black/10 dark:bg-white/15"
    >
      <div className="h-2 rounded bg-foreground" style={{ width: `${percent}%` }} />
    </div>
  );
}
