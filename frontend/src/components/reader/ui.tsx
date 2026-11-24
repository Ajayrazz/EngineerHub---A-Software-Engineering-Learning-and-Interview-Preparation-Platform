import type { ReactNode } from "react";
import { SiteNav } from "@/components/site-nav";

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
      <SiteNav username={username} />
      <main
        className={`mx-auto flex w-full flex-1 flex-col gap-6 p-6 ${wide ? "max-w-7xl" : "max-w-4xl"}`}
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
      className="chip border-accent/50 text-accent"
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
      className="w-full"
    >
      {/* <progress> carries the value as an attribute: inline style attributes are blocked by our CSP. */}
      <progress className="meter" value={percent} max={100} aria-hidden />
    </div>
  );
}
