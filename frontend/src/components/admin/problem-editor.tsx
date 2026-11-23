"use client";

import { useState, type FormEvent } from "react";
import { Markdown } from "@/components/markdown";
import type { components } from "@/lib/api/schema";
import {
  counts,
  fromApi,
  move,
  newRow,
  sameTests,
  toApi,
  validateTests,
  type TestRow,
} from "@/lib/problems/tests";
import { adminFetch } from "./admin-fetch";
import { ValidationPanel } from "./validation-panel";
import { Notice, btn, input, when } from "./ui";

type ProblemView = components["schemas"]["ProblemView"];
type TestsView = components["schemas"]["TestsView"];
type ValidationView = components["schemas"]["ValidationView"];
type TagView = components["schemas"]["TagView"];
type TopicView = components["schemas"]["TopicView"];
type Flash = { kind: "ok" | "error"; text: string } | null;

export function ProblemEditor({
  csrfToken,
  canPublish,
  canCreatePatterns,
  topics,
  patterns: initialPatterns,
  initial,
  initialTests,
  initialValidation,
}: {
  csrfToken: string;
  canPublish: boolean;
  canCreatePatterns: boolean;
  topics: TopicView[];
  patterns: TagView[];
  initial: ProblemView;
  initialTests: TestsView;
  initialValidation: ValidationView;
}) {
  const [problem, setProblem] = useState(initial);
  const [testsMeta, setTestsMeta] = useState({
    version: initialTests.testcaseVersion ?? 0,
    validation: initialTests.validationStatus ?? "unverified",
  });
  const [savedRows, setSavedRows] = useState<TestRow[]>(() => fromApi(initialTests.tests));
  const [rows, setRows] = useState<TestRow[]>(savedRows);
  const [patterns, setPatterns] = useState(initialPatterns);
  const [flash, setFlash] = useState<Flash>(null);
  const [busy, setBusy] = useState(false);
  const base = `admin/problems/${problem.id}`;

  async function call(
    path: string,
    method: "PUT" | "POST" | "PATCH",
    body: unknown,
    ok: string,
  ): Promise<unknown | null> {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, method, body);
    setBusy(false);
    if (!r.ok) {
      setFlash({ kind: "error", text: r.message });
      return null;
    }
    setFlash({ kind: "ok", text: ok });
    return r.data ?? {};
  }

  /** Calls that answer with the whole problem. */
  async function callProblem(
    path: string,
    method: "PUT" | "POST" | "PATCH",
    body: unknown,
    ok: string,
  ) {
    const data = (await call(path, method, body, ok)) as ProblemView | null;
    if (data) setProblem(data);
    return data;
  }

  return (
    <div className="flex flex-col gap-8">
      <section aria-label="Problem status" className="flex flex-wrap items-center gap-3 text-sm">
        <span>
          Status: <strong data-testid="problem-status">{problem.status}</strong> · tests:{" "}
          <strong data-testid="validation-status">
            {testsMeta.validation === "verified" ? "verified" : "not verified"}
          </strong>{" "}
          · version {problem.version} · test set v{testsMeta.version} · public URL{" "}
          <code>/problems/{problem.slug}</code>
          {problem.hasUnpublishedChanges && (
            <span data-testid="problem-unpublished"> · unpublished changes</span>
          )}{" "}
          · updated {when(problem.updatedAt)}
        </span>
      </section>
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}

      <Details
        problem={problem}
        busy={busy}
        save={(body) =>
          callProblem(
            base,
            "PATCH",
            { ...body, expectedVersion: problem.version },
            "Details saved.",
          )
        }
      />

      <Tags
        problem={problem}
        topics={topics}
        patterns={patterns}
        canCreatePatterns={canCreatePatterns}
        busy={busy}
        save={(topicIds, patternIds) =>
          callProblem(`${base}/tags`, "PUT", { topicIds, patternIds }, "Tags saved.")
        }
        createPattern={async (slug, name) => {
          const p = (await call(
            "admin/patterns",
            "POST",
            { slug, name },
            "Pattern created.",
          )) as TagView | null;
          if (p)
            setPatterns((all) =>
              [...all, p].sort((a, b) => (a.name ?? "").localeCompare(b.name ?? "")),
            );
          return p !== null;
        }}
      />

      <Tests
        rows={rows}
        setRows={setRows}
        saved={savedRows}
        busy={busy}
        meta={testsMeta}
        save={async () => {
          const data = (await call(
            `${base}/tests`,
            "PUT",
            { tests: toApi(rows), expectedTestcaseVersion: testsMeta.version },
            "Test set saved as a new version. It must be verified again before publishing.",
          )) as TestsView | null;
          if (!data) return;
          const fresh = fromApi(data.tests);
          setSavedRows(fresh);
          setRows(fresh);
          setTestsMeta({
            version: data.testcaseVersion ?? 0,
            validation: data.validationStatus ?? "unverified",
          });
          setProblem((p) => ({
            ...p,
            sampleCount: counts(fresh).sample,
            hiddenCount: counts(fresh).hidden,
            testcaseVersion: data.testcaseVersion ?? 0,
          }));
        }}
      />

      <ValidationPanel
        csrfToken={csrfToken}
        problemId={problem.id ?? ""}
        testsVersion={testsMeta.version}
        initial={initialValidation}
        onStatus={(s) => setTestsMeta((m) => ({ ...m, validation: s }))}
      />

      <Preview problem={problem} samples={savedRows.filter((r) => r.sample)} />

      <SourceAndPublish
        problem={problem}
        canPublish={canPublish}
        busy={busy}
        saveSource={(body) => callProblem(`${base}/source`, "PUT", body, "Content source saved.")}
        publish={(confirmed) =>
          callProblem(
            `${base}/publish`,
            "POST",
            { originalityConfirmed: confirmed },
            "Problem published.",
          )
        }
        unpublish={() =>
          callProblem(`${base}/unpublish`, "POST", undefined, "Problem unpublished.")
        }
        archive={() => callProblem(`${base}/archive`, "POST", undefined, "Problem archived.")}
      />
    </div>
  );
}

// ------------------------------------------------------------------ details

function Details({
  problem,
  busy,
  save,
}: {
  problem: ProblemView;
  busy: boolean;
  save: (body: Record<string, unknown>) => Promise<unknown>;
}) {
  const [title, setTitle] = useState(problem.title ?? "");
  const [difficulty, setDifficulty] = useState(problem.difficulty ?? "easy");
  const [accessTier, setAccessTier] = useState(problem.accessTier ?? "free");
  const [timeLimitMs, setTimeLimitMs] = useState(problem.timeLimitMs ?? 2000);
  const [memoryLimitMb, setMemoryLimitMb] = useState(problem.memoryLimitMb ?? 256);
  const [checkerType, setCheckerType] = useState(problem.checkerType ?? "exact");
  const [statement, setStatement] = useState(problem.statement ?? "");
  const [inputFormat, setInputFormat] = useState(problem.inputFormat ?? "");
  const [outputFormat, setOutputFormat] = useState(problem.outputFormat ?? "");
  const [constraints, setConstraints] = useState(problem.constraints ?? "");
  const [hints, setHints] = useState<string[]>(problem.hints ?? []);

  function submit(e: FormEvent) {
    e.preventDefault();
    void save({
      title,
      difficulty,
      accessTier,
      timeLimitMs,
      memoryLimitMb,
      checkerType,
      statement,
      inputFormat,
      outputFormat,
      constraints,
      hints: hints.filter((h) => h.trim() !== ""),
    });
  }

  return (
    <section aria-label="Problem details" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Details</h2>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <div className="flex flex-wrap gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Title
            <input
              className={`${input} w-80`}
              name="detail-title"
              value={title}
              maxLength={200}
              required
              onChange={(e) => setTitle(e.target.value)}
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
          <label className="flex flex-col gap-1 text-sm">
            Time limit (ms)
            <input
              className={`${input} w-28`}
              type="number"
              min={100}
              max={20000}
              value={timeLimitMs}
              onChange={(e) => setTimeLimitMs(Number(e.target.value))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Memory (MB)
            <input
              className={`${input} w-28`}
              type="number"
              min={16}
              max={2048}
              value={memoryLimitMb}
              onChange={(e) => setMemoryLimitMb(Number(e.target.value))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Output comparison
            <select
              className={input}
              value={checkerType}
              onChange={(e) => setCheckerType(e.target.value)}
              name="detail-checker"
            >
              <option value="exact">exact</option>
              <option value="token">token (ignore whitespace)</option>
              <option value="float">float (tolerance)</option>
            </select>
          </label>
        </div>
        {checkerType !== problem.checkerType && (
          <p className="text-sm opacity-80">
            Changing the comparison resets the verification of the test data.
          </p>
        )}

        <div className="grid gap-3 lg:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm">
            Statement (markdown)
            <textarea
              aria-label="Statement"
              className={`${input} min-h-56 font-mono`}
              value={statement}
              maxLength={50_000}
              onChange={(e) => setStatement(e.target.value)}
            />
            <span className="self-end opacity-60">{statement.length}/50000</span>
          </label>
          <div className="flex flex-col gap-1 text-sm">
            Live preview
            <div className="min-h-56 rounded border p-3" data-testid="statement-preview">
              <Markdown text={statement} />
            </div>
          </div>
        </div>
        <div className="grid gap-3 lg:grid-cols-3">
          <label className="flex flex-col gap-1 text-sm">
            Input format
            <textarea
              aria-label="Input format"
              className={`${input} min-h-24 font-mono`}
              value={inputFormat}
              onChange={(e) => setInputFormat(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Output format
            <textarea
              aria-label="Output format"
              className={`${input} min-h-24 font-mono`}
              value={outputFormat}
              onChange={(e) => setOutputFormat(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Constraints
            <textarea
              aria-label="Constraints"
              className={`${input} min-h-24 font-mono`}
              value={constraints}
              onChange={(e) => setConstraints(e.target.value)}
            />
          </label>
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm">Hints (up to 5, shown progressively to learners)</legend>
          {hints.map((h, i) => (
            <div key={i} className="flex gap-2">
              <input
                aria-label={`Hint ${i + 1}`}
                className={`${input} flex-1`}
                value={h}
                maxLength={2000}
                onChange={(e) =>
                  setHints((all) => all.map((x, k) => (k === i ? e.target.value : x)))
                }
              />
              <button
                type="button"
                className={btn}
                onClick={() => setHints((all) => all.filter((_, k) => k !== i))}
                aria-label={`Remove hint ${i + 1}`}
              >
                Remove
              </button>
            </div>
          ))}
          {hints.length < 5 && (
            <div>
              <button type="button" className={btn} onClick={() => setHints((all) => [...all, ""])}>
                + hint
              </button>
            </div>
          )}
        </fieldset>
        <div>
          <button className={btn} disabled={busy} type="submit" data-testid="details-save">
            Save details
          </button>
        </div>
      </form>
    </section>
  );
}

// ------------------------------------------------------------------ tags

function Tags({
  problem,
  topics,
  patterns,
  canCreatePatterns,
  busy,
  save,
  createPattern,
}: {
  problem: ProblemView;
  topics: TopicView[];
  patterns: TagView[];
  canCreatePatterns: boolean;
  busy: boolean;
  save: (topicIds: string[], patternIds: string[]) => Promise<unknown>;
  createPattern: (slug: string, name: string) => Promise<boolean>;
}) {
  const [topicIds, setTopicIds] = useState<string[]>((problem.topics ?? []).map((t) => t.id ?? ""));
  const [patternIds, setPatternIds] = useState<string[]>(
    (problem.patterns ?? []).map((t) => t.id ?? ""),
  );
  const [name, setName] = useState("");
  const toggle = (list: string[], id: string, on: boolean) =>
    on ? [...list, id] : list.filter((x) => x !== id);
  const slug = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);

  return (
    <section aria-label="Tags" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Topics and patterns</h2>
      <fieldset className="flex flex-wrap gap-x-4 gap-y-1 text-sm" aria-label="Topics">
        <legend className="mb-1">Topics (max 10)</legend>
        {topics.map((t) => (
          <label key={t.id} className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={topicIds.includes(t.id ?? "")}
              onChange={(e) => setTopicIds((l) => toggle(l, t.id ?? "", e.target.checked))}
            />
            {t.name}
          </label>
        ))}
      </fieldset>
      <fieldset className="flex flex-wrap gap-x-4 gap-y-1 text-sm" aria-label="Patterns">
        <legend className="mb-1">Patterns (max 10)</legend>
        {patterns.length === 0 && <span className="opacity-70">No patterns yet.</span>}
        {patterns.map((t) => (
          <label key={t.id} className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={patternIds.includes(t.id ?? "")}
              onChange={(e) => setPatternIds((l) => toggle(l, t.id ?? "", e.target.checked))}
            />
            {t.name}
          </label>
        ))}
      </fieldset>
      {canCreatePatterns && (
        <form
          className="flex items-end gap-2"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await createPattern(slug, name)) setName("");
          }}
        >
          <label className="flex flex-col gap-1 text-sm">
            New pattern
            <input
              className={input}
              name="new-pattern"
              value={name}
              maxLength={100}
              required
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <button
            className={btn}
            disabled={busy || slug === ""}
            type="submit"
            data-testid="pattern-add"
          >
            Add pattern
          </button>
        </form>
      )}
      <div>
        <button
          className={btn}
          disabled={busy}
          onClick={() => void save(topicIds, patternIds)}
          data-testid="tags-save"
        >
          Save tags
        </button>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ tests

function Tests({
  rows,
  setRows,
  saved,
  busy,
  meta,
  save,
}: {
  rows: TestRow[];
  setRows: (f: (r: TestRow[]) => TestRow[]) => void;
  saved: TestRow[];
  busy: boolean;
  meta: { version: number; validation: string };
  save: () => Promise<void>;
}) {
  const dirty = !sameTests(rows, saved);
  const problems = validateTests(rows);
  const c = counts(rows);
  const patch = (key: string, p: Partial<TestRow>) =>
    setRows((all) => all.map((r) => (r.key === key ? { ...r, ...p } : r)));

  return (
    <section aria-label="Test cases" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Test cases</h2>
      <p className="text-sm opacity-80" data-testid="test-counts">
        {c.sample} sample · {c.hidden} hidden · set v{meta.version}. Saving replaces the whole set
        with a new version and resets verification. Hidden tests are never shown to learners.
      </p>
      {rows.map((r, i) => (
        <div key={r.key} className="flex flex-col gap-2 rounded border p-3" data-testid="test-row">
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <strong>
              {i + 1}. {r.sample ? "sample" : "hidden"}
            </strong>
            <label className="flex items-center gap-1.5">
              <input
                type="checkbox"
                checked={r.sample}
                onChange={(e) => patch(r.key, { sample: e.target.checked })}
                aria-label={`Test ${i + 1} is a sample`}
              />
              sample (visible to learners)
            </label>
            <button
              className={btn}
              disabled={i === 0}
              onClick={() => setRows((all) => move(all, i, -1))}
              aria-label={`Move test ${i + 1} up`}
            >
              ↑
            </button>
            <button
              className={btn}
              disabled={i === rows.length - 1}
              onClick={() => setRows((all) => move(all, i, 1))}
              aria-label={`Move test ${i + 1} down`}
            >
              ↓
            </button>
            <button
              className={btn}
              onClick={() => setRows((all) => all.filter((x) => x.key !== r.key))}
              aria-label={`Remove test ${i + 1}`}
            >
              Remove
            </button>
          </div>
          <div className="grid gap-2 lg:grid-cols-2">
            <label className="flex flex-col gap-1 text-sm">
              Input
              <textarea
                aria-label={`Test ${i + 1} input`}
                className={`${input} min-h-20 font-mono`}
                value={r.input}
                onChange={(e) => patch(r.key, { input: e.target.value })}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Expected output
              <textarea
                aria-label={`Test ${i + 1} expected`}
                className={`${input} min-h-20 font-mono`}
                value={r.expected}
                onChange={(e) => patch(r.key, { expected: e.target.value })}
              />
            </label>
          </div>
          {r.sample && (
            <label className="flex flex-col gap-1 text-sm">
              Explanation (shown with the sample)
              <input
                aria-label={`Test ${i + 1} explanation`}
                className={input}
                value={r.explanation}
                maxLength={2000}
                onChange={(e) => patch(r.key, { explanation: e.target.value })}
              />
            </label>
          )}
        </div>
      ))}
      <div className="flex flex-wrap items-center gap-2">
        <button
          className={btn}
          onClick={() => setRows((all) => [...all, newRow(true)])}
          data-testid="add-sample"
        >
          + sample test
        </button>
        <button
          className={btn}
          onClick={() => setRows((all) => [...all, newRow(false)])}
          data-testid="add-hidden"
        >
          + hidden test
        </button>
      </div>
      {problems.length > 0 && (
        <ul
          className="list-disc pl-6 text-sm text-red-600 dark:text-red-400"
          data-testid="test-problems"
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
          onClick={() => void save()}
          data-testid="tests-save"
        >
          Save test set
        </button>
        {dirty && <span className="text-sm opacity-70">Unsaved changes</span>}
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ preview

function Preview({ problem, samples }: { problem: ProblemView; samples: TestRow[] }) {
  return (
    <section aria-label="Learner preview" className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">Preview of what learners will see (saved draft)</h2>
      <div className="flex flex-col gap-4 rounded border p-4" data-testid="problem-preview">
        <h3 className="text-xl font-semibold">{problem.title}</h3>
        <p className="text-xs opacity-70">
          {problem.difficulty} · {problem.timeLimitMs} ms · {problem.memoryLimitMb} MB
        </p>
        <Markdown text={problem.statement ?? ""} />
        {problem.inputFormat && (
          <div>
            <h4 className="font-medium">Input</h4>
            <Markdown text={problem.inputFormat} />
          </div>
        )}
        {problem.outputFormat && (
          <div>
            <h4 className="font-medium">Output</h4>
            <Markdown text={problem.outputFormat} />
          </div>
        )}
        {problem.constraints && (
          <div>
            <h4 className="font-medium">Constraints</h4>
            <Markdown text={problem.constraints} />
          </div>
        )}
        {samples.map((s, i) => (
          <div key={s.key} data-testid="preview-sample">
            <h4 className="font-medium">Example {i + 1}</h4>
            <pre className="overflow-x-auto rounded bg-black/10 p-2 text-xs dark:bg-white/15">{`Input:\n${s.input}\nOutput:\n${s.expected}`}</pre>
            {s.explanation && <p className="text-sm">{s.explanation}</p>}
          </div>
        ))}
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ source & publishing

function SourceAndPublish({
  problem,
  canPublish,
  busy,
  saveSource,
  publish,
  unpublish,
  archive,
}: {
  problem: ProblemView;
  canPublish: boolean;
  busy: boolean;
  saveSource: (body: Record<string, string>) => Promise<unknown>;
  publish: (confirmed: boolean) => Promise<unknown>;
  unpublish: () => Promise<unknown>;
  archive: () => Promise<unknown>;
}) {
  const [kind, setKind] = useState(problem.source?.kind ?? "original");
  const [license, setLicense] = useState(problem.source?.license ?? "");
  const [attribution, setAttribution] = useState(problem.source?.attribution ?? "");
  const [sourceUrl, setSourceUrl] = useState(problem.source?.sourceUrl ?? "");
  const [confirmed, setConfirmed] = useState(false);
  const needsLicense = kind === "licensed" || kind === "open_license";

  return (
    <section aria-label="Source and publishing" className="flex flex-col gap-4">
      <h2 className="text-lg font-medium">Source and publishing</h2>
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          void saveSource({ kind, license, attribution, sourceUrl });
        }}
      >
        <label className="flex flex-col gap-1 text-sm">
          Where does this problem come from?
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
          {problem.source
            ? problem.source.reviewedAt
              ? `Signed off ${when(problem.source.reviewedAt)}`
              : "Recorded, not yet signed off"
            : "No source recorded yet"}
        </span>
      </form>

      {canPublish ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm opacity-80">
            Publishing needs: a source, the statement and formats, at least one sample and one
            hidden test, and <strong>verified</strong> test data (reference solution checked against
            a brute-force solution; available with the judge).
          </p>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              data-testid="originality"
            />
            I confirm this problem is original or properly licensed and I have reviewed it.
          </label>
          <div className="flex flex-wrap gap-2">
            <button
              className={btn}
              disabled={busy || !confirmed}
              onClick={() => void publish(confirmed).then(() => setConfirmed(false))}
              data-testid="problem-publish"
            >
              {problem.status === "published" ? "Publish changes" : "Publish problem"}
            </button>
            {problem.status === "published" && (
              <button
                className={btn}
                disabled={busy}
                onClick={() => void unpublish()}
                data-testid="problem-unpublish"
              >
                Unpublish
              </button>
            )}
            {problem.status !== "archived" && (
              <button
                className={btn}
                disabled={busy}
                onClick={() => void archive()}
                data-testid="problem-archive"
              >
                Archive
              </button>
            )}
          </div>
        </div>
      ) : (
        <p className="text-sm opacity-70">Publishing is done by an admin.</p>
      )}
    </section>
  );
}
