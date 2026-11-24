/** What a learner sees of a question: no answer key (the server only sends that after grading). */
export type PublicQuestion = {
  id: string;
  type: string;
  prompt: string;
  options: { id: string; text: string }[];
};

export type Selection = Record<string, string[]>;

export type QuestionResult = {
  questionId: string;
  correct: boolean;
  selectedOptionIds: string[];
  correctOptionIds: string[];
  explanation?: string;
};

/** Single choice and true/false replace the selection; multiple choice toggles. */
export function select(selection: Selection, q: PublicQuestion, optionId: string): Selection {
  const now = selection[q.id] ?? [];
  const next =
    q.type === "multi"
      ? now.includes(optionId)
        ? now.filter((o) => o !== optionId)
        : [...now, optionId]
      : [optionId];
  return { ...selection, [q.id]: next };
}

export function answeredAll(questions: readonly PublicQuestion[], selection: Selection): boolean {
  return questions.length > 0 && questions.every((q) => (selection[q.id] ?? []).length > 0);
}

/** The request body for grading: every question, in order, with what was chosen. */
export function attemptBody(questions: readonly PublicQuestion[], selection: Selection) {
  return {
    answers: questions.map((q) => ({ questionId: q.id, optionIds: selection[q.id] ?? [] })),
  };
}

export function scoreText(score: number, total: number): string {
  if (total <= 0) return "";
  const pct = Math.round((score / total) * 100);
  return `${score} of ${total} correct (${pct}%)`;
}

/** How one option should be shown after grading. */
export function optionState(
  result: QuestionResult | undefined,
  optionId: string,
): "neutral" | "right" | "missed" | "wrong" {
  if (!result) return "neutral";
  const picked = result.selectedOptionIds.includes(optionId);
  const isCorrect = result.correctOptionIds.includes(optionId);
  if (picked && isCorrect) return "right";
  if (picked) return "wrong";
  return isCorrect ? "missed" : "neutral";
}
