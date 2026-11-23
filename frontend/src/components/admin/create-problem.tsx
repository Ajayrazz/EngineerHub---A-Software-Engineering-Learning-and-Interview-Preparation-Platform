"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type ProblemView = components["schemas"]["ProblemView"];

const slugify = (s: string) =>
  s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);

export function CreateProblem({ csrfToken }: { csrfToken: string }) {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [slug, setSlug] = useState("");
  const [touched, setTouched] = useState(false);
  const [difficulty, setDifficulty] = useState("easy");
  const [accessTier, setAccessTier] = useState("free");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const r = await adminFetch(csrfToken, "admin/problems", "POST", {
      slug,
      title,
      difficulty,
      accessTier,
    });
    setBusy(false);
    if (!r.ok) return setError(r.message);
    router.push(`/admin/problems/${(r.data as ProblemView).id}`);
  }

  return (
    <section aria-label="New problem" className="flex flex-col gap-3 rounded border p-4">
      <h2 className="text-lg font-medium">New problem</h2>
      <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Title
          <input
            className={`${input} w-72`}
            name="problem-title"
            value={title}
            maxLength={200}
            required
            onChange={(e) => {
              setTitle(e.target.value);
              if (!touched) setSlug(slugify(e.target.value));
            }}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Slug (public URL; cannot be changed later)
          <input
            className={`${input} w-64`}
            name="problem-slug"
            value={slug}
            maxLength={80}
            required
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            onChange={(e) => {
              setTouched(true);
              setSlug(e.target.value);
            }}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Level
          <select
            className={input}
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value)}
          >
            <option>easy</option>
            <option>medium</option>
            <option>hard</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Access
          <select
            className={input}
            value={accessTier}
            onChange={(e) => setAccessTier(e.target.value)}
          >
            <option>free</option>
            <option>premium</option>
          </select>
        </label>
        <button className={btn} disabled={busy} type="submit" data-testid="problem-create">
          Create problem
        </button>
      </form>
      {error && <Notice kind="error">{error}</Notice>}
    </section>
  );
}
