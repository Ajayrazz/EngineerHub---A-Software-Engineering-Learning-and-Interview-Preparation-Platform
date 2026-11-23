"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { btn } from "@/components/admin/ui";

type Props = {
  csrfToken: string | null;
  lessonId: string;
  completed: boolean;
  courseSlug: string;
  lessonSlug: string;
};

async function put(csrf: string, lessonId: string, body: unknown) {
  return fetch(`/api/proxy/lessons/${lessonId}/progress`, {
    method: "PUT",
    credentials: "same-origin",
    cache: "no-store",
    headers: { "content-type": "application/json", "x-csrf-token": csrf },
    body: JSON.stringify(body),
  });
}

/** Marks the lesson as started on open (signed-in readers) and offers "mark complete". */
export function LessonActions({ csrfToken, lessonId, completed, courseSlug, lessonSlug }: Props) {
  const router = useRouter();
  const [done, setDone] = useState(completed);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (!csrfToken || completed || started.current) return;
    started.current = true;
    // best effort: opening a lesson records the course as "in progress" and sets the resume point
    void put(csrfToken, lessonId, { status: "in_progress", scrollPct: 0 }).then(() =>
      router.refresh(),
    );
  }, [csrfToken, completed, lessonId, router]);

  if (!csrfToken) {
    return (
      <p className="text-sm">
        <a
          className="underline"
          href={`/auth/login?returnTo=${encodeURIComponent(`/courses/${courseSlug}/lessons/${lessonSlug}`)}`}
        >
          Log in
        </a>{" "}
        to track your progress.
      </p>
    );
  }

  async function toggle() {
    if (!csrfToken) return;
    setBusy(true);
    setError(null);
    const r = await put(csrfToken, lessonId, {
      status: done ? "in_progress" : "completed",
      scrollPct: done ? 0 : 100,
    });
    setBusy(false);
    if (!r.ok) return setError("Could not save your progress. Please retry.");
    setDone(!done);
    router.refresh();
  }

  return (
    <div className="flex items-center gap-3">
      <button
        className={btn}
        disabled={busy}
        onClick={toggle}
        data-testid="lesson-complete"
        aria-pressed={done}
      >
        {done ? "✓ Completed (undo)" : "Mark as complete"}
      </button>
      {error && (
        <span role="alert" className="text-sm text-red-600 dark:text-red-400">
          {error}
        </span>
      )}
    </div>
  );
}
