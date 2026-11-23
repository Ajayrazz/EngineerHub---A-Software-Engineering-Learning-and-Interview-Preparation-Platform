"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { btn } from "@/components/admin/ui";

/** Star toggle for a problem. Signed-out visitors get a login link instead. */
export function BookmarkButton({
  csrfToken,
  slug,
  initial,
}: {
  csrfToken: string | null;
  slug: string;
  initial: boolean;
}) {
  const router = useRouter();
  const [on, setOn] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!csrfToken) {
    return (
      <a
        className="text-sm underline"
        href={`/auth/login?returnTo=${encodeURIComponent(`/problems/${slug}`)}`}
      >
        Log in to bookmark
      </a>
    );
  }

  async function toggle() {
    setBusy(true);
    setError(null);
    const r = await fetch(`/api/proxy/problems/${slug}/bookmark`, {
      method: on ? "DELETE" : "PUT",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "x-csrf-token": csrfToken ?? "" },
    });
    setBusy(false);
    if (!r.ok) return setError("Could not update your bookmark. Please retry.");
    setOn(!on);
    router.refresh();
  }

  return (
    <span className="inline-flex items-center gap-2">
      <button
        className={btn}
        disabled={busy}
        onClick={toggle}
        aria-pressed={on}
        data-testid="bookmark"
      >
        {on ? "★ Bookmarked" : "☆ Bookmark"}
      </button>
      {error && (
        <span role="alert" className="text-sm text-red-600 dark:text-red-400">
          {error}
        </span>
      )}
    </span>
  );
}
