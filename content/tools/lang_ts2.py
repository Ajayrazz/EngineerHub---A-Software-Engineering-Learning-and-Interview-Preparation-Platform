"""Deep version of the TypeScript lesson (reuses verified programs from lang_ts.py, adds new ones)."""
from courses_a import Q, L
from lang_ts import TS as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "typescript", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 narrowing,2 strings/arrays,3 interfaces,4 heap,5 maps,6 sorting,7 template

TYPES = C("""// Inference, literal types, unions and structural typing
let count = 5;                              // inferred number
const mode = "fast";                        // literal type "fast"
type Dir = "up" | "down" | "left" | "right";
const d: Dir = "left";
// const bad: Dir = "diagonal";             // compile error: not assignable to Dir

function describe(x: number | string | null): string {
  if (x === null) return "nothing";
  if (typeof x === "number") return `number ${x.toFixed(1)}`;   // narrowed to number
  return `text ${x.toUpperCase()}`;                              // narrowed to string
}
console.log(describe(3), describe("hi"), describe(null));

interface Named { name: string }
interface Aged { age: number }
type Person = Named & Aged;                  // intersection: has both
const p: Person = { name: "Asha", age: 30 };
function greet(n: Named): string { return `hi ${n.name}`; }
console.log(greet(p));                       // structural typing: Person has everything Named needs

type Pt = { readonly x: number; readonly y: number; label?: string };
const pt: Pt = { x: 1, y: 2 };
// pt.x = 5;                                 // compile error: readonly
console.log(pt.label ?? "no label", Object.keys(pt));

type Shape = { kind: "circle"; r: number } | { kind: "rect"; w: number; h: number };
function area(s: Shape): number {
  switch (s.kind) {
    case "circle": return Math.PI * s.r * s.r;
    case "rect": return s.w * s.h;
    default: { const _never: never = s; return _never; }   // compile error if a case is missing
  }
}
console.log(area({ kind: "rect", w: 3, h: 4 }));          // 12

const nums = [1, 2, 3] as const;             // readonly tuple of literal types
type First = typeof nums[0];                 // 1
const first: First = 1;
console.log(first, nums.length);

type Mutable = Partial<Pt>;                  // utility types: Partial, Required, Readonly, Pick, Omit, Record
const rec: Record<string, number> = { a: 1, b: 2 };
console.log(rec["a"] + rec["b"], count, mode);""", "Inference, unions, narrowing, intersection, structural typing, readonly, discriminated unions and utility types")

GENERICS = C("""function maxBy<T>(items: readonly T[], key: (t: T) => number): T | undefined {
  let best: T | undefined;
  let bestKey = -Infinity;
  for (const it of items) {
    const k = key(it);
    if (k > bestKey) { bestKey = k; best = it; }
  }
  return best;
}
console.log(maxBy([3, 9, 4], x => x), maxBy(["a", "ccc", "bb"], s => s.length), maxBy([], (x: number) => x));   // 9 ccc undefined

function swap<T>(a: T[], i: number, j: number): void { [a[i], a[j]] = [a[j], a[i]]; }
const arr = ["x", "y", "z"]; swap(arr, 0, 2);
console.log(arr);                            // [ 'z', 'y', 'x' ]

interface HasLength { length: number }
function longest<T extends HasLength>(a: T, b: T): T { return a.length >= b.length ? a : b; }   // constraint
console.log(longest("abc", "de"), longest([1], [1, 2]));

class Stack<T> {
  private items: T[] = [];
  push(x: T): void { this.items.push(x); }
  pop(): T | undefined { return this.items.pop(); }
  peek(): T | undefined { return this.items[this.items.length - 1]; }
  get size(): number { return this.items.length; }
}
const st = new Stack<number>();
st.push(1); st.push(2);
console.log(st.pop(), st.peek(), st.size);   // 2 1 1
// st.push("a");                             // compile error: string is not number

function toMap<K extends string | number, V>(pairs: ReadonlyArray<readonly [K, V]>): Map<K, V> {
  return new Map(pairs);
}
console.log(toMap([["a", 1], ["b", 2]]).get("b"));   // 2

const x: unknown = JSON.parse('{"n": 5}');
if (typeof x === "object" && x !== null && "n" in x) console.log((x as { n: number }).n);   // 5""", "Generics, constraints, a generic Stack and the safe unknown type")

GRAPH = C("""interface Edge { to: number; w: number }
const n = 6;
const edges: ReadonlyArray<readonly [number, number]> = [[0, 1], [0, 2], [1, 3], [2, 3], [3, 4], [2, 5]];
const adj: number[][] = Array.from({ length: n }, () => []);
for (const [u, v] of edges) { adj[u].push(v); adj[v].push(u); }

const dist: number[] = new Array<number>(n).fill(-1);
const queue: number[] = [0];
let head = 0;
dist[0] = 0;
while (head < queue.length) {
  const u = queue[head++];
  for (const v of adj[u]) {
    if (dist[v] === -1) { dist[v] = dist[u] + 1; queue.push(v); }
  }
}
console.log(dist);                           // [ 0, 1, 1, 2, 3, 2 ]

const weighted: Edge[][] = Array.from({ length: 3 }, () => []);
weighted[0].push({ to: 1, w: 4 });
console.log(weighted[0][0].w);               // 4""", "A typed adjacency list and breadth-first search")

ERR = C("""type Result<T> = { ok: true; value: T } | { ok: false; error: string };

function parsePositive(text: string): Result<number> {
  const t = text.trim();
  if (t === "") return { ok: false, error: "empty" };
  const v = Number(t);
  if (!Number.isInteger(v)) return { ok: false, error: "not an integer" };
  if (v <= 0) return { ok: false, error: `not positive: ${v}` };
  return { ok: true, value: v };
}
for (const s of [" 42 ", "abc", "-5", ""]) {
  const r = parsePositive(s);
  console.log(r.ok ? r.value : r.error);     // 42, not an integer, not positive: -5, empty
}

function fail(msg: string): never { throw new Error(msg); }   // never returns
try { fail("boom"); } catch (e: unknown) {
  console.log(e instanceof Error ? e.message : String(e));      // catch variables are unknown in strict mode
}

const a: number[] = [];
const first: number | undefined = a[0];       // with noUncheckedIndexedAccess this is the honest type
console.log(first === undefined ? "empty" : first);
console.log(Math.max(...a, -Infinity), a.at(0), a.length === 0);   // -Infinity undefined true

function assertNever(x: never): never { throw new Error("unexpected " + String(x)); }
type Color = "red" | "green";
function code(c: Color): number { if (c === "red") return 1; if (c === "green") return 2; return assertNever(c); }
console.log(code("green"));                   // 2""", "A Result type, never, unknown in catch, empty input and exhaustive checks")

TS2 = L("typescript-for-dsa", "TypeScript for DSA", "free", 75, "", blocks=[
M("""# TypeScript for Data Structures and Algorithms: a complete guide

TypeScript is **JavaScript with a static type system**. Everything in the *JavaScript for DSA* lesson (the engine, numbers, `Array`, `Map`, `Set`, sorting, the missing heap) is still true, because TypeScript is **erased** to plain JavaScript before running. What TypeScript adds is a layer of checking at compile time: it refuses programs that pass a string where a number is expected, forget the `undefined` case, or misspell a field. This lesson explains that type layer in depth and then shows how each data structure looks in typed code, with the **time and space complexity of every operation**. Every program was **type-checked with `tsc --strict`** and run.

## 1. What TypeScript is, and why it behaves the way it does

TypeScript (TS) is a **superset** of JavaScript: every valid JavaScript program is valid TypeScript. The compiler `tsc` (or a faster tool such as `esbuild`, `swc`, or Node's built-in type stripping) checks types and then **removes** them, producing ordinary JavaScript. Types therefore **cost nothing at run time**, and they **disappear at run time**: you cannot ask "is this an `interface Person`?" while the program runs, only inspect real values (`typeof`, `in`, `instanceof`, a tag field).

| Property | What it means | Consequence |
|---|---|---|
| **Static, structural typing** | Types are checked before running; two types are compatible when their *shapes* match | You rarely need to declare that a class "implements" something; matching fields is enough |
| **Gradual** | You can opt out with `any` | `any` switches checking off: avoid it |
| **Erased** | Types are not present at run time | No type-based dispatch; use tagged unions |
| **Same runtime as JavaScript** | Floats, `NaN`, coercion, `sort()` as strings | All JS pitfalls remain |
| **`strict` mode** | A family of checks (`strictNullChecks`, `noImplicitAny`, ...) | Forces you to handle `undefined`/`null` |

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| Types document intent and catch bugs before running | Extra syntax; types are only as good as you write them |
| `strict` null checks remove the most common JS runtime error | Same runtime pitfalls as JavaScript |
| Excellent for modelling nodes, edges, results with unions | No built-in heap, deque or sorted map |
| Very common in front-end and Node.js jobs | Generic type errors can be long |

## 2. Program structure and typed input

A TypeScript file is a JavaScript file with annotations. Run it with `ts-node`, `tsx`, Node's `--experimental-strip-types` (Node 22.6+, which supports *erasable* syntax only: no enums, no parameter properties), or compile with `tsc` and run the output. Enable `"strict": true` in `tsconfig.json`. Reading input is the same as in JavaScript; the annotation tells the compiler what you get back:
"""),
codes[0],
M("""**How it works.** `readFileSync(0, "utf8")` returns a `string`; `.split(...)` gives `string[]`; `.filter(Boolean)` keeps the type; `.map(Number)` converts to `number[]`. The annotation `const data: number[]` is optional here (inferred) but documents the contract. Total cost is O(length of the input).

## 3. The type system in depth

### 3.1 Primitive and built-in types

| Type | Meaning | Notes |
|---|---|---|
| `number` | any float (also integers) | exact integers only up to 2⁵³ - 1 |
| `bigint` | arbitrary-precision integer | `123n`; do not mix with `number` |
| `string`, `boolean` | as in JS | |
| `null`, `undefined` | the "no value" types | under `strict` they are **not** assignable to other types |
| `T[]`, `Array<T>` | arrays | `ReadonlyArray<T>` / `readonly T[]` forbids mutation |
| `[A, B]` | **tuple**: fixed length and types | `[number, string]`; named elements `[dist: number, node: number]` |
| `Map<K, V>`, `Set<T>` | typed collections | `map.get(k)` is `V | undefined` |
| `unknown` | "something, check before use" | the safe top type |
| `any` | opt out of checking | avoid |
| `never` | a value that cannot exist | for impossible branches and functions that always throw |
| `void` | no meaningful return value | |
| `object`, `{}` | any non-primitive / non-null value | rarely useful |

### 3.2 Inference and annotations
TypeScript **infers** types from initial values, so `let count = 5` is a `number` and `const mode = "fast"` has the **literal type** `"fast"`. Annotate **function parameters and return types** (the boundary of your code) and let local variables be inferred. Annotate when inference is too wide (`const a: number[] = []` instead of `never[]`).

### 3.3 Unions, intersections, literals and narrowing
* A **union** `A | B` is a value that is either type. Use it for "number or string", "result or error", "found or `undefined`".
* A **literal type** such as `"up"` restricts a value to specific constants; `type Dir = "up" | "down"` gives you an enum-like set without runtime cost.
* An **intersection** `A & B` has the members of both.
* **Narrowing:** inside `if (typeof x === "number")`, `if (x !== undefined)`, `if ("n" in x)`, `if (x instanceof Date)` or after checking a **tag** field, the compiler refines the union. A **discriminated union** (each variant has a literal `kind`) makes `switch` exhaustive, and a `never` check turns a forgotten case into a compile error.
* **Structural typing:** an object is acceptable wherever its *shape* matches, so `Person` works where `Named` is expected as long as it has a `name: string`.
* **`readonly`** properties and arrays cannot be modified through that type; `as const` freezes a literal to its narrowest readonly type. The utility types `Partial<T>`, `Required<T>`, `Readonly<T>`, `Pick<T, K>`, `Omit<T, K>`, `Record<K, V>` build new types from old ones.

Run the program, then uncomment each *compile error* line in your editor and read the message; that is the quickest way to learn the checker:
"""),
TYPES,
M("""### 3.4 Strict null checks: the feature that pays for TypeScript
With `strictNullChecks`, `null` and `undefined` are separate types. A function that may not find something returns `T | undefined`, and you **must** handle it before using the value (`if (x !== undefined)`, `x ?? fallback`, `x?.field`). `Map.get`, `Array.find`, `array[i]` with `noUncheckedIndexedAccess`, and `string.match` all return possibly-missing values, which is exactly where JavaScript programs crash at run time. The non-null assertion `x!` tells the compiler "trust me"; use it only when you can prove the value exists (for example right after checking `has`).

### 3.5 Numbers, overflow and conversions
The runtime is JavaScript's: **all numbers are 64-bit floats**. There is no integer overflow, but integers above **2⁵³ - 1 lose precision**, so use `bigint` for exact huge values. Division is floating point (`7 / 2 === 3.5`); use `Math.floor` or `Math.trunc` for integer division. `%` keeps the dividend's sign. Bitwise operators work on **32-bit** signed integers. Convert strings explicitly: `Number(s)`, `Number.parseInt(s, 10)`; `Number("")` is `0`, `Number("12px")` is `NaN`. Truthiness and equality rules are those of JavaScript: use `===`, and remember `NaN !== NaN`.

## 4. Operators and control flow

The operators and statements are those of JavaScript: arithmetic `+ - * / % **`, comparison `=== !== < <= > >=`, logical `&& || !`, **nullish coalescing** `??`, **optional chaining** `?.`, bitwise `& | ^ ~ << >> >>>`, spread/rest `...`, destructuring. Control flow: `if`, `switch`, `for`, `for...of` (values), `for...in` (keys), `while`, `do...while`, `break`, `continue`, labels. TypeScript makes these safer: `for (const x of arr)` gives `x` the element type; a `switch` over a union can be checked for exhaustiveness; `??` only replaces `null`/`undefined` (so `0 ?? 5` is `0`).

**Counting the cost** is identical to JavaScript: a loop over n items is O(n); hidden linear work inside loops (`includes`, `indexOf`, `shift`, `slice`, spread) makes it O(n²).

## 5. Functions and generics in depth

Annotate parameters and the return type: `function sum(a: number[]): number`. Optional parameters (`x?: number`), default values (`k = 3`), rest parameters (`...xs: number[]`), function types `(a: number, b: number) => number`, overloads, and arrow functions all work. As in JavaScript, arguments are **passed by sharing**: the callee receives a copy of the value, which for arrays and objects is a reference to the same object, so it can mutate but not rebind the caller's variable; `readonly` parameter types document and enforce "I will not mutate this".

### 5.1 Generics
A **generic** function or class works over many types while staying type-safe: `function maxBy<T>(items: readonly T[], key: (t: T) => number): T | undefined`. The type parameter is *inferred* from the call. **Constraints** (`T extends HasLength`) say what a type argument must support. Generic classes such as `Stack<T>` and `MinHeap<T>` let you write a data structure once and have the compiler stop you from pushing a `string` into a `Stack<number>`.
"""),
GENERICS,
M("""### 5.2 Recursion and stack depth
The runtime limit is the same as in JavaScript: roughly 10⁴ nested calls before `RangeError: Maximum call stack size exceeded`. Convert deep recursion to a loop with an explicit stack, and memoise with a `Map<string, number>` or an array.

## 6. Strings in depth

Strings are JavaScript strings: **immutable** UTF-16 sequences; `s[i]` is a one-character `string`. TypeScript types them as `string`, so a function taking a `string` rejects a `number` at compile time. The operations and costs are exactly those of JavaScript:

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length | `s.length` | O(1) | |
| Index | `s[i]`, `s.at(-1)` | O(1) | typed `string` (or `string | undefined` with `noUncheckedIndexedAccess`) |
| Char code | `s.charCodeAt(i)` | O(1) | letter index `s.charCodeAt(i) - 97` |
| Substring | `s.slice(a, b)` | O(b - a) | end exclusive |
| Concatenate | `s + t`, template literal | amortised cheap | build large strings with `join` |
| Compare | `s === t` | O(min(n, m)) | |
| Search | `indexOf`, `includes` | O(n·m) worst | |
| Split / join | `split`, `join` | O(n) | `split` returns `string[]` |
| Reverse | `[...s].reverse().join("")` | O(n) | |
| Space | | O(n) | |

**String literal types and template literal types** (`type Id = \\`user-${number}\\``) let the compiler check string *formats*, but that is rarely needed in algorithm problems.

## 7. Arrays in depth

A TypeScript array is a JavaScript array with an element type: `number[]`, `string[]`, `number[][]` for grids, `Array<[number, number]>` for pairs. The complexity table is the same as in JavaScript: index O(1), `push`/`pop` amortised O(1), `shift`/`unshift`/`splice` in the middle **O(n)**, `indexOf`/`includes` O(n), `sort` O(n log n) and **stable**, copying O(n). Use typed arrays (`Int32Array`, `Float64Array`) for fixed-size numeric data: contiguous and compact, 4 or 8 bytes per element. A `readonly number[]` parameter promises not to mutate. **`sort()` still sorts as strings by default**, so always write `(a, b) => a - b`. Grids: `Array.from({ length: r }, () => new Array<number>(c).fill(0))`; never `new Array(r).fill(new Array(c))`.

| Operation | Time | Space |
|---|---|---|
| `a[i]` read/write | O(1) | O(1) |
| `push` / `pop` | amortised O(1) / O(1) | O(1) |
| `shift` / `unshift` | **O(n)** | O(1) |
| `splice` in the middle | O(n) | O(k) |
| `indexOf` / `includes` / `find` | O(n) | O(1) |
| `slice` / spread copy | O(n) | O(n) |
| `sort` | O(n log n) | O(n) |
| `map` / `filter` | O(n) | O(n) |
"""),
codes[1],
codes[2],
M("""## 8. `Map` and `Set` in depth

`Map<K, V>` and `Set<T>` are the same hash tables as in JavaScript (insertion ordered, keys keep their type, objects compare by identity), now **typed**: `new Map<string, number>()` rejects `set("a", "x")`. `get(k)` has type `V | undefined`, which is the safe, honest type: handle the missing case.

| Operation | `Map` / `Set` | `Record<string, V>` / object |
|---|---|---|
| set / add | O(1) amortised | O(1) average |
| get / has | O(1) average | O(1) average |
| delete | O(1) average | O(1) average |
| size | O(1) | O(n) |
| iterate | O(n), insertion order | O(n) |
| space | O(n) | O(n) |

Keys that are arrays or objects are compared by **identity**; encode coordinates as a string `` `${r},${c}` `` or a number `r * cols + c`. A `Record<string, number>` is typed like an object but **all keys are strings**; prefer `Map<number, V>` for numeric keys. There is no built-in sorted map; keep a sorted array and binary search (O(log n) find, O(n) insert).
"""),
codes[5],
M("""## 9. Stacks, queues, deques and heaps (typed)

| Structure | Implementation | Operations | Time | Space |
|---|---|---|---|---|
| Stack | `T[]` with `push`/`pop`/`at(-1)` | push, pop, peek | O(1) each | O(n) |
| Queue | head-index class (`Queue<T>` below) | enqueue, dequeue | O(1) each (`shift` would be O(n)) | O(n) |
| Deque | two indexes over an array / linked list | both ends | O(1) each | O(n) |
| Heap | generic `MinHeap<T>` with a comparator | push, pop, peek | O(log n), O(log n), O(1) | O(n) |
| Sorted set | sorted `T[]` + binary search | find, insert | O(log n), O(n) | O(n) |

**How the heap works.** A complete binary tree stored in an array: element `i` has children `2i + 1`, `2i + 2` and parent `(i - 1) >> 1`. **Push** appends and *sifts up* (at most log n swaps). **Pop** removes the root, moves the last element to the root, and *sifts down* (at most log n swaps). **Peek** is the root. **Heapify** n items takes O(n). A **max**-heap just flips the comparator. The generic class below is typed so that `MinHeap<[number, number]>` accepts only `(distance, node)` pairs; note it avoids *parameter properties* so Node's type stripping can run it.
"""),
codes[4],
M("""## 10. Sorting, comparators and searching

A comparator has type `(a: T, b: T) => number`. Describe record shapes with an `interface` so that a misspelt field is a compile error, and chain keys with `||`: `(a, b) => b.age - a.age || a.name.localeCompare(b.name)`. `sort` is **stable**, sorts **in place**, and without a comparator converts elements to strings. Binary search is a hand-written `lo/hi` loop (no built-in); typing the array as `readonly number[]` documents that it is not modified.
"""),
codes[6],
M("""## 11. Objects, classes and the memory model

Classes add **type information** to JavaScript classes: field types, `private`/`protected`/`public`/`readonly` modifiers (enforced only at compile time, except `#private`), `implements` for interfaces, and generics. Objects are heap references; equality and hashing are by identity. The garbage collector frees unreachable objects.

### 11.1 Linked lists and trees
Model nodes with an `interface` (or class) whose links are **nullable**: `next: ListNode | null`. Strict null checks then force you to test `node !== null` before dereferencing, removing the classic "cannot read property of null" crash. Reaching the i-th node is O(i); inserting after a node you hold is O(1); recursion depth equals tree height.
"""),
codes[3],
M("""### 11.2 Graphs
An **adjacency list** `number[][]` (or `Edge[][]` with `{ to, w }` for weighted edges) uses O(V + E) space; an adjacency matrix `number[][]` uses O(V²). Breadth-first search with a head-index queue visits each vertex and edge once: **O(V + E) time, O(V) space**, shortest paths in an unweighted graph.
"""),
GRAPH,
M("""## 12. Errors and edge cases

TypeScript catches *some* edge cases at compile time (a possibly-`undefined` value, a missing union case) and none at run time (empty arrays, `NaN`, division by zero, deep recursion). With `strict` on, `catch (e)` gives `e` the type `unknown`: narrow it (`e instanceof Error`) before using `.message`. Model expected failures as **values** with a `Result<T>` union, and reserve `throw` for genuine programming errors.

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty array | `a[0]` is `undefined`; `Math.max(...[])` is `-Infinity` | check `a.length === 0` |
| Missing `Map` key | `get` is `V | undefined` | `?? 0`, or check `has` |
| `Number("")` is 0 | silent wrong value | trim and test for `""` |
| Huge integers | precision loss above 2⁵³ - 1 | `bigint`, `Number.isSafeInteger` |
| Forgotten union case | silent fall-through | `never` exhaustiveness check |
| `sort()` with no comparator | string order | `(a, b) => a - b` |
"""),
ERR,
M("""## 13. The master complexity cheat sheet

`n` is the number of elements. Running time is that of JavaScript; types add none.

| Operation | `T[]` | typed array | `Map<K,V>` / `Set<T>` | `Record` / object | `string` | `MinHeap<T>` |
|---|---|---|---|---|---|---|
| Access by index / key | O(1) | O(1) | O(1) avg | O(1) avg | O(1) | peek O(1) |
| Push / pop at end | O(1)* / O(1) | n/a | n/a | n/a | n/a | O(log n) |
| Shift / unshift (front) | **O(n)** | n/a | n/a | n/a | n/a | n/a |
| Insert / delete in the middle | O(n) | n/a | O(1) avg by key | O(1) avg | O(n) | n/a |
| Search by value | O(n) | O(n) | O(1) avg by key | O(1) avg by key | O(n·m) | O(n) |
| Min / max | O(n) | O(n) | O(n) | O(n) | O(n) | O(1) |
| Sort | O(n log n) | O(n log n) | n/a | n/a | via array | n/a |
| Iterate | O(n) | O(n) | O(n) | O(n) | O(n) | O(n) |
| Space | O(n) | 4 or 8 B per item | O(n), heavier | O(n), heavier | O(n) | O(n) |

\\* amortised or average.

**What to say in an interview:** "I'll keep a `Map<number, number>` from value to index. Each lookup and insert is O(1) on average, so one pass is O(n) time and O(n) extra space. The brute force is O(n²) time and O(1) space; I'm trading memory for time. And because `get` returns `number | undefined`, the compiler makes me handle the missing case."

## 14. Pitfalls and idioms checklist

1. Turn on `strict`; avoid `any` and the `!` assertion unless you can justify them.
2. `map.get(k)` is `V | undefined`: handle it with `??`, `if` or `?.`.
3. `sort()` without a comparator still sorts as strings.
4. Types are **erased**: you cannot test an interface at run time; use a tag field or `typeof`/`in`/`instanceof`.
5. `number` is a float: integers lose exactness above 2⁵³ - 1; use `bigint`.
6. `shift()` is O(n): use a head index.
7. Prefer `unknown` to `any` for untrusted data, then validate.
8. Use `readonly` for inputs you will not mutate.
9. Use discriminated unions plus a `never` check for exhaustive handling.
10. `catch (e)` is `unknown`: narrow before use.
11. Object keys are strings; use `Map` for numbers.
12. Do not use `enum` if you want Node's type stripping; use union literals or `as const` objects.
13. Equal arrays are not equal keys: encode them.
14. Recursion depth is about 10⁴: convert to loops.

## 15. A reusable solution template
"""),
codes[7],
M("""## 16. How TypeScript compares with the other languages in this chapter

| Topic | TypeScript | JavaScript | Java | C# |
|---|---|---|---|---|
| Typing | static, structural, erased | dynamic | static, nominal | static, nominal |
| Null safety | `strictNullChecks` | none | `Optional`, annotations | nullable reference types |
| Integers | float / `bigint` | same | 32/64-bit | 32/64-bit |
| Generics | yes (erased) | n/a | yes (erased) | yes (reified) |
| Heap | write your own | write your own | `PriorityQueue` | `PriorityQueue` |
| Runtime | JavaScript engine | same | JVM | .NET |

## 17. Practice plan

1. Convert your JavaScript solutions to TypeScript and fix every compiler error: each one is a bug you would otherwise ship.
2. Re-type the generic `MinHeap<T>` and `Queue<T>` until you can write them from memory.
3. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest), [Shortest Maze Path](/problems/shortest-maze-path) and [Course Order](/problems/course-order) in TypeScript, writing the type of every function first, then the time and space complexity.
"""),
], quiz=OLD["quiz"] + [
 Q("Why does a discriminated union with a `never` default help?", ["The compiler errors if you forget a case", "It makes code faster", "It removes types at run time", "It sorts the cases"], 0, "An unhandled variant is not assignable to never."),
 Q("In strict mode, `catch (e)` gives `e` the type:", ["unknown", "any", "Error", "string"], 0, "Narrow it before using it."),
 Q("Two TypeScript types are compatible when:", ["Their shapes match (structural typing)", "They have the same name", "They are declared in one file", "They are classes"], 0, "That is structural typing."),
 Q("What does `as const` do to an array literal?", ["Makes it a readonly tuple of literal types", "Makes it immutable at run time deeply", "Copies it", "Sorts it"], 0, "It is a compile-time narrowing, not a runtime freeze."),
])
