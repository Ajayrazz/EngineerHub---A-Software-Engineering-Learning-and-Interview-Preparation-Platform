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

/** Page title block with an optional lead line and right-hand actions. */
export function PageHeader({
  title,
  lead,
  children,
}: {
  title: string;
  lead?: string;
  children?: ReactNode;
}) {
  return (
    <div className="hero-glow relative overflow-hidden rounded-2xl border p-8">
      <div className="stars" aria-hidden />
      <div className="relative flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          {lead && <p className="mt-2 max-w-2xl text-muted">{lead}</p>}
        </div>
        {children}
      </div>
    </div>
  );
}
