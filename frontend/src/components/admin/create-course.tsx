"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input } from "./ui";

type TopicView = components["schemas"]["TopicView"];
type CourseView = components["schemas"]["CourseView"];

const slugify = (s: string) =>
  s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);

export function CreateCourse({ csrfToken, topics }: { csrfToken: string; topics: TopicView[] }) {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [slug, setSlug] = useState("");
  const [slugTouched, setSlugTouched] = useState(false);
  const [description, setDescription] = useState("");
  const [topicId, setTopicId] = useState("");
  const [difficulty, setDifficulty] = useState("beginner");
  const [accessTier, setAccessTier] = useState("free");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const r = await adminFetch(csrfToken, "admin/courses", "POST", {
      slug,
      title,
      description,
      topicId: topicId || undefined,
      difficulty,
      accessTier,
    });
    setBusy(false);
    if (!r.ok) return setError(r.message);
    router.push(`/admin/courses/${(r.data as CourseView).id}`);
  }

  return (
    <section aria-label="New course" className="flex flex-col gap-3 rounded border p-4">
      <h2 className="text-lg font-medium">New course</h2>
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          Title
          <input
            className={input}
            name="course-title"
            value={title}
            maxLength={200}
            required
            onChange={(e) => {
              setTitle(e.target.value);
              if (!slugTouched) setSlug(slugify(e.target.value));
            }}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Slug (the public URL; cannot be changed later)
          <input
            className={input}
            name="course-slug"
            value={slug}
            maxLength={80}
            required
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            onChange={(e) => {
              setSlugTouched(true);
              setSlug(e.target.value);
            }}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm sm:col-span-2">
          Description
          <textarea
            className={input}
            name="course-description"
            rows={2}
            maxLength={2000}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Topic
          <select className={input} value={topicId} onChange={(e) => setTopicId(e.target.value)}>
            <option value="">(none)</option>
            {topics.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <div className="flex gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Difficulty
            <select
              className={input}
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value)}
            >
              <option>beginner</option>
              <option>intermediate</option>
              <option>advanced</option>
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
        </div>
        <div className="sm:col-span-2">
          <button className={btn} disabled={busy} type="submit" data-testid="course-create">
            Create course
          </button>
        </div>
      </form>
      {error && <Notice kind="error">{error}</Notice>}
    </section>
  );
}
