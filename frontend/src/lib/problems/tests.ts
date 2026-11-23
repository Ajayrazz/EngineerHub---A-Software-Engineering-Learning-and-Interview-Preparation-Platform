/**
 * Pure logic of the test-case table (no React). Mirrors the server's TestCaseValidator for fast feedback;
 * the server remains authoritative.
 */
export type TestRow = {
  key: string;
  input: string;
  expected: string;
  sample: boolean;
  explanation: string;
};
export type ApiTest = {
  sample?: boolean;
  input?: string;
  expected?: string;
  explanation?: string | null;
};

export const LIMITS = { tests: 200, samples: 10, chars: 100_000, explanation: 2_000 } as const;

let counter = 0;
const nextKey = () => `t${Date.now().toString(36)}${(counter++).toString(36)}`;

export function newRow(sample = false): TestRow {
  return { key: nextKey(), input: "", expected: "", sample, explanation: "" };
}

export function fromApi(tests: readonly ApiTest[] | undefined): TestRow[] {
  return (tests ?? []).map((t) => ({
    key: nextKey(),
    input: t.input ?? "",
    expected: t.expected ?? "",
    sample: t.sample ?? false,
    explanation: t.explanation ?? "",
  }));
}

/** What the API accepts. Explanations only exist on samples. */
export function toApi(rows: readonly TestRow[]) {
  return rows.map((r) => ({
    input: r.input,
    expected: r.expected,
    sample: r.sample,
    ...(r.sample && r.explanation.trim() !== "" ? { explanation: r.explanation } : {}),
  }));
}

export function sameTests(a: readonly TestRow[], b: readonly TestRow[]): boolean {
  return JSON.stringify(toApi(a)) === JSON.stringify(toApi(b));
}

export function move<T>(items: readonly T[], index: number, delta: -1 | 1): T[] {
  const to = index + delta;
  if (index < 0 || index >= items.length || to < 0 || to >= items.length) return [...items];
  const copy = [...items];
  [copy[index], copy[to]] = [copy[to], copy[index]];
  return copy;
}

const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;

export function validateTests(rows: readonly TestRow[]): string[] {
  const errors: string[] = [];
  if (rows.length > LIMITS.tests) errors.push(`At most ${LIMITS.tests} test cases.`);
  const samples = rows.filter((r) => r.sample).length;
  if (samples > LIMITS.samples) errors.push(`At most ${LIMITS.samples} sample tests.`);
  rows.forEach((r, i) => {
    const n = i + 1;
    if (r.expected.trim() === "") errors.push(`Test ${n}: the expected output is empty.`);
    if (r.input.length > LIMITS.chars || r.expected.length > LIMITS.chars)
      errors.push(`Test ${n}: too long (at most ${LIMITS.chars} characters).`);
    if (CONTROL.test(r.input) || CONTROL.test(r.expected))
      errors.push(`Test ${n}: contains a control character.`);
    if (r.explanation.length > LIMITS.explanation)
      errors.push(`Test ${n}: the explanation is too long.`);
  });
  return errors;
}

export function counts(rows: readonly TestRow[]) {
  const sample = rows.filter((r) => r.sample).length;
  return { sample, hidden: rows.length - sample };
}
