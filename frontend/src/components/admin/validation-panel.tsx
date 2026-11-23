"use client";

import { useEffect, useState } from "react";
import type { components } from "@/lib/api/schema";
import { adminFetch } from "./admin-fetch";
import { Notice, btn, input, when } from "./ui";

type ValidationView = components["schemas"]["ValidationView"];
type RunView = components["schemas"]["RunView"];

const ROLES: { role: string; title: string; help: string }[] = [
  {
    role: "reference",
    title: "Reference solution",
    help: "The solution you claim is correct and efficient.",
  },
  {
    role: "brute",
    title: "Brute-force solution",
    help: "Slow but obviously correct; used to cross-check the reference.",
  },
  {
    role: "generator",
    title: "Input generator",
    help: "Reads a seed (first line of stdin) and prints one valid random input.",
  },
];
const LANGUAGES = ["python", "java", "cpp"];

export function ValidationPanel({
  csrfToken,
  problemId,
  testsVersion,
  initial,
  onStatus,
}: {
  csrfToken: string;
  problemId: string;
  testsVersion: number;
  initial: ValidationView;
  onStatus: (s: string) => void;
}) {
  const base = `admin/problems/${problemId}/validation`;
  const [view, setView] = useState(initial);
  const [flash, setFlash] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [cases, setCases] = useState(50);

  // the test set (or anything else) changed elsewhere in the editor: reload so stale runs show up
  useEffect(() => {
    if (testsVersion === view.testcaseVersion) return;
    void adminFetch(csrfToken, base, "GET").then((r) => {
      if (r.ok) setView(r.data as ValidationView);
    });
  }, [testsVersion, view.testcaseVersion, csrfToken, base]);

  async function run(path: string, method: "PUT" | "POST", body: unknown, ok: string) {
    setBusy(true);
    setFlash(null);
    const r = await adminFetch(csrfToken, path, method, body);
    if (!r.ok) {
      setBusy(false);
      setFlash({ kind: "error", text: r.message });
      return;
    }
    const fresh = await adminFetch(csrfToken, base, "GET");
    setBusy(false);
    if (fresh.ok) {
      const v = fresh.data as ValidationView;
      setView(v);
      onStatus(v.validationStatus ?? "unverified");
    }
    setFlash({ kind: "ok", text: ok });
  }

  return (
    <section aria-label="Verification" className="flex flex-col gap-4">
      <h2 className="text-lg font-medium">Verification</h2>
      <p className="text-sm opacity-80">
        A problem is verified when the reference solution reproduces every expected output and
        agrees with the brute-force solution on generated inputs. These programs are only ever run
        inside the judge sandbox, never on the web server. Changing a program, the tests, the limits
        or the comparison resets verification.
      </p>
      {!view.executorAvailable && (
        <Notice kind="error">
          <span data-testid="no-executor">
            No code execution backend is available yet, so verification cannot run. You can already
            store the programs.
          </span>
        </Notice>
      )}
      {flash && <Notice kind={flash.kind}>{flash.text}</Notice>}

      {ROLES.map((r) => (
        <ProgramForm
          key={`${r.role}-${view.programs?.find((p) => p.role === r.role)?.sha256 ?? "new"}`}
          title={r.title}
          help={r.help}
          role={r.role}
          saved={view.programs?.find((p) => p.role === r.role)}
          busy={busy}
          save={(language, source) =>
            run(`${base}/programs/${r.role}`, "PUT", { language, source }, `${r.title} saved.`)
          }
        />
      ))}

      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Generated cases
          <input
            className={`${input} w-28`}
            type="number"
            min={1}
            max={500}
            value={cases}
            onChange={(e) => setCases(Number(e.target.value))}
          />
        </label>
        <button
          className={btn}
          disabled={busy}
          data-testid="validation-run"
          onClick={() =>
            void run(`${base}/runs`, "POST", { generatedCases: cases }, "Verification requested.")
          }
        >
          Request verification
        </button>
      </div>

      <table className="w-full text-left text-sm" data-testid="validation-runs">
        <thead>
          <tr className="border-b opacity-70">
            <th className="py-2">Requested</th>
            <th>Status</th>
            <th>Test set</th>
            <th>Checked</th>
            <th>Result</th>
          </tr>
        </thead>
        <tbody>
          {(view.runs ?? []).map((r) => (
            <RunRow key={r.id} run={r} />
          ))}
        </tbody>
      </table>
      {(view.runs ?? []).length === 0 && <p className="text-sm">No verification runs yet.</p>}
    </section>
  );
}

function RunRow({ run }: { run: RunView }) {
  return (
    <tr className="border-b align-top" data-testid="validation-run-row">
      <td className="py-2">{when(run.requestedAt)}</td>
      <td data-testid="run-status">{run.status}</td>
      <td>v{run.testcaseVersion}</td>
      <td>
        {run.testsChecked ?? "—"} tests / {run.generatedChecked ?? "—"} of {run.generatedCases}{" "}
        generated
      </td>
      <td>
        {run.summary}
        {run.counterexample && (
          <pre className="mt-1 max-w-md overflow-x-auto rounded bg-black/10 p-2 text-xs dark:bg-white/15">
            {run.counterexample}
          </pre>
        )}
      </td>
    </tr>
  );
}

function ProgramForm({
  title,
  help,
  role,
  saved,
  busy,
  save,
}: {
  title: string;
  help: string;
  role: string;
  saved?: components["schemas"]["ProgramView"];
  busy: boolean;
  save: (language: string, source: string) => Promise<void>;
}) {
  const [language, setLanguage] = useState(saved?.language ?? "python");
  const [source, setSource] = useState(saved?.source ?? "");
  const dirty = source !== (saved?.source ?? "") || language !== (saved?.language ?? "python");
  return (
    <form
      className="flex flex-col gap-2 rounded border p-3"
      data-testid={`program-${role}`}
      onSubmit={(e) => {
        e.preventDefault();
        void save(language, source);
      }}
    >
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <strong>{title}</strong>
        <span className="opacity-70">{help}</span>
        <select
          aria-label={`${title} language`}
          className={input}
          value={language}
          onChange={(e) => setLanguage(e.target.value)}
        >
          {LANGUAGES.map((l) => (
            <option key={l}>{l}</option>
          ))}
        </select>
        {saved && <span className="opacity-60">saved {when(saved.updatedAt)}</span>}
      </div>
      <textarea
        aria-label={`${title} source`}
        className={`${input} min-h-32 font-mono`}
        value={source}
        maxLength={64_000}
        spellCheck={false}
        onChange={(e) => setSource(e.target.value)}
      />
      <div>
        <button
          className={btn}
          type="submit"
          disabled={busy || !dirty || source.trim() === ""}
          data-testid={`save-${role}`}
        >
          Save {role}
        </button>
      </div>
    </form>
  );
}
