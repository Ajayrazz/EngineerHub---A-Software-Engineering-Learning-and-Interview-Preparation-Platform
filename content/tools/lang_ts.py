from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "typescript", code, cap)

TS = L("typescript-for-dsa", "TypeScript for DSA", "free", 30, "", blocks=[
M("""## Why TypeScript for interviews?

TypeScript is **JavaScript plus a static type system**. It compiles (or is type-stripped) to plain JavaScript, so everything you learned in *JavaScript for DSA* still applies: same runtime, same `Array`, `Map`, `Set`, same floating-point numbers, same missing heap. What you add is **types**, which catch whole classes of bugs before the code runs: wrong argument types, possibly-`undefined` values, misspelt fields, forgotten cases.

| Strengths | Weaknesses |
|---|---|
| Types document intent and catch bugs early | Extra syntax; types are erased at run time |
| `strict` mode forces you to handle `undefined` | Same runtime pitfalls as JavaScript (sort, floats, `shift`) |
| Excellent for modelling nodes and graphs with interfaces | No standard heap or deque either |
| Very common in front-end and Node.js jobs | Easy to cheat with `any`, which hides the benefit |

All programs below are **type-checked with `tsc --strict`** and run on Node.js. Compile settings assumed: `strict: true`, target ES2022.

## 1. Program skeleton and typed input
"""),
C("""import { readFileSync } from "node:fs";

const data: number[] = readFileSync(0, "utf8")
  .split(/\\s+/)
  .filter(Boolean)
  .map(Number);

const n: number = data[0];
const k: number = data[1];
const nums: number[] = data.slice(2, 2 + n);
const sum = nums.reduce((a, b) => a + b, 0);
console.log(`n=${n} k=${k} sum=${sum}`);""", "Typed input parsing"),
M("""## 2. Basic types

| Type | Meaning |
|---|---|
| `number` | any float (also used for integers); `bigint` for huge exact integers |
| `string`, `boolean` | as in JavaScript |
| `number[]` or `Array<number>` | array of numbers |
| `[number, string]` | **tuple**: fixed length and element types |
| `Map<K, V>`, `Set<T>` | typed collections |
| `null`, `undefined` | distinct types; under `strict` you must handle them |
| `unknown` | safe "anything" (must be narrowed before use) |
| `any` | turns checking off: avoid |
| `never` | a value that cannot exist (exhaustive checks) |

Annotate **function parameters and return types**; local variables can usually be inferred.

## 3. Strict null checks and narrowing

With `strict`, `map.get(key)` has type `V | undefined`, so you must check before using it. The compiler **narrows** the type inside `if (x !== undefined)`. This single feature removes the most common JavaScript runtime error.
"""),
C("""function firstIndexOf(a: number[], x: number): number | undefined {
  const i = a.indexOf(x);
  return i === -1 ? undefined : i;
}

const idx = firstIndexOf([4, 8, 15], 8);
// console.log(idx + 1);          // error under strict: idx may be undefined
if (idx !== undefined) console.log(idx + 1);      // narrowed to number: prints 2

const counts = new Map<string, number>();
for (const w of ["a", "b", "a"]) counts.set(w, (counts.get(w) ?? 0) + 1);   // ?? supplies a default
console.log(counts.get("a"), counts.get("z"));    // 2 undefined

const pair: [number, string] = [3, "three"];
const [num, label] = pair;
console.log(num, label);

type Result = { ok: true; value: number } | { ok: false; error: string };   // discriminated union
function safeDiv(a: number, b: number): Result {
  return b === 0 ? { ok: false, error: "divide by zero" } : { ok: true, value: a / b };
}
const r = safeDiv(7, 2);
console.log(r.ok ? r.value : r.error);            // 3.5""", "Narrowing, defaults with ??, tuples and a discriminated union"),
M("""## 4. Strings, arrays and 2-D grids

Strings behave exactly as in JavaScript (immutable, `s[i]` is a one-character string, `charCodeAt` for codes). Arrays are typed: `number[]`, `string[]`, `number[][]` for grids. Create independent rows with `Array.from`. **`Array.prototype.sort()` still sorts as strings by default**, so always pass a comparator for numbers.
"""),
C("""function isPalindrome(s: string): boolean {
  let i = 0, j = s.length - 1;
  while (i < j) if (s[i++] !== s[j--]) return false;
  return true;
}
console.log(isPalindrome("level"));                        // true

const counts: number[] = new Array<number>(26).fill(0);
for (const c of "banana") counts[c.charCodeAt(0) - 97]++;
console.log(counts[0], counts["n".charCodeAt(0) - 97]);    // 3 2

const grid: number[][] = Array.from({ length: 3 }, () => new Array<number>(4).fill(-1));
grid[1][2] = 7;
console.log(grid[1], grid.length, grid[0].length);

const a: number[] = [5, 2, 9, 1, 10];
a.sort((x, y) => x - y);                                    // numeric sort
console.log(a, [...a].reverse());

const readonlyList: readonly number[] = [1, 2, 3];          // cannot push or assign
console.log(readonlyList.length);""", "Typed strings, counting array, grids and sorting"),
M("""## 5. Interfaces, type aliases and generics

Use an `interface` (or `type`) to describe the shape of nodes, edges and records. **Generics** (`<T>`) let one data structure work for many element types while staying type-safe: `Stack<T>`, `MinHeap<T>`. A `?` marks an optional field; `readonly` forbids reassignment.
"""),
C("""interface ListNode {
  val: number;
  next: ListNode | null;
}
interface TreeNode {
  val: number;
  left: TreeNode | null;
  right: TreeNode | null;
}
interface Edge { to: number; weight: number }
type Graph = Edge[][];                       // adjacency list

function reverse(head: ListNode | null): ListNode | null {
  let prev: ListNode | null = null;
  while (head !== null) {
    const nxt: ListNode | null = head.next;
    head.next = prev;
    prev = head;
    head = nxt;
  }
  return prev;
}

const height = (t: TreeNode | null): number =>
  t === null ? 0 : 1 + Math.max(height(t.left), height(t.right));

let list: ListNode | null = { val: 1, next: { val: 2, next: { val: 3, next: null } } };
list = reverse(list);
const out: number[] = [];
for (let p = list; p !== null; p = p.next) out.push(p.val);
console.log(out);                            // [ 3, 2, 1 ]

const tree: TreeNode = { val: 1, left: { val: 2, left: { val: 3, left: null, right: null }, right: null }, right: null };
console.log(height(tree));                   // 3

const g: Graph = [[{ to: 1, weight: 4 }], [{ to: 0, weight: 4 }]];
console.log(g[0][0].weight);                 // 4""", "Interfaces for nodes and an adjacency-list type"),
M("""## 6. A generic heap, stack and queue

TypeScript has no built-in priority queue, so you write a typed one. The comparator makes it work for numbers, tuples or objects, and the type parameter prevents mixing types.
"""),
C("""class MinHeap<T> {
  private a: T[] = [];
  private readonly cmp: (x: T, y: T) => number;
  constructor(cmp: (x: T, y: T) => number) {
    this.cmp = cmp;
  }
  get size(): number { return this.a.length; }
  peek(): T | undefined { return this.a[0]; }
  push(x: T): void {
    const a = this.a;
    a.push(x);
    let i = a.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (this.cmp(a[i], a[p]) >= 0) break;
      [a[i], a[p]] = [a[p], a[i]];
      i = p;
    }
  }
  pop(): T | undefined {
    const a = this.a;
    if (a.length === 0) return undefined;
    const top = a[0];
    const last = a.pop() as T;
    if (a.length > 0) {
      a[0] = last;
      let i = 0;
      for (;;) {
        const l = 2 * i + 1, r = l + 1;
        let m = i;
        if (l < a.length && this.cmp(a[l], a[m]) < 0) m = l;
        if (r < a.length && this.cmp(a[r], a[m]) < 0) m = r;
        if (m === i) break;
        [a[i], a[m]] = [a[m], a[i]];
        i = m;
      }
    }
    return top;
  }
}

const h = new MinHeap<number>((x, y) => x - y);
[5, 1, 4].forEach(x => h.push(x));
console.log(h.pop(), h.peek(), h.size);                  // 1 4 2

const pq = new MinHeap<[dist: number, node: number]>((x, y) => x[0] - y[0]);   // Dijkstra-style pairs
pq.push([5, 1]); pq.push([2, 7]);
console.log(pq.pop()?.[1]);                               // 7

const stack: number[] = [1, 2];
stack.push(3);
console.log(stack.pop(), stack.at(-1));                   // 3 2

class Queue<T> {                                          // O(1) dequeue with a head index
  private items: T[] = [];
  private head = 0;
  enqueue(x: T): void { this.items.push(x); }
  dequeue(): T | undefined { return this.head < this.items.length ? this.items[this.head++] : undefined; }
  get length(): number { return this.items.length - this.head; }
}
const q = new Queue<string>();
q.enqueue("a"); q.enqueue("b");
console.log(q.dequeue(), q.length);                       // a 1""", "Generic MinHeap, stack and a head-index Queue"),
M("""## 7. Maps, sets and typed keys

`Map<K, V>` and `Set<T>` are typed: the compiler rejects a wrong key or value type. Coordinates need a **string key** (`` `${r},${c}` ``) or a numeric encoding (`r * cols + c`), because arrays and objects compare by identity.
"""),
C("""const nums: number[] = [2, 7, 11, 15];
const target = 9;
const seen = new Map<number, number>();                   // value -> index
for (let i = 0; i < nums.length; i++) {
  const j = seen.get(target - nums[i]);
  if (j !== undefined) { console.log(j, i); break; }
  seen.set(nums[i], i);
}

const groups = new Map<number, string[]>();
for (const w of ["to", "be", "or", "not"]) {
  const list = groups.get(w.length) ?? [];
  list.push(w);
  groups.set(w.length, list);
}
console.log(Object.fromEntries(groups));                  // { '2': [ 'to', 'be', 'or' ], '3': [ 'not' ] }

const visited = new Set<string>();
const key = (r: number, c: number): string => `${r},${c}`;
visited.add(key(0, 1));
console.log(visited.has(key(0, 1)), visited.has(key(1, 0)));   // true false

const dirs: ReadonlyArray<readonly [number, number]> = [[1, 0], [-1, 0], [0, 1], [0, -1]];
let neighbours = 0;
for (const [dr, dc] of dirs) if (0 <= 2 + dr && 2 + dr < 5 && 0 <= 2 + dc && 2 + dc < 5) neighbours++;
console.log(neighbours);                                   // 4""", "Typed Map and Set, coordinate keys and direction arrays"),
M("""## 8. Sorting and comparators with types

A comparator has the type `(a: T, b: T) => number`. For objects, describe the shape with an interface so that a misspelt field is a compile error. Chain keys with `||`.
"""),
C("""interface Person { name: string; age: number }
const people: Person[] = [{ name: "Asha", age: 30 }, { name: "Ben", age: 25 }, { name: "Chen", age: 30 }];
people.sort((a, b) => b.age - a.age || a.name.localeCompare(b.name));
console.log(people.map(p => p.name).join(" "));            // Asha Chen Ben

function lowerBound(a: readonly number[], x: number): number {
  let lo = 0, hi = a.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (a[mid] < x) lo = mid + 1; else hi = mid;
  }
  return lo;
}
console.log(lowerBound([1, 3, 5, 5, 7, 9], 5));            // 2

const intervals: Array<[number, number]> = [[5, 8], [1, 3], [2, 6]];
intervals.sort((p, q) => p[0] - q[0]);
console.log(intervals.map(([s]) => s).join(""));           // 125""", "Typed comparators and a typed lowerBound"),
M("""## 9. Complexity and pitfalls checklist

TypeScript does not change running time: the table in *JavaScript for DSA* applies unchanged (array `shift` O(n), `Map`/`Set` O(1) average, `sort` O(n log n)).

- Turn on **`strict`**; avoid `any` and the non-null assertion `!` unless you can justify it.
- `map.get(k)` returns `V | undefined`: handle it (`??`, `if`, `?.`).
- `sort()` without a comparator is still a string sort.
- Types are **erased at run time**: you cannot check a type with `typeof` on an interface; check fields or use discriminated unions.
- `number` is a float: integers lose exactness above 2⁵³ - 1; use `bigint` (`123n`).
- `readonly` arrays and tuples document that a function will not mutate its input.
- Narrow `null` before dereferencing: `node !== null`, or optional chaining `node?.next`.
- Prefer `unknown` to `any` for untrusted input, then validate.
- Generic functions: `function swap<T>(a: T[], i: number, j: number): void`.

## 10. A reusable solution template
"""),
C("""import { readFileSync } from "node:fs";

function twoSum(a: readonly number[], target: number): [number, number] | null {
  const seen = new Map<number, number>();
  for (let j = 0; j < a.length; j++) {
    const i = seen.get(target - a[j]);
    if (i !== undefined) return [i + 1, j + 1];
    if (!seen.has(a[j])) seen.set(a[j], j);
  }
  return null;
}

const data = readFileSync(0, "utf8").split(/\\s+/).filter(Boolean).map(Number);
const n = data[0];
const t = data[1];
const a = data.slice(2, 2 + n);
const r = twoSum(a, t);
console.log(r === null ? "-1" : `${r[0]} ${r[1]}`);""", "Template: typed pure function plus input and output"),
M("""## 11. What to practise next

1. Convert your JavaScript solutions to TypeScript and fix every compiler error: each one is a bug you would have shipped.
2. Re-type the generic `MinHeap<T>` until you can do it from memory.
3. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest) and [Shortest Maze Path](/problems/shortest-maze-path) in TypeScript.
"""),
], quiz=[
 Q("Under `strict`, `map.get(key)` has type:", ["V | undefined", "V", "any", "null"], 0, "You must handle the missing-key case."),
 Q("TypeScript types exist:", ["Only at compile time (erased at run time)", "At run time only", "Nowhere", "Only in browsers"], 0, "The generated JavaScript has no types."),
 Q("Which statement is true about `sort()` in TypeScript?", ["It still needs a numeric comparator", "It sorts numbers correctly by default", "It is typed numerically", "It is O(n)"], 0, "TypeScript does not change the runtime behaviour."),
 Q("A generic `MinHeap<T>` mainly provides:", ["One implementation for many types, with type safety", "Faster sorting", "A built-in priority queue", "Automatic memoisation"], 0, "The type parameter prevents mixing element types."),
 Q("Why prefer `unknown` to `any`?", ["It forces you to narrow before use", "It is faster", "It is shorter", "No reason"], 0, "any switches checking off."),
])
