import { describe, expect, it } from "vitest";
import {
  answeredAll,
  attemptBody,
  optionState,
  scoreText,
  select,
  type PublicQuestion,
} from "./logic";

const single: PublicQuestion = {
  id: "q1",
  type: "single",
  prompt: "p",
  options: [
    { id: "a", text: "A" },
    { id: "b", text: "B" },
  ],
};
const multi: PublicQuestion = {
  id: "q2",
  type: "multi",
  prompt: "p",
  options: [
    { id: "a", text: "A" },
    { id: "b", text: "B" },
    { id: "c", text: "C" },
  ],
};
const tf: PublicQuestion = {
  id: "q3",
  type: "true_false",
  prompt: "p",
  options: [
    { id: "t", text: "True" },
    { id: "f", text: "False" },
  ],
};

describe("quiz answer handling", () => {
  it("replaces the choice for single answer and true/false questions", () => {
    let s = select({}, single, "a");
    s = select(s, single, "b");
    expect(s.q1).toEqual(["b"]);
    expect(select({}, tf, "f").q3).toEqual(["f"]);
  });

  it("toggles options for multiple choice and keeps other questions untouched", () => {
    let s = select({ q1: ["a"] }, multi, "a");
    s = select(s, multi, "c");
    expect(s.q2).toEqual(["a", "c"]);
    s = select(s, multi, "a");
    expect(s.q2).toEqual(["c"]);
    expect(s.q1).toEqual(["a"]);
  });

  it("allows checking only when every question has an answer", () => {
    const qs = [single, multi, tf];
    expect(answeredAll(qs, {})).toBe(false);
    expect(answeredAll(qs, { q1: ["a"], q2: ["b"] })).toBe(false);
    expect(answeredAll(qs, { q1: ["a"], q2: ["b"], q3: [] })).toBe(false);
    expect(answeredAll(qs, { q1: ["a"], q2: ["b"], q3: ["t"] })).toBe(true);
    expect(answeredAll([], {})).toBe(false);
  });

  it("builds the request from the questions in order, never from stray selections", () => {
    const body = attemptBody([single, tf], { q3: ["t"], q1: ["b"], ghost: ["x"] });
    expect(body).toEqual({
      answers: [
        { questionId: "q1", optionIds: ["b"] },
        { questionId: "q3", optionIds: ["t"] },
      ],
    });
    expect(attemptBody([single], {}).answers[0].optionIds).toEqual([]);
  });

  it("words the score", () => {
    expect(scoreText(2, 3)).toBe("2 of 3 correct (67%)");
    expect(scoreText(0, 4)).toBe("0 of 4 correct (0%)");
    expect(scoreText(1, 1)).toBe("1 of 1 correct (100%)");
    expect(scoreText(0, 0)).toBe("");
  });

  it("shows what was right, wrong and missed after grading", () => {
    const r = {
      questionId: "q2",
      correct: false,
      selectedOptionIds: ["a", "b"],
      correctOptionIds: ["a", "c"],
    };
    expect(optionState(r, "a")).toBe("right");
    expect(optionState(r, "b")).toBe("wrong");
    expect(optionState(r, "c")).toBe("missed");
    expect(optionState({ ...r, selectedOptionIds: [], correctOptionIds: ["a"] }, "b")).toBe(
      "neutral",
    );
    expect(optionState(undefined, "a")).toBe("neutral");
  });
});
