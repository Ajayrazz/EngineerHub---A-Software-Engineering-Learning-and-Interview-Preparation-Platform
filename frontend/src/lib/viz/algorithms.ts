import {
  MAX_STEPS,
  VizInputError,
  type ArrayFrame,
  type Cell,
  type GraphFrame,
  type Mark,
  type NodeState,
  type Step,
  type Trace,
} from "./types";

/** Collects steps and refuses to grow past the ceiling, whatever the input. */
export class Recorder {
  readonly steps: Step[] = [];
  push(step: Step) {
    if (this.steps.length >= MAX_STEPS)
      throw new VizInputError("This input needs too many steps to show.");
    this.steps.push(step);
  }
}

type Marks = Record<number, Mark>;

function arrayFrame(
  values: (number | null)[],
  marks: Marks = {},
  pointers: { label: string; index: number }[] = [],
  stats: { label: string; value: string }[] = [],
  buffer?: { label: string; values: number[] },
): ArrayFrame {
  const frame: ArrayFrame = {
    kind: "array",
    cells: values.map((value, i): Cell => ({ value, mark: marks[i] ?? "none" })),
    pointers,
    stats,
  };
  if (buffer)
    frame.buffer = {
      label: buffer.label,
      cells: buffer.values.map((value) => ({ value, mark: "none" })),
    };
  return frame;
}

const range = (from: number, to: number): number[] => {
  const out: number[] = [];
  for (let i = from; i <= to; i++) out.push(i);
  return out;
};
const markAll = (indices: number[], mark: Mark, into: Marks = {}): Marks => {
  for (const i of indices) into[i] = mark;
  return into;
};

// ------------------------------------------------------------------ input parsing

export function parseNumbers(text: string, min: number, max: number): number[] {
  const values = text
    .split(",")
    .map((t) => t.trim())
    .filter((t) => t !== "")
    .map(Number);
  if (values.length < min || values.length > max || values.some((v) => !Number.isInteger(v)))
    throw new VizInputError(`Enter ${min} to ${max} whole numbers separated by commas.`);
  return values;
}

function requireSorted(values: number[]) {
  for (let i = 1; i < values.length; i++)
    if (values[i - 1] > values[i])
      throw new VizInputError("This algorithm needs a sorted list, smallest first.");
}

// ------------------------------------------------------------------ sorting

export function bubbleSort(input: string): Trace {
  const a = parseNumbers(input, 2, 16);
  const n = a.length;
  const r = new Recorder();
  const pseudo = [
    "repeat up to n - 1 passes:",
    "  for i from 0 to (last unsorted index - 1):",
    "    if a[i] > a[i+1]:",
    "      swap a[i] and a[i+1]",
    "  the last unsorted item is now in its final place",
    "stop early if a pass made no swaps",
  ];
  r.push({ frame: arrayFrame(a), note: "Start with the unsorted list.", line: 0 });
  let sortedFrom = n;
  let finished = false;
  for (let pass = 0; pass < n - 1 && !finished; pass++) {
    let swapped = false;
    for (let i = 0; i < n - 1 - pass; i++) {
      const base = markAll(range(sortedFrom, n - 1), "sorted");
      r.push({
        frame: arrayFrame(a, { ...base, [i]: "compare", [i + 1]: "compare" }),
        note: `Compare ${a[i]} and ${a[i + 1]}.`,
        line: 2,
      });
      if (a[i] > a[i + 1]) {
        [a[i], a[i + 1]] = [a[i + 1], a[i]];
        swapped = true;
        r.push({
          frame: arrayFrame(a, { ...base, [i]: "swap", [i + 1]: "swap" }),
          note: `${a[i + 1]} is greater than ${a[i]}, so swap them.`,
          line: 3,
        });
      }
    }
    sortedFrom = n - 1 - pass;
    r.push({
      frame: arrayFrame(a, markAll(range(sortedFrom, n - 1), "sorted")),
      note: `${a[sortedFrom]} is now in its final place.`,
      line: 4,
    });
    if (!swapped) {
      r.push({
        frame: arrayFrame(a, markAll(range(0, n - 1), "sorted")),
        note: "No swaps in this pass, so the list is sorted.",
        line: 5,
      });
      finished = true;
    }
  }
  if (!finished)
    r.push({
      frame: arrayFrame(a, markAll(range(0, n - 1), "sorted")),
      note: "The list is sorted.",
      line: 4,
    });
  return { algorithm: "bubble-sort", pseudo, steps: r.steps };
}

export function selectionSort(input: string): Trace {
  const a = parseNumbers(input, 2, 16);
  const n = a.length;
  const r = new Recorder();
  const pseudo = [
    "for i from 0 to n - 2:",
    "  min = i",
    "  for j from i + 1 to n - 1:",
    "    if a[j] < a[min]: min = j",
    "  swap a[i] and a[min]",
    "the first i + 1 items are now sorted",
  ];
  r.push({ frame: arrayFrame(a), note: "Start with the unsorted list.", line: 0 });
  for (let i = 0; i < n - 1; i++) {
    let min = i;
    const done = markAll(range(0, i - 1), "sorted");
    r.push({
      frame: arrayFrame(a, { ...done, [i]: "active" }, [{ label: "i", index: i }]),
      note: `Look for the smallest item from position ${i} onwards.`,
      line: 1,
    });
    for (let j = i + 1; j < n; j++) {
      r.push({
        frame: arrayFrame(a, { ...done, [min]: "active", [j]: "compare" }, [
          { label: "min", index: min },
          { label: "j", index: j },
        ]),
        note: `Is ${a[j]} smaller than the smallest so far (${a[min]})?`,
        line: 3,
      });
      if (a[j] < a[min]) {
        min = j;
        r.push({
          frame: arrayFrame(a, { ...done, [min]: "active" }, [{ label: "min", index: min }]),
          note: `Yes: ${a[min]} is the new smallest.`,
          line: 3,
        });
      }
    }
    if (min !== i) {
      [a[i], a[min]] = [a[min], a[i]];
      r.push({
        frame: arrayFrame(a, { ...done, [i]: "swap", [min]: "swap" }),
        note: `Swap the smallest (${a[i]}) into position ${i}.`,
        line: 4,
      });
    } else {
      r.push({
        frame: arrayFrame(a, { ...done, [i]: "active" }),
        note: `${a[i]} is already in place.`,
        line: 4,
      });
    }
    r.push({
      frame: arrayFrame(a, markAll(range(0, i), "sorted")),
      note: `The first ${i + 1} item${i === 0 ? " is" : "s are"} sorted.`,
      line: 5,
    });
  }
  r.push({
    frame: arrayFrame(a, markAll(range(0, n - 1), "sorted")),
    note: "The list is sorted.",
    line: 5,
  });
  return { algorithm: "selection-sort", pseudo, steps: r.steps };
}

export function insertionSort(input: string): Trace {
  const a = parseNumbers(input, 2, 16);
  const n = a.length;
  const r = new Recorder();
  const pseudo = [
    "for i from 1 to n - 1:",
    "  j = i",
    "  while j > 0 and a[j-1] > a[j]:",
    "    swap a[j-1] and a[j]",
    "    j = j - 1",
    "a[0..i] is now sorted",
  ];
  r.push({
    frame: arrayFrame(a, { 0: "sorted" }),
    note: "A list of one item is already sorted.",
    line: 0,
  });
  for (let i = 1; i < n; i++) {
    let j = i;
    r.push({
      frame: arrayFrame(a, { ...markAll(range(0, i - 1), "sorted"), [i]: "active" }, [
        { label: "i", index: i },
      ]),
      note: `Insert ${a[i]} into the sorted part on its left.`,
      line: 1,
    });
    while (j > 0) {
      const sorted = markAll(range(0, i), "sorted");
      r.push({
        frame: arrayFrame(a, { ...sorted, [j - 1]: "compare", [j]: "compare" }, [
          { label: "j", index: j },
        ]),
        note: `Is ${a[j - 1]} greater than ${a[j]}?`,
        line: 2,
      });
      if (a[j - 1] <= a[j]) break;
      [a[j - 1], a[j]] = [a[j], a[j - 1]];
      r.push({
        frame: arrayFrame(a, { ...sorted, [j - 1]: "swap", [j]: "swap" }, [
          { label: "j", index: j - 1 },
        ]),
        note: "Yes: swap them.",
        line: 3,
      });
      j--;
    }
    r.push({
      frame: arrayFrame(a, markAll(range(0, i), "sorted")),
      note: `The first ${i + 1} items are sorted.`,
      line: 5,
    });
  }
  r.push({
    frame: arrayFrame(a, markAll(range(0, n - 1), "sorted")),
    note: "The list is sorted.",
    line: 5,
  });
  return { algorithm: "insertion-sort", pseudo, steps: r.steps };
}

export function mergeSort(input: string): Trace {
  const a = parseNumbers(input, 2, 12);
  const r = new Recorder();
  const pseudo = [
    "mergeSort(lo, hi):",
    "  if lo == hi: return",
    "  mid = (lo + hi) / 2",
    "  mergeSort(lo, mid) and mergeSort(mid + 1, hi)",
    "  merge the two sorted halves into a buffer",
    "  copy the buffer back into a[lo..hi]",
  ];
  r.push({ frame: arrayFrame(a), note: "Start with the unsorted list.", line: 0 });
  const sortRange = (lo: number, hi: number) => {
    if (lo === hi) return;
    const mid = Math.floor((lo + hi) / 2);
    r.push({
      frame: arrayFrame(a, markAll(range(lo, hi), "active")),
      note: `Split positions ${lo}..${hi} into ${lo}..${mid} and ${mid + 1}..${hi}.`,
      line: 2,
    });
    sortRange(lo, mid);
    sortRange(mid + 1, hi);
    let i = lo;
    let j = mid + 1;
    const out: number[] = [];
    const view = (extra: Marks, note: string, line: number) =>
      r.push({
        frame: arrayFrame(
          a,
          { ...markAll(range(lo, hi), "active"), ...extra },
          [
            ...(i <= mid ? [{ label: "L", index: i }] : []),
            ...(j <= hi ? [{ label: "R", index: j }] : []),
          ],
          [],
          { label: "merge buffer", values: [...out] },
        ),
        note,
        line,
      });
    while (i <= mid && j <= hi) {
      view({ [i]: "compare", [j]: "compare" }, `Compare ${a[i]} (left) and ${a[j]} (right).`, 4);
      if (a[i] <= a[j]) out.push(a[i++]);
      else out.push(a[j++]);
      view({}, `Take ${out[out.length - 1]} into the buffer.`, 4);
    }
    while (i <= mid) {
      out.push(a[i++]);
      view({}, `The right half is used up: take ${out[out.length - 1]} from the left.`, 4);
    }
    while (j <= hi) {
      out.push(a[j++]);
      view({}, `The left half is used up: take ${out[out.length - 1]} from the right.`, 4);
    }
    for (let k = 0; k < out.length; k++) a[lo + k] = out[k];
    r.push({
      frame: arrayFrame(a, markAll(range(lo, hi), "sorted")),
      note: `Copy the buffer back: positions ${lo}..${hi} are sorted.`,
      line: 5,
    });
  };
  sortRange(0, a.length - 1);
  r.push({
    frame: arrayFrame(a, markAll(range(0, a.length - 1), "sorted")),
    note: "The list is sorted.",
    line: 0,
  });
  return { algorithm: "merge-sort", pseudo, steps: r.steps };
}

// ------------------------------------------------------------------ searching

export function binarySearch(input: string, param: string): Trace {
  const a = parseNumbers(input, 2, 16);
  requireSorted(a);
  const target = Number(param.trim());
  if (param.trim() === "" || !Number.isInteger(target))
    throw new VizInputError("Enter a whole number to search for.");
  const r = new Recorder();
  const pseudo = [
    "lo = 0, hi = n - 1",
    "while lo <= hi:",
    "  mid = (lo + hi) / 2",
    "  if a[mid] == target: found at mid",
    "  else if a[mid] < target: lo = mid + 1",
    "  else: hi = mid - 1",
    "the target is not in the list",
  ];
  const view = (lo: number, hi: number, mid: number | null, extra: Marks = {}) => {
    const marks: Marks = {};
    for (let i = 0; i < a.length; i++) marks[i] = i >= lo && i <= hi ? "active" : "dim";
    if (mid !== null) marks[mid] = "compare";
    return arrayFrame(
      a,
      { ...marks, ...extra },
      [
        { label: "lo", index: Math.min(lo, a.length - 1) },
        ...(mid !== null ? [{ label: "mid", index: mid }] : []),
        { label: "hi", index: Math.max(hi, 0) },
      ],
      [{ label: "target", value: String(target) }],
    );
  };
  let lo = 0;
  let hi = a.length - 1;
  r.push({ frame: view(lo, hi, null), note: `Search for ${target} in the whole list.`, line: 0 });
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    r.push({ frame: view(lo, hi, mid), note: `Look at the middle item, ${a[mid]}.`, line: 2 });
    if (a[mid] === target) {
      r.push({
        frame: view(lo, hi, mid, { [mid]: "found" }),
        note: `${a[mid]} equals the target: found at position ${mid}.`,
        line: 3,
      });
      return { algorithm: "binary-search", pseudo, steps: r.steps };
    }
    if (a[mid] < target) {
      lo = mid + 1;
      r.push({
        frame: view(lo, hi, null),
        note: `${a[mid]} is less than ${target}: discard the left half.`,
        line: 4,
      });
    } else {
      hi = mid - 1;
      r.push({
        frame: view(lo, hi, null),
        note: `${a[mid]} is greater than ${target}: discard the right half.`,
        line: 5,
      });
    }
  }
  r.push({
    frame: arrayFrame(
      a,
      markAll(range(0, a.length - 1), "dim"),
      [],
      [{ label: "target", value: String(target) }],
    ),
    note: `The range is empty: ${target} is not in the list.`,
    line: 6,
  });
  return { algorithm: "binary-search", pseudo, steps: r.steps };
}

export function twoPointers(input: string, param: string): Trace {
  const a = parseNumbers(input, 2, 16);
  requireSorted(a);
  const target = Number(param.trim());
  if (param.trim() === "" || !Number.isInteger(target))
    throw new VizInputError("Enter a whole number for the target sum.");
  const r = new Recorder();
  const pseudo = [
    "left = 0, right = n - 1",
    "while left < right:",
    "  sum = a[left] + a[right]",
    "  if sum == target: found the pair",
    "  else if sum < target: left = left + 1",
    "  else: right = right - 1",
    "no two items add up to the target",
  ];
  const view = (left: number, right: number, marks: Marks, sum: number | null) =>
    arrayFrame(
      a,
      marks,
      [
        { label: "left", index: left },
        { label: "right", index: right },
      ],
      [
        { label: "target", value: String(target) },
        ...(sum !== null ? [{ label: "sum", value: String(sum) }] : []),
      ],
    );
  let left = 0;
  let right = a.length - 1;
  r.push({
    frame: view(left, right, {}, null),
    note: `Find two items that add up to ${target}.`,
    line: 0,
  });
  while (left < right) {
    const sum = a[left] + a[right];
    r.push({
      frame: view(left, right, { [left]: "compare", [right]: "compare" }, sum),
      note: `${a[left]} + ${a[right]} = ${sum}.`,
      line: 2,
    });
    if (sum === target) {
      r.push({
        frame: view(left, right, { [left]: "found", [right]: "found" }, sum),
        note: `${sum} equals the target: the pair is ${a[left]} and ${a[right]}.`,
        line: 3,
      });
      return { algorithm: "two-pointers", pseudo, steps: r.steps };
    }
    if (sum < target) {
      left++;
      r.push({
        frame: view(left, right, {}, sum),
        note: `${sum} is less than ${target}: move the left pointer right to get a bigger sum.`,
        line: 4,
      });
    } else {
      right--;
      r.push({
        frame: view(left, right, {}, sum),
        note: `${sum} is greater than ${target}: move the right pointer left to get a smaller sum.`,
        line: 5,
      });
    }
  }
  r.push({
    frame: arrayFrame(a, {}, [], [{ label: "target", value: String(target) }]),
    note: "The pointers met: no two items add up to the target.",
    line: 6,
  });
  return { algorithm: "two-pointers", pseudo, steps: r.steps };
}

export function slidingWindow(input: string, param: string): Trace {
  const a = parseNumbers(input, 2, 16);
  const k = Number(param.trim());
  if (param.trim() === "" || !Number.isInteger(k) || k < 1 || k > a.length)
    throw new VizInputError(`The window size must be a whole number from 1 to ${a.length}.`);
  const r = new Recorder();
  const pseudo = [
    "sum = a[0] + ... + a[k-1]; best = sum",
    "for each next position:",
    "  sum = sum + (item entering) - (item leaving)",
    "  best = max(best, sum)",
    "the answer is best",
  ];
  const win = (start: number, extra: Marks = {}) =>
    markAll(range(start, start + k - 1), "window", { ...extra });
  let sum = a.slice(0, k).reduce((x, y) => x + y, 0);
  let best = sum;
  let bestStart = 0;
  const stats = () => [
    { label: "window sum", value: String(sum) },
    { label: "best", value: String(best) },
  ];
  r.push({
    frame: arrayFrame(a, win(0), [], stats()),
    note: `Sum the first ${k} items: ${sum}.`,
    line: 0,
  });
  for (let start = 1; start + k <= a.length; start++) {
    const entering = a[start + k - 1];
    const leaving = a[start - 1];
    sum = sum + entering - leaving;
    r.push({
      frame: arrayFrame(
        a,
        win(start, { [start + k - 1]: "active", [start - 1]: "dim" }),
        [],
        stats(),
      ),
      note: `Slide right: add ${entering}, drop ${leaving}. The sum is now ${sum}.`,
      line: 2,
    });
    if (sum > best) {
      best = sum;
      bestStart = start;
      r.push({
        frame: arrayFrame(a, win(start), [], stats()),
        note: `${sum} beats the previous best.`,
        line: 3,
      });
    }
  }
  r.push({
    frame: arrayFrame(a, markAll(range(bestStart, bestStart + k - 1), "found"), [], stats()),
    note: `The largest sum of ${k} items in a row is ${best}.`,
    line: 4,
  });
  return { algorithm: "sliding-window", pseudo, steps: r.steps };
}

// ------------------------------------------------------------------ stack

const PAIRS: Record<string, string> = { ")": "(", "]": "[", "}": "{" };

export function validParentheses(input: string): Trace {
  const text = input.trim();
  if (!/^[()[\]{}]{1,24}$/.test(text))
    throw new VizInputError("Enter 1 to 24 brackets from ( ) [ ] { }.");
  const chars = [...text];
  const r = new Recorder();
  const pseudo = [
    "for each character c:",
    "  if c opens a bracket: push c",
    "  else if the stack is empty or its top does not match c: unbalanced",
    "  else: pop the top",
    "balanced if the stack is empty at the end",
  ];
  const stack: string[] = [];
  const frame = (at: number, verdict?: "balanced" | "unbalanced") => ({
    kind: "stack" as const,
    chars,
    at,
    stack: [...stack],
    ...(verdict ? { verdict } : {}),
  });
  r.push({ frame: frame(-1), note: "Start with an empty stack.", line: 0 });
  for (let i = 0; i < chars.length; i++) {
    const c = chars[i];
    if (c === "(" || c === "[" || c === "{") {
      stack.push(c);
      r.push({ frame: frame(i), note: `"${c}" opens a bracket: push it.`, line: 1 });
      continue;
    }
    if (stack.length === 0) {
      r.push({
        frame: frame(i, "unbalanced"),
        note: `"${c}" closes a bracket but the stack is empty: unbalanced.`,
        line: 2,
      });
      return { algorithm: "valid-parentheses", pseudo, steps: r.steps };
    }
    const top = stack[stack.length - 1];
    if (top !== PAIRS[c]) {
      r.push({
        frame: frame(i, "unbalanced"),
        note: `"${c}" does not match the top of the stack ("${top}"): unbalanced.`,
        line: 2,
      });
      return { algorithm: "valid-parentheses", pseudo, steps: r.steps };
    }
    stack.pop();
    r.push({ frame: frame(i), note: `"${c}" matches "${top}": pop it.`, line: 3 });
  }
  const balanced = stack.length === 0;
  r.push({
    frame: frame(chars.length, balanced ? "balanced" : "unbalanced"),
    note: balanced
      ? "The stack is empty: the brackets are balanced."
      : "Opening brackets are left on the stack: unbalanced.",
    line: 4,
  });
  return { algorithm: "valid-parentheses", pseudo, steps: r.steps };
}

// ------------------------------------------------------------------ graph

export function parseEdges(text: string): [number, number][] {
  const edges: [number, number][] = [];
  for (const part of text.split(",")) {
    const m = /^\s*([0-7])-([0-7])\s*$/.exec(part);
    if (!m) throw new VizInputError("Enter edges like 0-1, 0-2, 1-3 using nodes 0 to 7.");
    const a = Number(m[1]);
    const b = Number(m[2]);
    if (a !== b && !edges.some(([x, y]) => x === Math.min(a, b) && y === Math.max(a, b)))
      edges.push([Math.min(a, b), Math.max(a, b)]);
  }
  if (edges.length === 0)
    throw new VizInputError("Add at least one edge between two different nodes.");
  return edges;
}

export function bfs(input: string, param: string): Trace {
  const edges = parseEdges(input);
  const start = Number(param.trim());
  if (!/^[0-7]$/.test(param.trim()))
    throw new VizInputError("The start node must be a number from 0 to 7.");
  const ids = [...new Set([start, ...edges.flat()])].sort((x, y) => x - y);
  if (!edges.flat().includes(start))
    throw new VizInputError("The start node must be part of at least one edge.");
  const adj = new Map<number, number[]>(ids.map((id) => [id, []]));
  for (const [x, y] of edges) {
    adj.get(x)!.push(y);
    adj.get(y)!.push(x);
  }
  adj.forEach((list) => list.sort((p, q) => p - q));
  const r = new Recorder();
  const pseudo = [
    "queue = [start]; mark start as seen",
    "while the queue is not empty:",
    "  node = dequeue",
    "  visit node",
    "  for each unseen neighbour, smallest first: mark seen and enqueue it",
    "the order of visits is the result",
  ];
  const state = new Map<number, NodeState>(ids.map((id) => [id, "unseen"]));
  const queue: number[] = [];
  const order: number[] = [];
  const frame = (): GraphFrame => ({
    kind: "graph",
    nodes: ids.map((id) => ({ id, state: state.get(id)! })),
    edges,
    queue: [...queue],
    order: [...order],
  });
  state.set(start, "queued");
  queue.push(start);
  r.push({ frame: frame(), note: `Start at node ${start}: put it in the queue.`, line: 0 });
  while (queue.length > 0) {
    const node = queue.shift()!;
    order.push(node);
    state.set(node, "visiting");
    r.push({
      frame: frame(),
      note: `Take node ${node} from the front of the queue and visit it.`,
      line: 2,
    });
    for (const next of adj.get(node)!) {
      if (state.get(next) === "unseen") {
        state.set(next, "queued");
        queue.push(next);
        r.push({
          frame: frame(),
          note: `Neighbour ${next} has not been seen: put it in the queue.`,
          line: 4,
        });
      }
    }
    state.set(node, "done");
    r.push({ frame: frame(), note: `Finished with node ${node}.`, line: 1 });
  }
  r.push({ frame: frame(), note: `Visit order: ${order.join(", ")}.`, line: 5 });
  return { algorithm: "bfs", pseudo, steps: r.steps };
}

// ------------------------------------------------------------------ dynamic programming

export function fibonacciDp(input: string): Trace {
  const text = input.trim();
  if (!/^\d{1,2}$/.test(text) || Number(text) > 30)
    throw new VizInputError("Enter a whole number from 0 to 30.");
  const n = Number(text);
  const r = new Recorder();
  const pseudo = [
    "dp[0] = 0, dp[1] = 1",
    "for i from 2 to n:",
    "  dp[i] = dp[i-1] + dp[i-2]",
    "the answer is dp[n]",
  ];
  const dp: (number | null)[] = Array.from({ length: n + 1 }, () => null);
  dp[0] = 0;
  if (n >= 1) dp[1] = 1;
  const filled = (): Marks => {
    const m: Marks = {};
    dp.forEach((v, i) => (m[i] = v === null ? "dim" : "none"));
    return m;
  };
  r.push({
    frame: arrayFrame(dp, filled()),
    note: `Base cases: dp[0] = 0${n >= 1 ? " and dp[1] = 1" : ""}.`,
    line: 0,
  });
  for (let i = 2; i <= n; i++) {
    dp[i] = (dp[i - 1] as number) + (dp[i - 2] as number);
    r.push({
      frame: arrayFrame(
        dp,
        { ...filled(), [i - 1]: "compare", [i - 2]: "compare", [i]: "active" },
        [{ label: "i", index: i }],
      ),
      note: `dp[${i}] = dp[${i - 1}] + dp[${i - 2}] = ${dp[i - 1]} + ${dp[i - 2]} = ${dp[i]}.`,
      line: 2,
    });
  }
  r.push({
    frame: arrayFrame(dp, { ...filled(), [n]: "found" }),
    note: `Fibonacci(${n}) = ${dp[n]}.`,
    line: 3,
  });
  return { algorithm: "fibonacci-dp", pseudo, steps: r.steps };
}
