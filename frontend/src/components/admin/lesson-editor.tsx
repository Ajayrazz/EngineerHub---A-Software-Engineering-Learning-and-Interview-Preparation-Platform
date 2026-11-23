"use client";

import { useState, type FormEvent } from "react";
import type { components } from "@/lib/api/schema";
import {
  CALLOUT_KINDS,
  LANGUAGES,
  LIMITS,
  fromApi,
  move,
  newBlock,
  remove,
  sameBlocks,
  toApi,
  validateBlocks,
  type Block,
  type BlockType,
} from "@/lib/cms/blocks";
import { Markdown } from "@/components/markdown";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input, when } from "./ui";

type LessonView = components["schemas"]["LessonView"];
type Flash = { kind: "ok" | "error"; text: string } | null;

export function LessonEditor({
  csrfToken,
  canPublish,
  courseId,
  courseSlug,
  sections,
  initial,
}: {
  csrfToken: string;
  canPublish: boolean;
  courseId: string;
  courseSlug: string;
  sections: { id: string; title: string }[];
  initial: LessonView;
}) {
  // One source of truth for the version: every save returns the new lesson, so meta, blocks and
  // source saves never race against a stale expectedVersion of their own making.
  const [lesson, setLesson] = useState(initial);
  const [saved, setSaved] = useState<Block[]>(() => fromApi(initial.blocks));
  const [blocks, setBlocks] = useState<Block[]>(saved);
  const [flash, setFlash] = useState<Flash>(null);
  const [busy, setBusy] = useState(false);

  const [title, setTitle] = useState(initial.title ?? "");
  const [accessTier, setAccessTier] = useState(initial.accessTier ?? "free");
  const [freePreview, setFreePreview] = useState(initial.freePreview ?? false);
  const [sectionId, setSectionId] = useState(initial.sectionId ?? "");

  const base = `admin/courses/${courseId}/lessons/${lesson.id}`;
  const dirty = !sameBlocks(blocks, saved);
  const problems = validateBlocks(blocks);

  async function call(
    path: string,
    method: "PUT" | "POST" | "PATCH",
    body: unknown,
    ok: string,
  ): Promise<LessonView | null> {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, method, body);
    setBusy(false);
    if (!r.ok) {
      setFlash({ kind: "error", text: r.message });
      return null;
    }
    const next = r.data as LessonView;
    setLesson(next);
    setFlash({ kind: "ok", text: ok });
    return next;
  }

  async function saveMeta(e: FormEvent) {
    e.preventDefault();
    await call(
      base,
      "PATCH",
      { title, accessTier, freePreview, sectionId, expectedVersion: lesson.version },
      "Lesson settings saved.",
    );
  }

  async function saveBlocks() {
    const next = await call(
      `${base}/blocks`,
      "PUT",
      { blocks: toApi(blocks), expectedVersion: lesson.version },
      "Content saved (draft).",
    );
    if (next) setSaved(blocks);
  }

  return (
    <div className="flex flex-col gap-8">
      <section aria-label="Lesson status" className="flex flex-wrap items-center gap-3 text-sm">
        <span>
          Status: <strong data-testid="lesson-status">{lesson.status}</strong>
          {lesson.hasUnpublishedChanges && (
            <span data-testid="lesson-unpublished"> · unpublished changes</span>
          )}{" "}
          · version {lesson.version} · public URL{" "}
          <code>
            /courses/{courseSlug}/lessons/{lesson.slug}
          </code>
        </span>
      </section>

      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}

      <section aria-label="Lesson settings" className="flex flex-col gap-3">
        <h2 className="text-lg font-medium">Settings</h2>
        <form onSubmit={saveMeta} className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Title
            <input
              className={`${input} w-72`}
              name="lesson-title"
              value={title}
              maxLength={200}
              required
              onChange={(e) => setTitle(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Section
            <select
              className={input}
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
            >
              {sections.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.title}
                </option>
              ))}
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
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={freePreview}
              onChange={(e) => setFreePreview(e.target.checked)}
            />
            Free preview (readable without premium)
          </label>
          <button className={btn} disabled={busy} type="submit" data-testid="settings-save">
            Save settings
          </button>
        </form>
      </section>

      <section aria-label="Content" className="flex flex-col gap-4">
        <h2 className="text-lg font-medium">Content</h2>
        {blocks.length === 0 && <p className="text-sm">No content yet. Add a block below.</p>}
        {blocks.map((b, i) => (
          <BlockEditor
            key={b.key}
            block={b}
            index={i}
            count={blocks.length}
            onChange={(next) => setBlocks((all) => all.map((x) => (x.key === b.key ? next : x)))}
            onMove={(d) => setBlocks((all) => move(all, i, d))}
            onRemove={() => setBlocks((all) => remove(all, i))}
          />
        ))}
        <div className="flex flex-wrap items-center gap-2">
          {(["markdown", "code", "callout"] as BlockType[]).map((t) => (
            <button
              key={t}
              className={btn}
              data-testid={`add-${t}`}
              onClick={() => setBlocks((all) => [...all, newBlock(t)])}
            >
              + {t}
            </button>
          ))}
        </div>
        {problems.length > 0 && (
          <ul
            className="list-disc pl-6 text-sm text-red-600 dark:text-red-400"
            data-testid="block-problems"
          >
            {problems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
        <div className="flex items-center gap-3">
          <button
            className={btn}
            disabled={busy || !dirty || problems.length > 0}
            onClick={saveBlocks}
            data-testid="blocks-save"
          >
            Save draft
          </button>
          {dirty && <span className="text-sm opacity-70">Unsaved changes</span>}
        </div>
      </section>

      <section aria-label="Preview" className="flex flex-col gap-3">
        <h2 className="text-lg font-medium">Preview of the saved draft</h2>
        <div className="rounded border p-4" data-testid="lesson-preview">
          <h3 className="mb-3 text-xl font-semibold">{lesson.title}</h3>
          {saved.length === 0 ? (
            <p className="text-sm opacity-70">Nothing saved yet.</p>
          ) : (
            <BlockPreview blocks={saved} />
          )}
        </div>
      </section>

      <SourceAndPublish
        base={base}
        lesson={lesson}
        canPublish={canPublish}
        busy={busy}
        dirty={dirty}
        call={call}
      />
    </div>
  );
}

function BlockPreview({ blocks }: { blocks: Block[] }) {
  return (
    <div className="flex flex-col gap-3">
      {blocks.map((b) =>
        b.type === "markdown" ? (
          <Markdown key={b.key} text={b.data.text ?? ""} />
        ) : b.type === "code" ? (
          <figure key={b.key}>
            <pre className="overflow-x-auto rounded bg-black/10 p-3 text-xs dark:bg-white/15">
              <code>{b.data.code}</code>
            </pre>
            {b.data.caption && (
              <figcaption className="text-xs opacity-70">{b.data.caption}</figcaption>
            )}
          </figure>
        ) : (
          <aside key={b.key} className="rounded border-l-4 p-3 text-sm">
            <strong className="capitalize">{b.data.kind}: </strong>
            {b.data.text}
          </aside>
        ),
      )}
    </div>
  );
}

function BlockEditor({
  block,
  index,
  count,
  onChange,
  onMove,
  onRemove,
}: {
  block: Block;
  index: number;
  count: number;
  onChange: (b: Block) => void;
  onMove: (d: -1 | 1) => void;
  onRemove: () => void;
}) {
  const set = (k: string, v: string) => onChange({ ...block, data: { ...block.data, [k]: v } });
  const body = block.type === "code" ? block.data.code : block.data.text;
  const max = LIMITS[block.type];
  return (
    <div className="flex flex-col gap-2 rounded border p-3" data-testid="block-editor">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <strong>
          {index + 1}. {block.type}
        </strong>
        <button
          className={btn}
          disabled={index === 0}
          onClick={() => onMove(-1)}
          aria-label={`Move block ${index + 1} up`}
        >
          ↑
        </button>
        <button
          className={btn}
          disabled={index === count - 1}
          onClick={() => onMove(1)}
          aria-label={`Move block ${index + 1} down`}
        >
          ↓
        </button>
        <button className={btn} onClick={onRemove} aria-label={`Remove block ${index + 1}`}>
          Remove
        </button>
        <span className="ml-auto opacity-60">
          {(body ?? "").length}/{max}
        </span>
      </div>

      {block.type === "markdown" && (
        <div className="grid gap-3 lg:grid-cols-2">
          <textarea
            aria-label={`Block ${index + 1} markdown`}
            className={`${input} min-h-40 font-mono`}
            value={block.data.text ?? ""}
            onChange={(e) => set("text", e.target.value)}
          />
          <div
            className="rounded border p-3"
            aria-label={`Block ${index + 1} live preview`}
            data-testid="block-live-preview"
          >
            <Markdown text={block.data.text ?? ""} />
          </div>
        </div>
      )}

      {block.type === "code" && (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap gap-2">
            <label className="flex flex-col gap-1 text-sm">
              Language
              <select
                className={input}
                value={block.data.language ?? "java"}
                onChange={(e) => set("language", e.target.value)}
              >
                {LANGUAGES.map((l) => (
                  <option key={l}>{l}</option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Caption (optional)
              <input
                className={`${input} w-72`}
                value={block.data.caption ?? ""}
                maxLength={LIMITS.caption}
                onChange={(e) => set("caption", e.target.value)}
              />
            </label>
          </div>
          <textarea
            aria-label={`Block ${index + 1} code`}
            className={`${input} min-h-32 font-mono`}
            value={block.data.code ?? ""}
            onChange={(e) => set("code", e.target.value)}
          />
        </div>
      )}

      {block.type === "callout" && (
        <div className="flex flex-col gap-2">
          <label className="flex flex-col gap-1 text-sm">
            Kind
            <select
              className={`${input} w-40`}
              value={block.data.kind ?? "info"}
              onChange={(e) => set("kind", e.target.value)}
            >
              {CALLOUT_KINDS.map((k) => (
                <option key={k}>{k}</option>
              ))}
            </select>
          </label>
          <textarea
            aria-label={`Block ${index + 1} callout text`}
            className={`${input} min-h-20`}
            value={block.data.text ?? ""}
            onChange={(e) => set("text", e.target.value)}
          />
        </div>
      )}
    </div>
  );
}

function SourceAndPublish({
  base,
  lesson,
  canPublish,
  busy,
  dirty,
  call,
}: {
  base: string;
  lesson: LessonView;
  canPublish: boolean;
  busy: boolean;
  dirty: boolean;
  call: (
    p: string,
    m: "PUT" | "POST" | "PATCH",
    b: unknown,
    ok: string,
  ) => Promise<LessonView | null>;
}) {
  const [kind, setKind] = useState(lesson.source?.kind ?? "original");
  const [license, setLicense] = useState(lesson.source?.license ?? "");
  const [attribution, setAttribution] = useState(lesson.source?.attribution ?? "");
  const [sourceUrl, setSourceUrl] = useState(lesson.source?.sourceUrl ?? "");
  const [confirmed, setConfirmed] = useState(false);
  const needsLicense = kind === "licensed" || kind === "open_license";

  return (
    <section aria-label="Source and publishing" className="flex flex-col gap-4">
      <h2 className="text-lg font-medium">Source and publishing</h2>
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={async (e) => {
          e.preventDefault();
          await call(
            `${base}/source`,
            "PUT",
            { kind, license, attribution, sourceUrl },
            "Content source saved.",
          );
        }}
      >
        <label className="flex flex-col gap-1 text-sm">
          Where does this content come from?
          <select
            className={input}
            value={kind}
            onChange={(e) => setKind(e.target.value)}
            name="source-kind"
          >
            <option value="original">Original (written by us)</option>
            <option value="ai_assisted">AI-assisted draft, human reviewed</option>
            <option value="open_license">Openly licensed</option>
            <option value="licensed">Licensed</option>
          </select>
        </label>
        {needsLicense && (
          <>
            <label className="flex flex-col gap-1 text-sm">
              License
              <input
                className={input}
                value={license}
                maxLength={60}
                onChange={(e) => setLicense(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Attribution
              <input
                className={`${input} w-72`}
                value={attribution}
                maxLength={300}
                onChange={(e) => setAttribution(e.target.value)}
              />
            </label>
          </>
        )}
        <label className="flex flex-col gap-1 text-sm">
          Source URL (optional, https)
          <input
            className={`${input} w-72`}
            value={sourceUrl}
            maxLength={500}
            onChange={(e) => setSourceUrl(e.target.value)}
          />
        </label>
        <button className={btn} disabled={busy} type="submit" data-testid="source-save">
          Save source
        </button>
        <span className="text-sm opacity-70" data-testid="source-state">
          {lesson.source
            ? lesson.source.reviewedAt
              ? `Signed off ${when(lesson.source.reviewedAt)}`
              : "Recorded, not yet signed off"
            : "No source recorded yet"}
        </span>
      </form>

      {canPublish ? (
        <div className="flex flex-col gap-2">
          {dirty && (
            <Notice kind="error">
              Save your draft before publishing: only saved content is published.
            </Notice>
          )}
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              data-testid="originality"
            />
            I confirm this content is original or properly licensed and I have reviewed it.
          </label>
          <div className="flex gap-2">
            <button
              className={btn}
              disabled={busy || dirty || !confirmed}
              onClick={async () => {
                if (
                  await call(
                    `${base}/publish`,
                    "POST",
                    { originalityConfirmed: confirmed },
                    "Lesson published.",
                  )
                ) {
                  setConfirmed(false);
                }
              }}
              data-testid="lesson-publish"
            >
              {lesson.status === "published" ? "Publish changes" : "Publish lesson"}
            </button>
            {lesson.status === "published" && (
              <button
                className={btn}
                disabled={busy}
                onClick={() => call(`${base}/unpublish`, "POST", undefined, "Lesson unpublished.")}
                data-testid="lesson-unpublish"
              >
                Unpublish
              </button>
            )}
          </div>
        </div>
      ) : (
        <p className="text-sm opacity-70">Publishing is done by a content manager or admin.</p>
      )}
    </section>
  );
}
