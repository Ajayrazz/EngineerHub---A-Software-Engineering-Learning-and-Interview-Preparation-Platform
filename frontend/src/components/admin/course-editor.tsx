"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import { move } from "@/lib/cms/blocks";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input, when } from "./ui";

type CourseView = components["schemas"]["CourseView"];
type SectionView = components["schemas"]["SectionView"];
type TopicView = components["schemas"]["TopicView"];
type LessonView = components["schemas"]["LessonView"];

type Flash = { kind: "ok" | "error"; text: string } | null;

export function CourseEditor({
  csrfToken,
  canPublish,
  topics,
  initial,
}: {
  csrfToken: string;
  canPublish: boolean;
  topics: TopicView[];
  initial: CourseView;
}) {
  const router = useRouter();
  const [course, setCourse] = useState(initial);
  const [flash, setFlash] = useState<Flash>(null);
  const [busy, setBusy] = useState(false);

  // meta form
  const [title, setTitle] = useState(initial.title ?? "");
  const [description, setDescription] = useState(initial.description ?? "");
  const [topicId, setTopicId] = useState(initial.topicId ?? "");
  const [difficulty, setDifficulty] = useState(initial.difficulty ?? "beginner");
  const [accessTier, setAccessTier] = useState(initial.accessTier ?? "free");
  const [newSection, setNewSection] = useState("");

  const base = `admin/courses/${course.id}`;

  /** Runs a call that answers with the updated course. */
  async function act(
    path: string,
    method: "PUT" | "POST" | "PATCH" | "DELETE",
    body: unknown,
    ok: string,
  ) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, method, body);
    setBusy(false);
    if (!r.ok) {
      setFlash({ kind: "error", text: r.message });
      return false;
    }
    const data = r.data as CourseView | undefined;
    if (data?.sections) setCourse(data);
    setFlash({ kind: "ok", text: ok });
    return true;
  }

  async function saveMeta(e: FormEvent) {
    e.preventDefault();
    await act(
      base,
      "PATCH",
      {
        title,
        description,
        topicId: topicId || undefined,
        difficulty,
        accessTier,
        expectedVersion: course.version,
      },
      "Course details saved.",
    );
  }

  const sections = course.sections ?? [];
  const reorderSections = (index: number, delta: -1 | 1) =>
    act(
      `${base}/sections/order`,
      "PUT",
      { ids: move(sections, index, delta).map((s) => s.id) },
      "Sections reordered.",
    );

  return (
    <div className="flex flex-col gap-8">
      <section aria-label="Course status" className="flex flex-wrap items-center gap-3">
        <span className="text-sm">
          Status: <strong data-testid="course-status">{course.status}</strong> · public URL{" "}
          <code>/courses/{course.slug}</code> · updated {when(course.updatedAt)}
        </span>
        {canPublish && course.status !== "published" && (
          <button
            className={btn}
            disabled={busy}
            onClick={() => act(`${base}/publish`, "POST", undefined, "Course published.")}
            data-testid="course-publish"
          >
            Publish course
          </button>
        )}
        {canPublish && course.status === "published" && (
          <button
            className={btn}
            disabled={busy}
            onClick={() => act(`${base}/unpublish`, "POST", undefined, "Course unpublished.")}
            data-testid="course-unpublish"
          >
            Unpublish
          </button>
        )}
        {canPublish && course.status !== "archived" && (
          <button
            className={btn}
            disabled={busy}
            onClick={() => act(`${base}/archive`, "POST", undefined, "Course archived.")}
          >
            Archive
          </button>
        )}
      </section>

      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}

      <section aria-label="Course details" className="flex flex-col gap-3">
        <h2 className="text-lg font-medium">Details</h2>
        <form onSubmit={saveMeta} className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm">
            Title
            <input
              className={input}
              name="meta-title"
              value={title}
              maxLength={200}
              required
              onChange={(e) => setTitle(e.target.value)}
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
          <label className="flex flex-col gap-1 text-sm sm:col-span-2">
            Description (needed before the course can be published)
            <textarea
              className={input}
              name="meta-description"
              rows={3}
              maxLength={2000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
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
            Default access for new lessons
            <select
              className={input}
              value={accessTier}
              onChange={(e) => setAccessTier(e.target.value)}
            >
              <option>free</option>
              <option>premium</option>
            </select>
          </label>
          <div className="sm:col-span-2">
            <button className={btn} disabled={busy} type="submit" data-testid="meta-save">
              Save details
            </button>
          </div>
        </form>
      </section>

      <section aria-label="Outline" className="flex flex-col gap-4">
        <h2 className="text-lg font-medium">Outline</h2>
        {sections.length === 0 && (
          <p className="text-sm">No sections yet. Add the first one below.</p>
        )}
        {sections.map((s, i) => (
          <SectionBox
            key={s.id}
            section={s}
            index={i}
            count={sections.length}
            courseId={course.id ?? ""}
            base={base}
            csrfToken={csrfToken}
            busy={busy}
            act={act}
            onMove={reorderSections}
            onLessonCreated={(lesson) =>
              router.push(`/admin/courses/${course.id}/lessons/${lesson.id}`)
            }
            setFlash={setFlash}
          />
        ))}
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await act(`${base}/sections`, "POST", { title: newSection }, "Section added.")) {
              setNewSection("");
            }
          }}
        >
          <label className="flex flex-col gap-1 text-sm">
            New section
            <input
              className={`${input} w-72`}
              name="new-section"
              value={newSection}
              maxLength={200}
              required
              onChange={(e) => setNewSection(e.target.value)}
            />
          </label>
          <button className={btn} disabled={busy} type="submit" data-testid="section-add">
            Add section
          </button>
        </form>
      </section>
    </div>
  );
}

function SectionBox({
  section,
  index,
  count,
  courseId,
  base,
  csrfToken,
  busy,
  act,
  onMove,
  onLessonCreated,
  setFlash,
}: {
  section: SectionView;
  index: number;
  count: number;
  courseId: string;
  base: string;
  csrfToken: string;
  busy: boolean;
  act: (
    p: string,
    m: "PUT" | "POST" | "PATCH" | "DELETE",
    b: unknown,
    ok: string,
  ) => Promise<boolean>;
  onMove: (index: number, delta: -1 | 1) => Promise<boolean>;
  onLessonCreated: (l: LessonView) => void;
  setFlash: (f: Flash) => void;
}) {
  const [title, setTitle] = useState(section.title ?? "");
  const [slug, setSlug] = useState("");
  const [lessonTitle, setLessonTitle] = useState("");
  const lessons = section.lessons ?? [];

  async function addLesson(e: FormEvent) {
    e.preventDefault();
    const r = await adminFetch(csrfToken, `${base}/lessons`, "POST", {
      sectionId: section.id,
      slug,
      title: lessonTitle,
    });
    if (!r.ok) return setFlash({ kind: "error", text: r.message });
    onLessonCreated(r.data as LessonView);
  }

  return (
    <div className="flex flex-col gap-3 rounded border p-3" data-testid="section-box">
      <div className="flex flex-wrap items-center gap-2">
        <input
          aria-label="Section title"
          className={`${input} w-72`}
          value={title}
          maxLength={200}
          onChange={(e) => setTitle(e.target.value)}
        />
        <button
          className={btn}
          disabled={busy || title.trim() === "" || title === section.title}
          onClick={() =>
            act(`${base}/sections/${section.id}`, "PATCH", { title }, "Section renamed.")
          }
        >
          Rename
        </button>
        <button
          className={btn}
          disabled={busy || index === 0}
          onClick={() => onMove(index, -1)}
          aria-label={`Move section ${section.title} up`}
        >
          ↑
        </button>
        <button
          className={btn}
          disabled={busy || index === count - 1}
          onClick={() => onMove(index, 1)}
          aria-label={`Move section ${section.title} down`}
        >
          ↓
        </button>
        <button
          className={btn}
          disabled={busy || lessons.length > 0}
          title={lessons.length > 0 ? "Move or delete its lessons first" : undefined}
          onClick={() =>
            act(`${base}/sections/${section.id}`, "DELETE", undefined, "Section deleted.")
          }
        >
          Delete
        </button>
      </div>

      <ul className="flex flex-col gap-1" data-testid="lesson-list">
        {lessons.map((l, i) => (
          <li
            key={l.id}
            className="flex flex-wrap items-center gap-2 text-sm"
            data-testid="lesson-item"
          >
            <Link className="underline" href={`/admin/courses/${courseId}/lessons/${l.id}`}>
              {l.title}
            </Link>
            <span className="opacity-70">
              ({l.slug}) · {l.status}
              {l.hasUnpublishedChanges ? " · unpublished changes" : ""} · {l.accessTier}
              {l.freePreview ? " · preview" : ""}
            </span>
            <button
              className={btn}
              disabled={busy || i === 0}
              aria-label={`Move lesson ${l.title} up`}
              onClick={() =>
                act(
                  `${base}/sections/${section.id}/lessons/order`,
                  "PUT",
                  { ids: move(lessons, i, -1).map((x) => x.id) },
                  "Lessons reordered.",
                )
              }
            >
              ↑
            </button>
            <button
              className={btn}
              disabled={busy || i === lessons.length - 1}
              aria-label={`Move lesson ${l.title} down`}
              onClick={() =>
                act(
                  `${base}/sections/${section.id}/lessons/order`,
                  "PUT",
                  { ids: move(lessons, i, 1).map((x) => x.id) },
                  "Lessons reordered.",
                )
              }
            >
              ↓
            </button>
          </li>
        ))}
      </ul>

      <form onSubmit={addLesson} className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1 text-sm">
          New lesson title
          <input
            className={input}
            name="lesson-title"
            value={lessonTitle}
            maxLength={200}
            required
            onChange={(e) => {
              setLessonTitle(e.target.value);
              setSlug(
                e.target.value
                  .toLowerCase()
                  .replace(/[^a-z0-9]+/g, "-")
                  .replace(/^-+|-+$/g, "")
                  .slice(0, 80),
              );
            }}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Slug
          <input
            className={input}
            name="lesson-slug"
            value={slug}
            maxLength={80}
            required
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            onChange={(e) => setSlug(e.target.value)}
          />
        </label>
        <button className={btn} disabled={busy} type="submit" data-testid="lesson-add">
          Add lesson
        </button>
      </form>
    </div>
  );
}
