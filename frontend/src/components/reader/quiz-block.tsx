"use client";

import { useState } from "react";
import { btn } from "@/components/admin/ui";
import { Markdown } from "@/components/markdown";
import {
  answeredAll,
  attemptBody,
  optionState,
  scoreText,
  select,
  type PublicQuestion,
  type QuestionResult,
  type Selection,
} from "@/lib/quiz/logic";

type Result = {
  attemptNo: number;
  score: number;
  total: number;
  results: QuestionResult[];
};

const MARK: Record<string, string> = { right: "✓ ", wrong: "✗ ", missed: "→ ", neutral: "" };
const LABEL: Record<string, string> = {
  right: "your answer, correct",
  wrong: "your answer, wrong",
  missed: "the correct answer",
  neutral: "",
};

/**
 * A quiz inside a lesson. Learners receive prompts and options only; the answer key and explanations come back from
 * the server when an attempt is graded, never before. Grading needs a login (attempts are stored per learner).
 */
export function QuizBlock({
  quizId,
  title,
  questions,
  lessonId,
  csrfToken,
  loginHref,
}: {
  quizId: string;
  title?: string;
  questions: PublicQuestion[];
  lessonId: string;
  csrfToken: string | null;
  loginHref: string;
}) {
  const [selection, setSelection] = useState<Selection>({});
  const [result, setResult] = useState<Result | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function check() {
    if (!csrfToken) return;
    setBusy(true);
    setError(null);
    const r = await fetch(`/api/proxy/lessons/${lessonId}/quizzes/${quizId}/attempts`, {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "content-type": "application/json", "x-csrf-token": csrfToken },
      body: JSON.stringify(attemptBody(questions, selection)),
    }).catch(() => null);
    setBusy(false);
    if (!r?.ok) {
      const problem = (await r?.json().catch(() => ({}))) as { detail?: string } | undefined;
      setError(problem?.detail ?? "Your answers could not be checked. Please try again.");
      return;
    }
    setResult((await r.json()) as Result);
  }

  const byQuestion = new Map((result?.results ?? []).map((x) => [x.questionId, x]));
  return (
    <section
      className="flex flex-col gap-4 rounded-xl border bg-surface/70 p-4"
      aria-label={title || "Quiz"}
      data-testid="quiz"
    >
      <h3 className="text-base font-semibold">{title || "Check your understanding"}</h3>
      {questions.map((q, qi) => {
        const res = byQuestion.get(q.id);
        return (
          <fieldset
            key={q.id}
            className="flex flex-col gap-2"
            data-testid="quiz-question"
            disabled={!!result}
          >
            <legend className="mb-1 text-sm font-medium">
              {qi + 1}. <Markdown text={q.prompt} />
            </legend>
            <p className="text-xs opacity-70">
              {q.type === "multi" ? "Select all that apply." : "Select one answer."}
            </p>
            {q.options.map((o) => {
              const state = optionState(res, o.id);
              return (
                <label
                  key={o.id}
                  className={`flex items-center gap-2 rounded-xl border bg-surface/70 px-2 py-1 text-sm ${state === "right" ? "border-green-600" : state === "wrong" ? "border-red-500" : state === "missed" ? "border-amber-500" : ""}`}
                  data-state={state}
                >
                  <input
                    type={q.type === "multi" ? "checkbox" : "radio"}
                    name={`q-${q.id}`}
                    checked={(selection[q.id] ?? []).includes(o.id)}
                    onChange={() => setSelection((s) => select(s, q, o.id))}
                  />
                  <span>
                    {MARK[state]}
                    {o.text}
                    {LABEL[state] && <span className="sr-only"> ({LABEL[state]})</span>}
                  </span>
                </label>
              );
            })}
            {res && (
              <div className="text-sm" data-testid="quiz-feedback">
                <strong>{res.correct ? "Correct." : "Not quite."}</strong>
                {res.explanation && <Markdown text={res.explanation} />}
              </div>
            )}
          </fieldset>
        );
      })}

      {result ? (
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <strong data-testid="quiz-score">{scoreText(result.score, result.total)}</strong>
          <span className="opacity-70">Attempt {result.attemptNo}</span>
          <button
            className={btn}
            onClick={() => {
              setResult(null);
              setSelection({});
            }}
            data-testid="quiz-retry"
          >
            Try again
          </button>
        </div>
      ) : csrfToken ? (
        <div className="flex flex-wrap items-center gap-3">
          <button
            className={btn}
            disabled={busy || !answeredAll(questions, selection)}
            onClick={() => void check()}
            data-testid="quiz-check"
          >
            Check answers
          </button>
          {!answeredAll(questions, selection) && (
            <span className="text-xs opacity-70">Answer every question to check.</span>
          )}
        </div>
      ) : (
        <p className="text-sm">
          <a className="underline" href={loginHref}>
            Log in
          </a>{" "}
          to check your answers and keep your score.
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm" data-testid="quiz-error">
          {error}
        </p>
      )}
    </section>
  );
}
