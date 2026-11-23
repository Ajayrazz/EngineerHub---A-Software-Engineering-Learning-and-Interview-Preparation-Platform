"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { btn } from "@/components/admin/ui";

type Props = {
  csrfToken: string | null;
  courseId: string;
  courseSlug: string;
  enrolled: boolean;
  resumeSlug: string | null;
  firstSlug: string | null;
};

export function EnrollButton({
  csrfToken,
  courseId,
  courseSlug,
  enrolled,
  resumeSlug,
  firstSlug,
}: Props) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!firstSlug) return <p className="text-sm opacity-70">No lessons are published yet.</p>;
  if (!csrfToken) {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <Link2 href={`/courses/${courseSlug}/lessons/${firstSlug}`}>Start reading</Link2>
        <a
          className="text-sm underline"
          href={`/auth/login?returnTo=${encodeURIComponent(`/courses/${courseSlug}`)}`}
        >
          Log in to track progress
        </a>
      </div>
    );
  }

  async function go() {
    if (!csrfToken) return;
    setBusy(true);
    setError(null);
    let target = resumeSlug ?? firstSlug;
    if (!enrolled) {
      const r = await fetch(`/api/proxy/courses/${courseId}/enroll`, {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: { "x-csrf-token": csrfToken },
      });
      if (!r.ok) {
        setBusy(false);
        return setError("Could not enroll. Please retry.");
      }
      const p = (await r.json()) as { resume?: { lessonSlug?: string } };
      target = p.resume?.lessonSlug ?? firstSlug;
    }
    router.push(`/courses/${courseSlug}/lessons/${target}`);
  }

  return (
    <div className="flex items-center gap-3">
      <button className={btn} disabled={busy} onClick={go} data-testid="enroll">
        {enrolled ? "Continue" : "Enroll and start"}
      </button>
      {error && (
        <span role="alert" className="text-sm text-red-600 dark:text-red-400">
          {error}
        </span>
      )}
    </div>
  );
}

function Link2({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <a href={href} className={btn} data-testid="start-reading">
      {children}
    </a>
  );
}
