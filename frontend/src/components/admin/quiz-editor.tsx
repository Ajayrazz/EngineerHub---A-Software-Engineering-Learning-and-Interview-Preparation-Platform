"use client";

import {
  LIMITS,
  QUIZ_TYPES,
  addOption,
  newQuestion,
  removeOption,
  setQuestionType,
  toggleCorrect,
  type Quiz,
  type QuizQuestion,
} from "@/lib/cms/blocks";
import { btn, input } from "./ui";

/**
 * Authoring of one quiz block: questions of three types, an answer key and an optional explanation.
 * The key is stored with the lesson but never sent to learners before they submit an attempt.
 */
export function QuizEditor({
  quiz,
  index,
  onChange,
}: {
  quiz: Quiz;
  index: number;
  onChange: (q: Quiz) => void;
}) {
  const setQuestion = (qi: number, q: QuizQuestion) =>
    onChange({ ...quiz, questions: quiz.questions.map((x, i) => (i === qi ? q : x)) });
  const label = (qi: number, what: string) => `Block ${index + 1}, question ${qi + 1} ${what}`;

  return (
    <div className="flex flex-col gap-4" data-testid="quiz-editor">
      <label className="flex flex-col gap-1 text-sm">
        Quiz title (optional)
        <input
          className={`${input} w-96`}
          aria-label={`Block ${index + 1} quiz title`}
          value={quiz.title}
          maxLength={LIMITS.quizTitle}
          onChange={(e) => onChange({ ...quiz, title: e.target.value })}
        />
      </label>

      {quiz.questions.map((q, qi) => (
        <fieldset
          key={q.id}
          className="flex flex-col gap-2 rounded border p-3"
          data-testid="quiz-question-editor"
        >
          <legend className="px-1 text-sm font-medium">Question {qi + 1}</legend>
          <div className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col gap-1 text-sm">
              Type
              <select
                className={input}
                aria-label={label(qi, "type")}
                value={q.type}
                onChange={(e) =>
                  setQuestion(qi, setQuestionType(q, e.target.value as QuizQuestion["type"]))
                }
              >
                {QUIZ_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              className={btn}
              disabled={quiz.questions.length <= 1}
              onClick={() =>
                onChange({ ...quiz, questions: quiz.questions.filter((_, i) => i !== qi) })
              }
              aria-label={label(qi, "remove")}
            >
              Remove question
            </button>
          </div>
          <textarea
            className={`${input} min-h-16`}
            aria-label={label(qi, "text")}
            placeholder="The question (markdown allowed)"
            value={q.prompt}
            maxLength={LIMITS.quizPrompt}
            onChange={(e) => setQuestion(qi, { ...q, prompt: e.target.value })}
          />
          <ul className="flex flex-col gap-2">
            {q.options.map((o, oi) => (
              <li key={o.id} className="flex items-center gap-2">
                <input
                  type={q.type === "multi" ? "checkbox" : "radio"}
                  name={`correct-${q.id}`}
                  checked={q.correct.includes(o.id)}
                  onChange={() => setQuestion(qi, toggleCorrect(q, o.id))}
                  aria-label={label(qi, `option ${oi + 1} is correct`)}
                />
                <input
                  className={`${input} w-96`}
                  aria-label={label(qi, `option ${oi + 1} text`)}
                  value={o.text}
                  maxLength={LIMITS.quizOption}
                  readOnly={q.type === "true_false"}
                  onChange={(e) =>
                    setQuestion(qi, {
                      ...q,
                      options: q.options.map((x) =>
                        x.id === o.id ? { ...x, text: e.target.value } : x,
                      ),
                    })
                  }
                />
                {q.type !== "true_false" && (
                  <button
                    className={btn}
                    disabled={q.options.length <= 2}
                    onClick={() => setQuestion(qi, removeOption(q, o.id))}
                    aria-label={label(qi, `remove option ${oi + 1}`)}
                  >
                    ×
                  </button>
                )}
              </li>
            ))}
          </ul>
          {q.type !== "true_false" && (
            <div>
              <button
                className={btn}
                onClick={() => setQuestion(qi, addOption(q))}
                aria-label={label(qi, "add option")}
              >
                + option
              </button>
            </div>
          )}
          <textarea
            className={`${input} min-h-12`}
            aria-label={label(qi, "explanation")}
            placeholder="Explanation shown after answering (optional)"
            value={q.explanation}
            maxLength={LIMITS.quizPrompt}
            onChange={(e) => setQuestion(qi, { ...q, explanation: e.target.value })}
          />
        </fieldset>
      ))}

      <div>
        <button
          className={btn}
          disabled={quiz.questions.length >= LIMITS.quizQuestions}
          onClick={() => onChange({ ...quiz, questions: [...quiz.questions, newQuestion()] })}
          aria-label={`Block ${index + 1} add question`}
        >
          + question
        </button>
      </div>
    </div>
  );
}
