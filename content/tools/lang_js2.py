"""Deep version of the JavaScript lesson (reuses verified programs from lang_js.py, adds new ones)."""
from courses_a import Q, L
from lang_js import JS as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "javascript", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 numbers,2 strings,3 arrays,4 map/set,5 heap,6 sorting,7 nodes,8 template

REFS = C("""function mutate(arr, n, str, obj) {
  arr.push(99);          // mutates the caller's array (same object)
  n += 1;                // local copy of a primitive
  str += "!";            // strings are immutable primitives
  obj.count = 1;         // mutates the caller's object
  obj = { count: 100 };  // rebinding the local name: the caller is unaffected
}
const a = [1, 2, 3]; let n = 5, s = "hi"; const o = { count: 0 };
mutate(a, n, s, o);
console.log(a, n, s, o);                       // [ 1, 2, 3, 99 ] 5 hi { count: 1 }

console.log([1] == [1], [1] === [1]);          // false false (objects compare by identity)
console.log(NaN === NaN, Number.isNaN(NaN), Object.is(NaN, NaN));   // false true true
console.log(0 === -0, Object.is(0, -0));       // true false
console.log(typeof null, typeof undefined, typeof [], typeof (() => 1));   // object undefined object function
console.log(null == undefined, null === undefined, "" == 0, "0" == false);  // true false true true

const shallow = [...[[1, 2], [3, 4]]];          // spread copies one level
shallow[0][0] = 100;
const deep = structuredClone([[1, 2], [3, 4]]); // true deep copy
console.log(shallow[0][0], deep[0][0]);        // 100 1

const frozen = Object.freeze([1, 2, 3]);
try { "use strict"; frozen.push(4); } catch (e) { console.log("cannot modify a frozen array"); }

const fns = [];
for (var i = 0; i < 3; i++) fns.push(() => i);  // var is function-scoped: all closures share one i
const fns2 = [];
for (let j = 0; j < 3; j++) fns2.push(() => j); // let is block-scoped: one j per iteration
console.log(fns.map(f => f()), fns2.map(f => f()));   // [ 3, 3, 3 ] [ 0, 1, 2 ]""", "Pass by sharing, equality traps, typeof, copies and var versus let closures")

HASH = C("""const m = new Map();
m.set(1, "number").set("1", "string").set(true, "bool");
console.log(m.size, m.get(1), m.get("1"));        // 3 number string (keys keep their type)

const o = {};
o[1] = "a"; o["1"] = "b";
console.log(Object.keys(o), o[1]);                // [ '1' ] b   (object keys become strings)

const keyArr = [1, 2];
const byIdentity = new Map([[keyArr, "x"]]);
console.log(byIdentity.get([1, 2]), byIdentity.get(keyArr));   // undefined x
const byValue = new Map([[JSON.stringify([1, 2]), "x"]]);
console.log(byValue.get(JSON.stringify([1, 2])));  // x  (serialise the key)

const seen = new Set();
const key = (r, c) => r * 1000 + c;               // numeric encoding of a coordinate
seen.add(key(2, 3));
console.log(seen.has(key(2, 3)), seen.has(key(3, 2)));   // true false

const lru = new Map();                              // Map remembers insertion order: a simple LRU cache
const get = k => { if (!lru.has(k)) return undefined; const v = lru.get(k); lru.delete(k); lru.set(k, v); return v; };
const put = (k, v, cap = 2) => { lru.delete(k); lru.set(k, v); if (lru.size > cap) lru.delete(lru.keys().next().value); };
put(1, 1); put(2, 2); get(1); put(3, 3);
console.log([...lru.keys()]);                       // [ 1, 3 ]""", "Map versus object keys, identity versus value keys, coordinate encoding and an LRU cache")

GRAPH = C("""const n = 6;
const edges = [[0, 1], [0, 2], [1, 3], [2, 3], [3, 4], [2, 5]];
const adj = Array.from({ length: n }, () => []);
for (const [u, v] of edges) { adj[u].push(v); adj[v].push(u); }   // undirected

const dist = new Array(n).fill(-1);
const queue = [0]; let head = 0;
dist[0] = 0;
while (head < queue.length) {
  const u = queue[head++];
  for (const v of adj[u]) if (dist[v] === -1) { dist[v] = dist[u] + 1; queue.push(v); }
}
console.log(dist);                                   // [ 0, 1, 1, 2, 3, 2 ]""", "A graph as an array of arrays and breadth-first search with a head index")

ERR = C("""function parsePositive(text) {
  const t = String(text).trim();
  if (t === "") return -1;                           // Number("") is 0, so reject empty text explicitly
  const v = Number(t);
  if (!Number.isInteger(v)) return -1;               // NaN and decimals are rejected
  if (v <= 0) throw new RangeError(`not positive: ${v}`);
  return v;
}
console.log(parsePositive(" 42 "), parsePositive("abc"), parsePositive(""));   // 42 -1 -1
try { parsePositive("-5"); } catch (e) { console.log(e.name, e.message); }

console.log(Number(""), Number(" "), Number("12px"), parseInt("12px", 10), parseInt("abc"));   // 0 0 NaN 12 NaN
console.log([].length === 0, Math.max(), Math.min(), Math.max(...[]));     // true -Infinity Infinity -Infinity
console.log([1, 2, 3].at(5), [1, 2, 3][5], [].pop(), [].shift());          // undefined undefined undefined undefined
try { null.x; } catch (e) { console.log(e.constructor.name); }             // TypeError
console.log(null?.x, ({}).x?.y, undefined ?? "default", 0 ?? "default", 0 || "default");  // undefined undefined default 0 default
try { JSON.parse("{bad"); } catch (e) { console.log(e.name); }              // SyntaxError
console.log(7 / 0, -7 / 0, 0 / 0, 2 ** 53 === 2 ** 53 + 1);                // Infinity -Infinity NaN true""", "Number conversion pitfalls, empty-input results, optional chaining and exceptions")

JS2 = L("javascript-for-dsa", "JavaScript for DSA", "free", 75, "", blocks=[
M("""# JavaScript for Data Structures and Algorithms: a complete guide

This lesson explains JavaScript **as you need it for algorithmic problem solving**: what it is and how the engine runs it, every primitive type and its quirks, equality and coercion, scope and functions, strings, arrays, `Map` and `Set`, and how to build the structures the language lacks (a heap, a deque) with the **time and space complexity of every operation**. The programs were run on Node.js.

## 1. What JavaScript is, and why it behaves the way it does

**JavaScript** was created for web browsers and now also runs servers (**Node.js**), mobile apps and tools. A **JavaScript engine** (V8 in Chrome and Node, SpiderMonkey in Firefox, JavaScriptCore in Safari) parses your code, starts running it in an interpreter and then **JIT-compiles** hot functions to machine code, so modern JavaScript is fast for such a dynamic language, typically within a small factor of Java. A program runs on a **single thread** with an **event loop** for asynchronous work; for algorithm problems you only need the synchronous part.

| Property | What it means | Consequence |
|---|---|---|
| **Dynamically typed** | Values have types, variables do not | Flexible, but type errors show up at run time; TypeScript adds static types |
| **One `number` type** | Every ordinary number is a 64-bit float | Integers are exact only up to 2⁵³ - 1; use `BigInt` beyond |
| **Reference semantics for objects** | Arrays and objects are references | Assignment aliases; copies must be explicit |
| **Weak and surprising coercion** | `==`, `+` convert types implicitly | Always use `===` and explicit conversions |
| **Garbage collected** | Unreachable objects are freed | No manual memory management |
| **Thin standard library** | No heap, deque or tree map built in | You write small helper classes |

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| Common in web, full-stack and Node roles | No priority queue, deque or sorted map in the language |
| Concise array methods (`map`, `filter`, `reduce`) | Default `sort()` compares as **strings** |
| `Map` and `Set` with insertion order | Precision loss above 2⁵³ - 1 |
| `BigInt` for exact huge integers | Type coercion surprises |

## 2. Program structure and input

A script runs top to bottom; there is no `main`. Statements end with `;` (optional but recommended). Modules use `import`/`export` (or `require` in classic Node). In Node you read all of standard input with `require("fs").readFileSync(0, "utf8")` (file descriptor 0). `console.log` prints; each call is a system call, so for many lines build an array and `join("\\n")` once.
"""),
codes[0],
M("""**How it works.** `readFileSync(0, "utf8")` returns the whole input as one string; `split(/\\s+/)` splits on any whitespace (the regular expression matches runs of spaces and newlines); `filter(Boolean)` removes empty strings (a leading or trailing separator creates one); `map(Number)` converts each token to a number. Total cost is O(length of the input), once.

## 3. Data types in depth

### 3.1 The seven primitive types and objects

| Type | Examples | Notes |
|---|---|---|
| `number` | `42`, `3.14`, `NaN`, `Infinity`, `-0` | 64-bit IEEE 754 float; the only number type |
| `bigint` | `123n`, `2n ** 100n` | arbitrary-precision integers; do not mix with `number` |
| `string` | `"abc"`, `'abc'`, `` `abc ${x}` `` | immutable UTF-16 text |
| `boolean` | `true`, `false` | |
| `undefined` | a variable never assigned | "no value yet" |
| `null` | explicit "no object" | `typeof null` is `"object"` (a historic bug) |
| `symbol` | `Symbol("id")` | unique identifiers; rare in algorithms |
| **objects** | `{}`, `[]`, functions, `Map`, `Set`, `Date` | everything else; compared **by reference** |

`typeof` reports the kind: `"number"`, `"string"`, `"boolean"`, `"undefined"`, `"object"` (also for arrays and null), `"function"`. Use `Array.isArray(x)` to detect arrays.

### 3.2 Numbers: floats, integers and precision
All numbers are 64-bit doubles: 1 sign bit, 11 exponent bits, 52 fraction bits. Consequences:

* **Integers are exact only up to `Number.MAX_SAFE_INTEGER` = 2⁵³ - 1 = 9,007,199,254,740,991.** Above that, neighbouring integers collapse: `2 ** 53 === 2 ** 53 + 1` is `true`. Sums or products of large values silently lose precision. Use **`BigInt`** (`123n`, `BigInt(x)`) for exact big integers; mix types only after converting.
* Decimals are approximate: `0.1 + 0.2 === 0.3` is `false`. Compare with a tolerance (`Math.abs(a - b) < 1e-9`).
* Special values: `Infinity`, `-Infinity`, `NaN` ("not a number"). `NaN !== NaN`; test with `Number.isNaN(x)`. Division by zero **does not throw**: `1 / 0 === Infinity`, `0 / 0` is `NaN`.
* There is no integer division: use `Math.floor(a / b)` (for non-negative) or `Math.trunc` (rounds toward zero). `%` keeps the sign of the dividend, so `-7 % 3 === -1`; a non-negative modulo is `((a % m) + m) % m`.
* **Bitwise operators convert to 32-bit signed integers**: `1 << 31` is negative, `2 ** 32 | 0` is `0`. Use them only for values under 2³¹; for larger values use arithmetic or `BigInt`.
* Conversions: `Number("42")`, `Number("")` is **0**, `Number("12px")` is `NaN`, `parseInt("12px", 10)` is 12 (always pass the radix), `String(12)`, `(12).toString(2)` gives binary text, `Math.round`, `Math.floor`, `Math.ceil`.

### 3.3 Truthiness, equality and coercion
**Falsy values** (treated as false in conditions): `false`, `0`, `-0`, `0n`, `""`, `null`, `undefined`, `NaN`. Everything else is truthy, including `"0"`, `[]` and `{}`. `if (!arr.length)` is the right empty test for an array; `if (!arr)` is not.

`==` **coerces** types and gives surprising results (`"" == 0`, `"0" == false`, `null == undefined` are all true); `===` compares without coercion. **Use `===` and `!==` always.** `Object.is(a, b)` is like `===` but treats `NaN` as equal to itself and distinguishes `0` from `-0`. Objects and arrays are equal only if they are the **same object**: `[1] === [1]` is `false`.

### 3.4 Variables and scope
`const` (cannot be rebound; the object it names can still be mutated), `let` (block scoped) and the legacy `var` (function scoped and hoisted, a source of bugs). Prefer `const`, then `let`. Declarations are **hoisted**: a `let`/`const` exists from the start of its block but cannot be used before its line (the "temporal dead zone"). The program below also shows the classic `var` closure bug.
"""),
codes[1],
REFS,
M("""## 4. Operators and control flow

| Group | Operators | Notes |
|---|---|---|
| Arithmetic | `+ - * / % **` | `+` also concatenates strings: `1 + "2"` is `"12"`, `"3" * "4"` is `12` |
| Comparison | `=== !== < <= > >=` | `"10" < "9"` is `true` (string comparison); compare numbers as numbers |
| Logical | `&& \\|\\| !` and `??` | `a \\|\\| b` returns `b` for any falsy `a`; **`a ?? b` only for `null`/`undefined`**, so `0 ?? 5` is `0` |
| Optional chaining | `a?.b`, `a?.[i]`, `f?.()` | stops with `undefined` if `a` is nullish |
| Bitwise | `& \\| ^ ~ << >> >>>` | 32-bit |
| Spread / rest | `[...a]`, `{...o}`, `(...args)` | copies one level; arguments |
| Destructuring | `const [a, b] = pair`, `const {x, y} = pt` | `[a, b] = [b, a]` swaps |

**Control flow.** `if / else if / else`, `switch` (strict comparison, falls through without `break`), `for (init; cond; step)`, `for (const x of iterable)` (values, for arrays, strings, Maps, Sets), `for (const k in obj)` (keys; avoid on arrays), `while`, `do while`, `break`, `continue`, labels. Array methods `forEach`, `map`, `filter`, `reduce`, `some`, `every`, `find`, `findIndex` take callbacks; they are O(n) each and cannot `break` early (use a loop when you need to stop).

**Counting the cost.** A loop over n items is O(n). Be careful with *hidden* linear work inside loops: `arr.includes(x)`, `arr.indexOf(x)`, `arr.shift()`, `arr.slice()` and `[...arr]` are O(n) each, so using them inside a loop makes it O(n²). Replace membership tests with a `Set`.

## 5. Functions in depth

Functions are values. Forms: declaration `function f(a, b) {}` (hoisted), expression `const f = function () {}`, **arrow** `(a, b) => a + b` (shorter, no own `this`). Features: default parameters `(a, b = 1)`, rest `...args`, destructured parameters. **Closures** capture variables from the surrounding scope (the `for (var ...)` example above is a closure bug). A function that does not `return` gives `undefined`. Arguments are **passed by sharing**: the parameter gets a copy of the value, which for objects is a reference to the same object, so the callee can mutate it but cannot rebind the caller's variable (the same rule as Java and Python). The recursion limit is roughly 10⁴ frames (`RangeError: Maximum call stack size exceeded`); convert deep recursion to a loop with an explicit stack. Memoise with a `Map`.

## 6. Strings in depth

A string is an **immutable** sequence of UTF-16 code units. `s[0] = "x"` silently does nothing. Characters outside the basic plane (many emoji) are two code units, which matters for `length` and indexing on Unicode text. `s[i]` returns a one-character *string* (there is no `char`).

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length | `s.length` | O(1) | property, not a method |
| Index | `s[i]`, `s.at(-1)`, `s.charAt(i)` | O(1) | out of range gives `undefined` / `""` |
| Char code / back | `s.charCodeAt(i)`, `String.fromCharCode(97)` | O(1) | letter index: `s.charCodeAt(i) - 97` |
| Substring | `s.slice(a, b)` | O(b - a) (engines may share memory) | end exclusive; negative counts from the end |
| Concatenate | `s + t`, `` `${s}${t}` `` | amortised cheap | engines build "ropes"; still avoid in huge nested loops |
| Join | `parts.join("")` | O(total) | the safe way to build large strings |
| Compare | `s === t`, `s < t` | O(min(n, m)) | `localeCompare` for locale order |
| Search | `s.indexOf(t)`, `s.includes(t)` | O(n·m) worst | |
| Split | `s.split(" ")`, `[...s]` | O(n) | `[...s]` iterates code points |
| Reverse | `[...s].reverse().join("")` | O(n) | |
| Case, trim | `toLowerCase`, `trim` | O(n) | |
| Space | | O(n) | 1 or 2 bytes per code unit in the engine |

`Array.from("abc")` or `[..."abc"]` gives characters; `for (const c of s)` iterates code points. Template literals `` `n=${n}` `` interpolate values.
"""),
codes[2],
M("""## 7. Arrays in depth

A JavaScript array is a **dynamic, resizable list** of values (it is actually an object with special behaviour). Engines such as V8 store arrays of small integers, of doubles, or of mixed values in contiguous memory with **specialised element kinds**, and switch representation if you add a different type, so keeping an array **homogeneous** and **dense** (no holes, filled in order) keeps it fast. `a[i]` is O(1). Appending grows the backing store geometrically, so `push` is **amortised O(1)**. For numeric work where speed matters use **typed arrays**: `new Int32Array(n)`, `new Float64Array(n)` are fixed-length, zero-filled, contiguous and allocation-light.

| Operation | Code | Time | Space | Notes |
|---|---|---|---|---|
| Read / write | `a[i]`, `a[i] = x` | O(1) | O(1) | writing past the end creates holes: avoid |
| Append / pop at end | `push`, `pop` | amortised O(1) / O(1) | O(1) | |
| Add / remove at front | `unshift`, `shift` | **O(n)** | O(1) | shifts all elements (engines optimise small cases, never rely on it) |
| Insert / delete in the middle | `splice(i, k, ...)` | O(n) | O(k) | |
| Slice / copy | `slice`, `[...a]`, `Array.from(a)` | O(n) | O(n) | shallow |
| Concatenate | `a.concat(b)`, `[...a, ...b]` | O(n + m) | O(n + m) | |
| Search | `indexOf`, `includes`, `find` | O(n) | O(1) | `includes` treats `NaN` as equal |
| Sort | `sort(cmp)` | O(n log n) | O(n) | stable (TimSort in V8) |
| Reverse | `reverse()` | O(n) | O(1) | in place |
| `map` / `filter` / `reduce` | | O(n) | O(n) / O(n) / O(1) | `map`/`filter` allocate a new array |
| Min / max | `Math.max(...a)` | O(n) | O(n) args | **spread of ~10⁵+ items can overflow the call stack**: use `reduce` or a loop |
| Fill | `new Array(n).fill(0)` | O(n) | O(n) | |
| Length | `a.length` | O(1) | | assigning a smaller length truncates |

**Sorting trap:** `[10, 9, 1].sort()` returns `[1, 10, 9]`, because the default comparison converts elements to **strings**. Always pass `(x, y) => x - y` for numbers.

**Copy traps:** `const b = a` aliases; `[...a]` is a **shallow** copy (rows of a 2-D array are shared); `structuredClone(a)` deep-copies. `new Array(r).fill(new Array(c))` shares **one** row `r` times; build grids with `Array.from({ length: r }, () => new Array(c).fill(0))`. Holes: `new Array(5)` has no elements (`.map` skips them); use `.fill`.
"""),
codes[3],
M("""## 8. `Map` and `Set` in depth

### 8.1 How they work
`Map` and `Set` are **hash tables** that remember **insertion order**. A key's hash chooses a slot; collisions are resolved internally; the table grows when it fills, so insertion is amortised O(1).

* **Keys keep their type** in a `Map` (`1` and `"1"` are different keys) and can be anything, including objects (compared **by identity**). A plain **object** used as a map converts every key to a **string** (so `o[1]` and `o["1"]` are the same), inherits properties such as `toString`, and has no `size`. Prefer `Map` for numeric or non-string keys and for counting; use `Object.create(null)` if you need an object-map without inherited names.
* Equal arrays are **different keys**: encode coordinates as a string (`` `${r},${c}` ``), a number (`r * cols + c`), or `JSON.stringify`.

| Operation | `Map` / `Set` | Plain object as a map |
|---|---|---|
| set / add | O(1) amortised | O(1) average |
| get / has | O(1) average | O(1) average |
| delete | O(1) average | O(1) (`delete o.k` can slow the engine) |
| size | O(1) (`m.size`) | O(n) (`Object.keys(o).length`) |
| iterate | O(n), insertion order | O(n), numeric-like keys first, then insertion order |
| space | O(n) | O(n) |

Methods: `m.set(k, v)`, `m.get(k)`, `m.has(k)`, `m.delete(k)`, `m.size`, iteration with `for (const [k, v] of m)`, `m.keys()`, `m.values()`, `[...m]`; `s.add(x)`, `s.has(x)`, `s.delete(x)`, `[...s]`. Remove duplicates with `[...new Set(a)]` (O(n)). There is **no built-in sorted map or tree set**: keep a sorted array and use binary search, whose insertion is O(n).
"""),
codes[4],
HASH,
M("""## 9. Stacks, queues, deques and heaps

JavaScript has no dedicated types for these; you build them from arrays and small classes.

| Structure | Implementation | Operations | Time |
|---|---|---|---|
| **Stack** | array: `push`, `pop`, `a.at(-1)` | push, pop, peek | O(1) each |
| **Queue** | array with a **head index**: `q.push(x)`, `q[head++]` | enqueue, dequeue | O(1) each (`shift()` would be O(n)) |
| **Deque** | two pointers over an array, or a doubly linked list | both ends | O(1) each |
| **Heap / priority queue** | a binary heap class over an array | push, pop, peek | O(log n), O(log n), O(1) |
| **Sorted structure** | sorted array + binary search | find, insert | O(log n), O(n) |

**How the heap works.** Store a complete binary tree in an array: element `i` has children `2i + 1` and `2i + 2` and parent `(i - 1) >> 1`. **Push:** append, then *sift up* while smaller than the parent (at most log n swaps). **Pop:** take the root, move the last element to the root, then *sift down* swapping with the smaller child (at most log n swaps). **Peek:** the root, O(1). **Heapify** n items in O(n). Making it a **max**-heap only needs a flipped comparator. Interviewers allowing JavaScript expect you to write this in a few minutes: practise it until it is automatic.

To keep the **k largest** of a stream use a min-heap of size k: O(n log k) time, O(k) space. The **head-index queue** never frees consumed slots; for very long streams periodically `queue = queue.slice(head); head = 0`.
"""),
codes[5],
M("""## 10. Sorting, comparators and searching

`Array.prototype.sort` is **stable** (guaranteed since ES2019) and runs in O(n log n). The comparator returns a negative number, zero, or a positive number; combine keys with `||`: `(a, b) => b.age - a.age || a.name.localeCompare(b.name)`. It is safe to subtract numbers here because JavaScript numbers are floats and do not wrap (but subtracting `Infinity` values or `NaN` breaks it). `sort` sorts **in place** and returns the same array; `toSorted()` (newer engines) returns a copy; copy first with `[...a].sort(...)` when you must keep the original.

There is no built-in binary search: write the `lo/hi` loop yourself, for example `lowerBound` (first index with `a[i] >= x`) below. `Array.prototype.toSorted`, `findLast` and `at` exist in modern Node versions.
"""),
codes[6],
M("""## 11. Objects, classes and the memory model

An **object** is a collection of key-value properties; arrays, functions, `Map` and `Set` are objects. Objects live on the **heap** and variables hold **references**. `class` syntax defines constructors and methods; fields are public by default (`#private` fields exist). `this` depends on how a function is called (arrow functions capture the surrounding `this`). Equality and hashing are by **identity** for objects, so equal-looking objects are different keys in a `Map`/`Set`. The engine's **garbage collector** frees objects no longer reachable; a long-lived cache or a closure that holds a big array is the usual "leak". Property access on objects is O(1) average.

### 11.1 Linked lists and trees
A node is a small object or class with references (`next`, `left`, `right`). `null` ends a list; dereferencing `null` throws `TypeError`, so test `node !== null`. Reaching the i-th node of a list is O(i); inserting after a node you hold is O(1). A tree of height h has recursion depth h (up to n for a degenerate tree, which can overflow the stack).
"""),
codes[7],
M("""### 11.2 Graphs
Store a graph as an **adjacency list**, an array of arrays (`adj[u]` lists the neighbours of `u`): O(V + E) space. An adjacency matrix is O(V²). Breadth-first search with a head-index queue visits each vertex and edge once: **O(V + E) time, O(V) space**, and gives shortest paths in an unweighted graph.
"""),
GRAPH,
M("""## 12. Errors and edge cases

JavaScript signals problems with **exceptions** (`throw new Error("...")`, `try / catch / finally`) and, much more often, with **silent odd values**: `undefined` from a missing index or property, `NaN` from a failed conversion, `Infinity` from division by zero. Most algorithm bugs are silent, so check edge cases deliberately.

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty array | `Math.max(...[])` is `-Infinity`; `a[0]` is `undefined` | `if (a.length === 0) return ...` |
| Missing map key | `m.get(k)` is `undefined`; `undefined + 1` is `NaN` | `m.get(k) ?? 0` |
| Number conversion | `Number("")` is `0`, `Number("12px")` is `NaN` | validate with `Number.isInteger` / `Number.isNaN` |
| Large integers | silent precision loss above 2⁵³ - 1 | `BigInt`, or check `Number.isSafeInteger` |
| `shift()` in a loop | O(n²) | head index |
| `sort()` without comparator | string order | `(x, y) => x - y` |
| Optional values | `null.x` throws `TypeError` | `x?.y`, `x ?? default` |
"""),
ERR,
M("""## 13. The master complexity cheat sheet

`n` is the number of elements.

| Operation | `Array` | typed array | `Map` / `Set` | object as map | string |
|---|---|---|---|---|---|
| Access by index / key | O(1) | O(1) | O(1) avg (key) | O(1) avg | O(1) |
| Push / pop at end | O(1)* / O(1) | n/a | n/a | n/a | n/a |
| Shift / unshift (front) | **O(n)** | n/a | n/a | n/a | n/a |
| Insert / delete in the middle | O(n) | n/a | O(1) avg (by key) | O(1) avg | O(n) |
| Search by value | O(n) | O(n) | O(1) avg (key) | O(1) avg (key) | O(n·m) |
| Min / max | O(n) | O(n) | O(n) | O(n) | O(n) |
| Sort | O(n log n) | O(n log n) | n/a | n/a | O(n log n) via array |
| Iterate | O(n) | O(n) | O(n) | O(n) | O(n) |
| Copy | O(n) | O(n) | O(n) | O(n) | O(1)-O(n) |
| Space | O(n) | 4/8 bytes per item | O(n), heavier | O(n), heavier | O(n) |

\\* amortised or average. **Heap** (your class): push O(log n), pop O(log n), peek O(1).

**What to say in an interview:** "I'll keep a `Map` from value to index. Each lookup and insert is O(1) on average, so one pass is O(n) time and O(n) extra space. The brute force is O(n²) time and O(1) space; I'm trading memory for time."

## 14. Pitfalls and idioms checklist

1. `arr.sort()` without a comparator sorts as strings.
2. Always use `===` and `!==`.
3. Floats are inexact (`0.1 + 0.2 !== 0.3`); integers above 2⁵³ - 1 lose precision: use `BigInt`.
4. `shift()` and `unshift()` are O(n): use a head index.
5. `new Array(n).fill([])` shares one inner array; use `Array.from`.
6. `Math.max(...hugeArray)` can crash the call stack; use a loop or `reduce`.
7. `[...a]` and `slice()` are shallow; use `structuredClone` for deep copies.
8. `Number("")` is `0`; `parseInt("08", 10)`: pass the radix; validate input.
9. Object keys become strings; use `Map` for numeric or object keys.
10. Equal arrays/objects are not equal keys; encode them.
11. `for...in` iterates keys (strings); use `for...of` for values.
12. Bitwise operators truncate to 32 bits.
13. `var` is function scoped: use `let` and `const`.
14. Recursion depth is about 10⁴: use iteration for deep structures.

## 15. A reusable solution template
"""),
codes[8],
M("""## 16. How JavaScript compares with the other languages in this chapter

| Topic | JavaScript | TypeScript | Python | Java |
|---|---|---|---|---|
| Typing | dynamic | static (erased) | dynamic | static |
| Integers | float (exact to 2⁵³) / `BigInt` | same | unlimited | 32/64-bit wrapping |
| Dynamic array | `Array` | `T[]` | `list` | `ArrayList` |
| Hash map | `Map` | `Map<K,V>` | `dict` | `HashMap` |
| Heap | write your own | write your own | `heapq` | `PriorityQueue` |
| Sorted map | none | none | none | `TreeMap` |
| Default sort | string order! | string order! | numeric | n/a |

## 17. Practice plan

1. Re-type the `MinHeap` until you can write it from memory; it unlocks top-k, Dijkstra and scheduling problems.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest), [Island Counter](/problems/island-counter) and [Shortest Maze Path](/problems/shortest-maze-path) in JavaScript, and write each time and space complexity.
3. Explain aloud why `sort()` needs a comparator, why `shift()` is slow and why `0.1 + 0.2 !== 0.3`: interviewers like these answers.
"""),
], quiz=OLD["quiz"] + [
 Q("What does `0 ?? 5` return?", ["0", "5", "undefined", "NaN"], 0, "?? only replaces null or undefined, unlike ||."),
 Q("`Number(\"\")` is:", ["0", "NaN", "undefined", "An error"], 0, "An empty string converts to zero."),
 Q("`[1] === [1]` is false because:", ["Arrays compare by reference", "Arrays cannot be compared", "The values differ", "=== is broken"], 0, "Use element-wise comparison or encode a key."),
 Q("A safe way to check for a missing map entry then count is:", ["m.set(k, (m.get(k) ?? 0) + 1)", "m[k]++ on a Map", "m.get(k)++", "m.count(k)"], 0, "?? supplies the default for missing keys."),
])
