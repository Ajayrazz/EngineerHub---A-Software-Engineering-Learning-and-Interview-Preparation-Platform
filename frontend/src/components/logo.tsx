import Link from "next/link";

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
