import { CATALOG, entry as vizEntry, inputProblem as vizProblem } from "@/lib/viz";
/**
 * Pure editor logic for lesson blocks (no React). The server re-validates everything (BlockValidator);
 * these checks only give authors fast feedback and mirror its limits.
 */
export type BlockType = "markdown" | "code" | "callout" | "quiz" | "viz";

export type QuizType = "single" | "multi" | "true_false";
export type QuizOption = { id: string; text: string };
export type QuizQuestion = {
  id: string;
  type: QuizType;
  prompt: string;
  explanation: string;
  options: QuizOption[];
  correct: string[];
};
export type Quiz = { quizId: string; title: string; questions: QuizQuestion[] };

/** Text blocks keep their fields in `data`; a quiz keeps its structure in `quiz` (and `data` stays empty). */
export type Block = {
  key: string;
  type: BlockType;
  data: Record<string, string>;
  quiz?: Quiz;
};
export type ApiBlock = { type?: string; data?: Record<string, unknown> };

export const LIMITS = {
  markdown: 50_000,
  code: 20_000,
  callout: 2_000,
  caption: 200,
  blocks: 200,
  quizQuestions: 20,
  quizPrompt: 2_000,
  quizOption: 300,
  quizTitle: 100,
} as const;
export const QUIZ_TYPES: { value: QuizType; label: string }[] = [
  { value: "single", label: "One correct answer" },
  { value: "multi", label: "Several correct answers" },
  { value: "true_false", label: "True / false" },
];
export const LANGUAGES = ["java", "python", "cpp", "text"] as const;
export const CALLOUT_KINDS = ["info", "warning", "tip"] as const;

let counter = 0;
const nextKey = () => `b${Date.now().toString(36)}${(counter++).toString(36)}`;

const uid = () => crypto.randomUUID();

export function newOption(text = ""): QuizOption {
  return { id: uid(), text };
}

export function newQuestion(type: QuizType = "single"): QuizQuestion {
  const options =
    type === "true_false" ? [newOption("True"), newOption("False")] : [newOption(), newOption()];
  return { id: uid(), type, prompt: "", explanation: "", options, correct: [options[0].id] };
}

export function newQuiz(): Quiz {
  return { quizId: uid(), title: "", questions: [newQuestion()] };
}

/** Changing the type keeps what can be kept and repairs the rest (true/false has exactly two fixed options). */
export function setQuestionType(q: QuizQuestion, type: QuizType): QuizQuestion {
  if (type === q.type) return q;
  if (type === "true_false") {
    const options = [newOption("True"), newOption("False")];
    return { ...q, type, options, correct: [options[0].id] };
  }
  const options = q.type === "true_false" ? [newOption(), newOption()] : q.options;
  const correct = q.type === "true_false" ? [options[0].id] : q.correct;
  return { ...q, type, options, correct: type === "single" ? correct.slice(0, 1) : correct };
}

/** Single choice and true/false hold exactly one correct option; multiple choice toggles. */
export function toggleCorrect(q: QuizQuestion, optionId: string): QuizQuestion {
  if (q.type !== "multi") return { ...q, correct: [optionId] };
  return {
    ...q,
    correct: q.correct.includes(optionId)
      ? q.correct.filter((c) => c !== optionId)
      : [...q.correct, optionId],
  };
}

export function addOption(q: QuizQuestion): QuizQuestion {
  const max = q.type === "multi" ? 8 : q.type === "true_false" ? 2 : 6;
  return q.options.length >= max ? q : { ...q, options: [...q.options, newOption()] };
}

export function removeOption(q: QuizQuestion, optionId: string): QuizQuestion {
  if (q.type === "true_false" || q.options.length <= 2) return q;
  const options = q.options.filter((o) => o.id !== optionId);
  let correct = q.correct.filter((c) => c !== optionId);
  if (correct.length === 0) correct = [options[0].id]; // never leave a question without an answer
  return { ...q, options, correct };
}

export function newBlock(type: BlockType): Block {
  if (type === "quiz") return { key: nextKey(), type, data: {}, quiz: newQuiz() };
  if (type === "viz") {
    const e = CATALOG[0];
    return {
      key: nextKey(),
      type,
      data: { algorithm: e.id, input: e.defaultInput, param: e.defaultParam },
    };
  }
  const data: Record<string, string> =
    type === "markdown"
      ? { text: "" }
      : type === "code"
        ? { language: "java", code: "", caption: "" }
        : { kind: "info", text: "" };
  return { key: nextKey(), type, data };
}

const asString = (v: unknown) => (typeof v === "string" ? v : "");

function quizFromApi(d: Record<string, unknown> | undefined): Quiz {
  const questions = Array.isArray(d?.questions) ? (d.questions as Record<string, unknown>[]) : [];
  return {
    quizId: asString(d?.quizId) || uid(),
    title: asString(d?.title),
    questions: questions.map((q) => ({
      id: asString(q.id) || uid(),
      type: (["single", "multi", "true_false"].includes(asString(q.type))
        ? q.type
        : "single") as QuizType,
      prompt: asString(q.prompt),
      explanation: asString(q.explanation),
      options: (Array.isArray(q.options) ? (q.options as Record<string, unknown>[]) : []).map(
        (o) => ({
          id: asString(o.id) || uid(),
          text: asString(o.text),
        }),
      ),
      correct: Array.isArray(q.correct) ? (q.correct as unknown[]).map(asString) : [],
    })),
  };
}

export function fromApi(blocks: readonly ApiBlock[] | undefined): Block[] {
  return (blocks ?? []).flatMap((b): Block[] => {
    if (b.type === "quiz")
      return [{ key: nextKey(), type: "quiz" as const, data: {}, quiz: quizFromApi(b.data) }];
    if (b.type === "viz") {
      const d = b.data ?? {};
      return [
        {
          key: nextKey(),
          type: "viz" as const,
          data: {
            algorithm: asString(d.algorithm),
            input: asString(d.input),
            param: asString(d.param),
          },
        },
      ];
    }
    if (b.type !== "markdown" && b.type !== "code" && b.type !== "callout") return [];
    const data: Record<string, string> = {};
    for (const [k, v] of Object.entries(b.data ?? {})) data[k] = typeof v === "string" ? v : "";
    return [{ key: nextKey(), type: b.type, data }];
  });
}

/** What the API accepts: only known keys, empty optional values dropped. */
export function toApi(blocks: readonly Block[]): ApiBlock[] {
  return blocks.map((b) => {
    if (b.type === "viz") {
      const data: Record<string, unknown> = {
        algorithm: b.data.algorithm ?? "",
        input: (b.data.input ?? "").trim(),
      };
      if ((b.data.param ?? "").trim() !== "") data.param = b.data.param.trim();
      return { type: b.type, data };
    }
    if (b.type === "quiz" && b.quiz) {
      const data: Record<string, unknown> = {
        quizId: b.quiz.quizId,
        questions: b.quiz.questions.map((q) => {
          const out: Record<string, unknown> = {
            id: q.id,
            type: q.type,
            prompt: q.prompt,
            options: q.options.map((o) => ({ id: o.id, text: o.text })),
            correct: q.correct,
          };
          if (q.explanation.trim() !== "") out.explanation = q.explanation;
          return out;
        }),
      };
      if (b.quiz.title.trim() !== "") data.title = b.quiz.title;
      return { type: b.type, data };
    }
    if (b.type === "markdown") return { type: b.type, data: { text: b.data.text ?? "" } };
    if (b.type === "callout")
      return { type: b.type, data: { kind: b.data.kind ?? "info", text: b.data.text ?? "" } };
    const data: Record<string, unknown> = {
      language: b.data.language ?? "text",
      code: b.data.code ?? "",
    };
    if ((b.data.caption ?? "").trim() !== "") data.caption = b.data.caption;
    return { type: b.type, data };
  });
}

export function move<T>(items: readonly T[], index: number, delta: -1 | 1): T[] {
  const to = index + delta;
  if (index < 0 || index >= items.length || to < 0 || to >= items.length) return [...items];
  const copy = [...items];
  [copy[index], copy[to]] = [copy[to], copy[index]];
  return copy;
}

export function remove<T>(items: readonly T[], index: number): T[] {
  return items.filter((_, i) => i !== index);
}

const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;

/** Messages for problems the server would also reject (empty list = fine). */
export function validateBlocks(blocks: readonly Block[]): string[] {
  const errors: string[] = [];
  if (blocks.length > LIMITS.blocks) errors.push(`At most ${LIMITS.blocks} blocks per lesson.`);
  blocks.forEach((b, i) => {
    const n = i + 1;
    if (b.type === "quiz") {
      errors.push(...validateQuiz(n, b.quiz));
      return;
    }
    if (b.type === "viz") {
      const problem = vizEntry(b.data.algorithm)
        ? vizProblem(b.data.algorithm ?? "", b.data.input ?? "", b.data.param ?? "")
        : "Choose an algorithm.";
      if (problem) errors.push(`Block ${n}: ${problem}`);
      return;
    }
    const body = b.type === "code" ? b.data.code : b.data.text;
    const max = LIMITS[b.type];
    if (!body || body.trim() === "") errors.push(`Block ${n}: the ${b.type} block is empty.`);
    else if (body.length > max)
      errors.push(`Block ${n}: too long (${body.length}/${max} characters).`);
    if (body && CONTROL.test(body)) errors.push(`Block ${n}: contains a control character.`);
    if (b.type === "code" && !(LANGUAGES as readonly string[]).includes(b.data.language ?? ""))
      errors.push(`Block ${n}: choose a language.`);
    if (b.type === "code" && (b.data.caption ?? "").length > LIMITS.caption)
      errors.push(`Block ${n}: caption is too long.`);
    if (b.type === "callout" && !(CALLOUT_KINDS as readonly string[]).includes(b.data.kind ?? ""))
      errors.push(`Block ${n}: choose a callout kind.`);
  });
  return errors;
}

export function sameBlocks(a: readonly Block[], b: readonly Block[]): boolean {
  return JSON.stringify(toApi(a)) === JSON.stringify(toApi(b));
}

/** Mirrors the server's quiz rules (BlockValidator); the server stays authoritative. */
export function validateQuiz(n: number, quiz: Quiz | undefined): string[] {
  const errors: string[] = [];
  if (!quiz) return [`Block ${n}: the quiz is missing.`];
  if (quiz.title.length > LIMITS.quizTitle) errors.push(`Block ${n}: the quiz title is too long.`);
  if (quiz.questions.length < 1 || quiz.questions.length > LIMITS.quizQuestions)
    errors.push(`Block ${n}: a quiz needs 1-${LIMITS.quizQuestions} questions.`);
  quiz.questions.forEach((q, qi) => {
    const at = `Block ${n}, question ${qi + 1}`;
    if (q.prompt.trim() === "") errors.push(`${at}: write the question.`);
    else if (q.prompt.length > LIMITS.quizPrompt) errors.push(`${at}: the question is too long.`);
    if (q.explanation.length > LIMITS.quizPrompt)
      errors.push(`${at}: the explanation is too long.`);
    if (
      CONTROL.test(q.prompt) ||
      CONTROL.test(q.explanation) ||
      q.options.some((o) => CONTROL.test(o.text))
    )
      errors.push(`${at}: contains a control character.`);
    const [min, max] = q.type === "multi" ? [2, 8] : q.type === "true_false" ? [2, 2] : [2, 6];
    if (q.options.length < min || q.options.length > max)
      errors.push(`${at}: needs ${min === max ? max : `${min}-${max}`} options.`);
    q.options.forEach((o, oi) => {
      if (o.text.trim() === "") errors.push(`${at}, option ${oi + 1}: write the option.`);
      else if (o.text.length > LIMITS.quizOption)
        errors.push(`${at}, option ${oi + 1}: too long (${o.text.length}/${LIMITS.quizOption}).`);
    });
    const ids = new Set(q.options.map((o) => o.id));
    if (q.correct.length === 0) errors.push(`${at}: mark the correct answer.`);
    else if (q.correct.some((c) => !ids.has(c)))
      errors.push(`${at}: the correct answer must be one of the options.`);
    else if (q.type !== "multi" && q.correct.length !== 1)
      errors.push(`${at}: exactly one option must be correct.`);
  });
  return errors;
}
