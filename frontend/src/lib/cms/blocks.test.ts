import { describe, expect, it } from "vitest";
import {
  addOption,
  fromApi,
  LIMITS,
  move,
  newBlock,
  newQuestion,
  remove,
  removeOption,
  sameBlocks,
  setQuestionType,
  toApi,
  toggleCorrect,
  validateBlocks,
  type Block,
} from "./blocks";

describe("blocks", () => {
  it("creates defaults for each type with unique keys", () => {
    const a = newBlock("markdown");
    const b = newBlock("markdown");
    expect(a.key).not.toBe(b.key);
    expect(newBlock("code").data.language).toBe("java");
    expect(newBlock("callout").data.kind).toBe("info");
  });

  it("round-trips through the API shape and drops empty captions and unknown keys", () => {
    const api = [
      { type: "markdown", data: { text: "hi", extra: "x" } },
      { type: "code", data: { language: "python", code: "print(1)", caption: "" } },
      { type: "callout", data: { kind: "tip", text: "t" } },
      { type: "unknown", data: {} },
    ];
    const blocks = fromApi(api);
    expect(blocks).toHaveLength(3);
    expect(toApi(blocks)).toEqual([
      { type: "markdown", data: { text: "hi" } },
      { type: "code", data: { language: "python", code: "print(1)" } },
      { type: "callout", data: { kind: "tip", text: "t" } },
    ]);
  });

  it("moves and removes immutably, ignoring out-of-range moves", () => {
    const items = [1, 2, 3];
    expect(move(items, 0, 1)).toEqual([2, 1, 3]);
    expect(move(items, 0, -1)).toEqual([1, 2, 3]);
    expect(move(items, 2, 1)).toEqual([1, 2, 3]);
    expect(remove(items, 1)).toEqual([1, 3]);
    expect(items).toEqual([1, 2, 3]);
  });

  it("validates like the server", () => {
    const md = newBlock("markdown");
    expect(validateBlocks([md])[0]).toMatch(/empty/);
    md.data.text = "ok";
    expect(validateBlocks([md])).toEqual([]);
    md.data.text = "x".repeat(LIMITS.markdown + 1);
    expect(validateBlocks([md])[0]).toMatch(/too long/);
    md.data.text = "a\u0000b";
    expect(validateBlocks([md])[0]).toMatch(/control/);
    const code = newBlock("code");
    code.data.code = "int x;";
    code.data.language = "ruby";
    expect(validateBlocks([code])[0]).toMatch(/language/);
    const callout = newBlock("callout");
    callout.data.text = "t";
    callout.data.kind = "danger";
    expect(validateBlocks([callout])[0]).toMatch(/kind/);
  });

  it("accepts newlines and tabs but caps the block count", () => {
    const md = newBlock("markdown");
    md.data.text = "line1\n\tline2\r\n";
    expect(validateBlocks([md])).toEqual([]);
    const many = Array.from({ length: LIMITS.blocks + 1 }, () => ({
      ...md,
      key: Math.random().toString(),
    }));
    expect(validateBlocks(many).join()).toMatch(/At most/);
  });

  it("detects unsaved changes", () => {
    const a = newBlock("markdown");
    a.data.text = "x";
    const b = { ...a, key: "other", data: { text: "x" } };
    expect(sameBlocks([a], [b])).toBe(true);
    expect(sameBlocks([a], [{ ...b, data: { text: "y" } }])).toBe(false);
  });
});

describe("quiz blocks", () => {
  const valid = (): Block => {
    const b = newBlock("quiz");
    const q = b.quiz!.questions[0];
    q.prompt = "Which is O(1)?";
    q.options[0].text = "Array index";
    q.options[1].text = "Linear search";
    return b;
  };

  it("starts with one single-choice question whose first option is the answer", () => {
    const b = newBlock("quiz");
    expect(b.quiz!.questions).toHaveLength(1);
    const q = b.quiz!.questions[0];
    expect(q.type).toBe("single");
    expect(q.options).toHaveLength(2);
    expect(q.correct).toEqual([q.options[0].id]);
    expect(new Set([b.quiz!.quizId, q.id, ...q.options.map((o) => o.id)]).size).toBe(4); // distinct UUIDs
  });

  it("round-trips through the API shape keeping every id, and drops empty optional text", () => {
    const b = valid();
    b.quiz!.title = "  ";
    b.quiz!.questions[0].explanation = "";
    const api = toApi([b]);
    const data = api[0].data as Record<string, unknown>;
    expect(data.title).toBeUndefined();
    const q = (data.questions as Record<string, unknown>[])[0];
    expect(q.explanation).toBeUndefined();
    const back = fromApi(api)[0];
    expect(back.quiz!.quizId).toBe(b.quiz!.quizId);
    expect(back.quiz!.questions[0].options.map((o) => o.id)).toEqual(
      b.quiz!.questions[0].options.map((o) => o.id),
    );
    expect(back.quiz!.questions[0].correct).toEqual(b.quiz!.questions[0].correct);
    expect(sameBlocks([b], fromApi(api))).toBe(true);
  });

  it("keeps the type's invariants when it changes", () => {
    const q = newQuestion("single");
    const tf = setQuestionType(q, "true_false");
    expect(tf.options.map((o) => o.text)).toEqual(["True", "False"]);
    expect(tf.correct).toEqual([tf.options[0].id]);
    const back = setQuestionType(tf, "single");
    expect(back.options).toHaveLength(2);
    expect(back.options.map((o) => o.text)).toEqual(["", ""]); // the fixed True/False texts do not leak into a choice question
    const multi = { ...q, type: "multi" as const, correct: [q.options[0].id, q.options[1].id] };
    expect(setQuestionType(multi, "single").correct).toHaveLength(1);
    expect(setQuestionType(q, "single")).toBe(q);
  });

  it("marks answers per type", () => {
    const q = newQuestion("single");
    expect(toggleCorrect(q, q.options[1].id).correct).toEqual([q.options[1].id]);
    const m = { ...q, type: "multi" as const, correct: [q.options[0].id] };
    expect(toggleCorrect(m, q.options[1].id).correct).toEqual([q.options[0].id, q.options[1].id]);
    expect(toggleCorrect(toggleCorrect(m, q.options[1].id), q.options[1].id).correct).toEqual([
      q.options[0].id,
    ]);
  });

  it("bounds the options and never leaves a question without an answer", () => {
    let q = newQuestion("single");
    for (let i = 0; i < 10; i++) q = addOption(q);
    expect(q.options).toHaveLength(6);
    let m = { ...newQuestion("multi") };
    for (let i = 0; i < 10; i++) m = addOption(m);
    expect(m.options).toHaveLength(8);
    expect(addOption(setQuestionType(newQuestion(), "true_false")).options).toHaveLength(2);
    const two = newQuestion("single");
    expect(removeOption(two, two.options[0].id)).toBe(two); // never below two options
    q = removeOption(q, q.options[0].id); // the answer is removed: the first remaining option takes over
    expect(q.correct).toEqual([q.options[0].id]);
  });

  it("accepts a complete quiz and mirrors the server's refusals", () => {
    expect(validateBlocks([valid()])).toEqual([]);
    const empty = valid();
    empty.quiz!.questions[0].prompt = " ";
    expect(validateBlocks([empty]).join(" ")).toContain("write the question");
    const noOption = valid();
    noOption.quiz!.questions[0].options[1].text = "";
    expect(validateBlocks([noOption]).join(" ")).toContain("option 2: write the option");
    const noAnswer = valid();
    noAnswer.quiz!.questions[0].correct = [];
    expect(validateBlocks([noAnswer]).join(" ")).toContain("mark the correct answer");
    const stray = valid();
    stray.quiz!.questions[0].correct = ["nope"];
    expect(validateBlocks([stray]).join(" ")).toContain("must be one of the options");
    const two = valid();
    two.quiz!.questions[0].correct = two.quiz!.questions[0].options.map((o) => o.id);
    expect(validateBlocks([two]).join(" ")).toContain("exactly one option");
    const tooMany = valid();
    tooMany.quiz!.questions = Array.from(
      { length: LIMITS.quizQuestions + 1 },
      () => valid().quiz!.questions[0],
    );
    expect(validateBlocks([tooMany]).join(" ")).toContain("1-20 questions");
    const none = valid();
    none.quiz!.questions = [];
    expect(validateBlocks([none]).join(" ")).toContain("1-20 questions");
    const long = valid();
    long.quiz!.questions[0].options[0].text = "x".repeat(LIMITS.quizOption + 1);
    expect(validateBlocks([long]).join(" ")).toContain("too long");
    const ctl = valid();
    ctl.quiz!.questions[0].prompt = "bad\u0000";
    expect(validateBlocks([ctl]).join(" ")).toContain("control character");
    const tf = valid();
    tf.quiz!.questions[0] = { ...tf.quiz!.questions[0], type: "true_false" };
    tf.quiz!.questions[0].options.push({ id: "x", text: "Maybe" });
    expect(validateBlocks([tf]).join(" ")).toContain("needs 2 options");
  });
});
