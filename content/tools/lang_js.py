from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "javascript", code, cap)

JS = L("javascript-for-dsa", "JavaScript for DSA", "free", 30, "", blocks=[
M("""## Why JavaScript for interviews?

JavaScript runs in every browser and, through **Node.js**, on servers. If you apply for front-end or full-stack roles you may be asked to solve problems in it. It is dynamically typed and concise like Python, but with a few traps that matter a lot in interviews: numbers are all floating point, `==` converts types, arrays are objects, and the standard library has **no built-in heap, deque or sorted map**, so you will write small ones yourself.

| Strengths | Weaknesses |
|---|---|
| Very common in web roles; fast JIT engines | No priority queue, deque or tree map in the standard library |
| Concise array methods (`map`, `filter`, `reduce`) | Default `sort()` compares as **strings** |
| `Map` and `Set` keep insertion order | One `Number` type: precise integers only up to 2⁵³ - 1 |
| `BigInt` for huge integers | Type coercion surprises with `==` and `+` |

All programs below run on Node.js 18 or newer.

## 1. Program skeleton and input

There is no `main`. Read standard input in Node with `fs.readFileSync(0)` (file descriptor 0).
"""),
C("""const data = require("fs").readFileSync(0, "utf8").split(/\\s+/).filter(Boolean).map(Number);
const n = data[0], k = data[1];
const nums = data.slice(2, 2 + n);
const sum = nums.reduce((a, b) => a + b, 0);
console.log(`n=${n} k=${k} sum=${sum}`);""", "Read all tokens, convert to numbers"),
M("""## 2. Numbers, `BigInt` and equality

Every normal number is a 64-bit **float** (`Number`). Integers are exact up to `Number.MAX_SAFE_INTEGER` (9 007 199 254 740 991 = 2⁵³ - 1); beyond that you silently lose precision. Use **`BigInt`** (`123n`) for large exact integers (you cannot mix `BigInt` and `Number` without converting).

| Operator | Notes |
|---|---|
| `/` | always true division: `7 / 2 === 3.5`; use `Math.floor(7 / 2)` or `Math.trunc` for integer division |
| `%` | remainder keeps the sign of the dividend: `-7 % 3 === -1`; non-negative modulo: `((a % m) + m) % m` |
| `===` | strict equality: use it always (`==` coerces: `"1" == 1` is true) |
| `Math.max(...a)` | spreading a huge array can overflow the call stack (around 10⁵ items) |
| Bitwise ops | work on **32-bit** signed integers: `1 << 31` is negative |
"""),
C("""console.log(0.1 + 0.2 === 0.3);                // false (floating point)
console.log(Number.MAX_SAFE_INTEGER + 2);        // 9007199254740992 (precision lost)
console.log(2n ** 100n);                         // exact big integer
console.log(Math.floor(7 / 2), Math.trunc(-7 / 2), -7 % 3);   // 3 -3 -1
console.log(((-7 % 3) + 3) % 3);                 // 2
console.log("1" == 1, "1" === 1);                // true false
console.log(1 << 31, 2 ** 31 | 0);               // -2147483648 -2147483648
const MOD = 1_000_000_007n;
console.log(123456789n * 987654321n % MOD);     // BigInt modular product""", "Floats, BigInt, division and equality"),
M("""## 3. Control flow, functions and scope

`if/else`, `switch`, `for`, `for...of` (values), `for...in` (keys; avoid on arrays), `while`, `break`, `continue`. Prefer `const` (cannot be reassigned) and `let` (block scoped); avoid `var`. Functions can be declarations, expressions or **arrow functions** `(a, b) => a + b`. Arguments are passed by value; for objects and arrays the value is a reference, so mutations are visible to the caller.

## 4. Strings

Strings are **immutable** UTF-16 sequences. Indexing `s[i]` gives a one-character string (no `char` type). Build with an array and `join` or `+=` (engines optimise concatenation).

| Task | Code |
|---|---|
| Length, char at | `s.length`, `s[i]`, `s.charAt(i)` |
| Char code / back | `s.charCodeAt(i)`, `String.fromCharCode(97)` |
| Letter index 0..25 | `s.charCodeAt(i) - 97` |
| Substring | `s.slice(a, b)` (end exclusive) |
| Split / join | `s.split(" ")`, `arr.join(",")` |
| Reverse | `[...s].reverse().join("")` |
| Search | `s.indexOf("ab")` (-1 if absent), `s.includes("ab")` |
"""),
C("""function isPalindrome(s) {
  let i = 0, j = s.length - 1;
  while (i < j) if (s[i++] !== s[j--]) return false;
  return true;
}
console.log(isPalindrome("level"));                         // true

console.log([..."hello"].reverse().join(""));               // olleh

const counts = new Array(26).fill(0);                       // letter frequencies
for (const c of "banana") counts[c.charCodeAt(0) - 97]++;
console.log(counts[0], counts["n".charCodeAt(0) - 97]);     // 3 2

console.log("abcabc".indexOf("ca"), "a-b-c".split("-"));   // 2 [ 'a', 'b', 'c' ]""", "Strings and a counting array"),
M("""## 5. Arrays

JavaScript arrays are **dynamic arrays**: `push` / `pop` at the end are O(1); `shift` / `unshift` at the front are **O(n)** in general (use an index pointer or a custom queue). 2-D arrays are arrays of arrays: `Array.from({ length: r }, () => new Array(c).fill(0))` (never `new Array(r).fill(new Array(c))`, which shares one row).

**Sorting trap:** `arr.sort()` with no comparator sorts as **strings**: `[10, 9, 1].sort()` gives `[1, 10, 9]`. Always pass `(a, b) => a - b` for numbers (safe here because numbers are floats, no integer overflow).
"""),
C("""const a = [5, 2, 9, 1, 10];
console.log([...a].sort());                  // [ 1, 10, 2, 5, 9 ]  (string order!)
console.log([...a].sort((x, y) => x - y));   // [ 1, 2, 5, 9, 10 ]

const grid = Array.from({ length: 3 }, () => new Array(4).fill(-1));
grid[1][2] = 7;
console.log(grid);

const bad = new Array(2).fill(new Array(2).fill(0));
bad[0][0] = 1;
console.log(bad);                            // both rows changed (shared reference)

console.log(a.slice(1, 3), a.indexOf(9), a.includes(2));
console.log(a.map(x => x * 2), a.filter(x => x % 2 === 0), a.reduce((s, x) => s + x, 0));
console.log(Math.max(...a), Math.min(...a));

const pairs = [[3, "c"], [1, "a"], [2, "b"]];
pairs.sort((p, q) => p[0] - q[0]);
console.log(pairs);""", "Arrays, the sort trap, 2-D grids and array methods"),
M("""## 6. `Map` and `Set`

Use `Map` for hash maps (any key type, keeps insertion order, `size` property) and `Set` for hash sets. Plain objects (`{}`) also work as maps but have string keys and inherited properties; prefer `Map` when keys are numbers or you need correct counting. Average O(1) for `get`, `set`, `has`, `delete`. Object and array keys use **identity**, so `new Set()` of arrays does not deduplicate equal arrays: use a string key like `` `${r},${c}` ``.
"""),
C("""const freq = new Map();
for (const c of "mississippi") freq.set(c, (freq.get(c) ?? 0) + 1);
console.log(freq.get("s"), freq.get("i"), freq.size);       // 4 4 4

const nums = [2, 7, 11, 15], target = 9;
const seen = new Map();                                       // value -> index
for (let i = 0; i < nums.length; i++) {
  if (seen.has(target - nums[i])) { console.log(seen.get(target - nums[i]), i); break; }
  seen.set(nums[i], i);
}

const groups = new Map();
for (const w of ["to", "be", "or", "not"]) {
  if (!groups.has(w.length)) groups.set(w.length, []);
  groups.get(w.length).push(w);
}
console.log([...groups]);                                    // [ [ 2, [ 'to', 'be', 'or' ] ], [ 3, [ 'not' ] ] ]

const visited = new Set();
visited.add(`${0},${1}`);                                    // string key for a coordinate
console.log(visited.has("0,1"), visited.size);

const unique = [...new Set([3, 1, 3, 2, 1])];
console.log(unique);                                         // [ 3, 1, 2 ]""", "Counting, two-sum, grouping and Set of coordinates"),
M("""## 7. Stack, queue, deque and heap (build your own)

| Need | Use |
|---|---|
| Stack | Array: `push`, `pop`, `at(-1)` for the top |
| Queue | Array with a moving **head index** (avoids O(n) `shift`), or a linked list |
| Deque | Array with two pointers, or a doubly linked list |
| Heap / priority queue | Write a small binary heap class (below) |
| Sorted set | No built-in; keep a sorted array with binary search |

Interviews that allow JavaScript expect you to write a heap in about two minutes. Practise it until it is automatic.
"""),
C("""class MinHeap {
  constructor(cmp = (a, b) => a - b) { this.a = []; this.cmp = cmp; }
  get size() { return this.a.length; }
  peek() { return this.a[0]; }
  push(x) {
    const a = this.a; a.push(x);
    let i = a.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (this.cmp(a[i], a[p]) >= 0) break;
      [a[i], a[p]] = [a[p], a[i]]; i = p;
    }
  }
  pop() {
    const a = this.a, top = a[0], last = a.pop();
    if (a.length) {
      a[0] = last;
      let i = 0;
      for (;;) {
        const l = 2 * i + 1, r = l + 1; let m = i;
        if (l < a.length && this.cmp(a[l], a[m]) < 0) m = l;
        if (r < a.length && this.cmp(a[r], a[m]) < 0) m = r;
        if (m === i) break;
        [a[i], a[m]] = [a[m], a[i]]; i = m;
      }
    }
    return top;
  }
}

const h = new MinHeap();
[5, 1, 4].forEach(x => h.push(x));
console.log(h.pop(), h.peek(), h.size);                       // 1 4 2

const maxHeap = new MinHeap((a, b) => b - a);                  // flip the comparator for a max-heap
[3, 9, 4].forEach(x => maxHeap.push(x));
console.log(maxHeap.pop());                                    // 9

const stack = [1, 2]; stack.push(3);
console.log(stack.pop(), stack.at(-1));                        // 3 2

const queue = [1, 2, 3]; let head = 0;                         // O(1) dequeue with a head index
console.log(queue[head++], queue.length - head);               // 1 2""", "A reusable MinHeap, plus stack and queue"),
M("""## 8. Sorting, comparators and binary search

`sort` is stable in modern engines (since ES2019) and runs in O(n log n). The comparator returns a negative, zero or positive number; for multi-key sorts chain with `||`: `(a, b) => b.age - a.age || a.name.localeCompare(b.name)`. There is no built-in binary search; write the standard loop (see the Binary Search chapter).
"""),
C("""const people = [{ name: "Asha", age: 30 }, { name: "Ben", age: 25 }, { name: "Chen", age: 30 }];
people.sort((a, b) => b.age - a.age || a.name.localeCompare(b.name));
console.log(people.map(p => p.name).join(" "));               // Asha Chen Ben

function lowerBound(a, x) {            // first index with a[i] >= x
  let lo = 0, hi = a.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (a[mid] < x) lo = mid + 1; else hi = mid;
  }
  return lo;
}
console.log(lowerBound([1, 3, 5, 5, 7, 9], 5));               // 2

const intervals = [[5, 8], [1, 3], [2, 6]];
intervals.sort((p, q) => p[0] - q[0]);
console.log(intervals.map(i => i[0]).join(""));               // 125""", "Multi-key sort, lowerBound and interval sort"),
M("""## 9. Classes, nodes and recursion

Use `class` for nodes. `null` and `undefined` are different: use `=== null` or `== null` deliberately. Recursion depth is limited (roughly 10⁴ frames); convert very deep recursion to loops.
"""),
C("""class ListNode { constructor(val, next = null) { this.val = val; this.next = next; } }
class TreeNode { constructor(val, left = null, right = null) { this.val = val; this.left = left; this.right = right; } }

function reverse(head) {
  let prev = null;
  while (head) { const nxt = head.next; head.next = prev; prev = head; head = nxt; }
  return prev;
}
const height = t => (t === null ? 0 : 1 + Math.max(height(t.left), height(t.right)));

let h = reverse(new ListNode(1, new ListNode(2, new ListNode(3))));
const out = [];
for (let p = h; p; p = p.next) out.push(p.val);
console.log(out);                                              // [ 3, 2, 1 ]
console.log(height(new TreeNode(1, new TreeNode(2, new TreeNode(3)))));   // 3""", "Linked list reversal and tree height"),
M("""## 10. Complexity cheat sheet (JavaScript)

| Operation | Array | Map / Set | Object (as map) |
|---|---|---|---|
| Index `a[i]` | O(1) | - | - |
| Push / pop at end | O(1) | - | - |
| Shift / unshift | O(n) | - | - |
| Lookup `has/get` | O(n) for `includes` | O(1) avg | O(1) avg |
| Insert middle (`splice`) | O(n) | - | - |
| Sort | O(n log n) | - | - |

## 11. Pitfalls and idioms checklist

- `arr.sort()` without a comparator sorts as strings.
- Use `===`; avoid `==`.
- `0.1 + 0.2 !== 0.3`; compare floats with a tolerance, and prefer integers.
- Integers are exact only up to 2⁵³ - 1; use `BigInt` beyond that.
- `shift()` is O(n); use a head index for queues.
- `new Array(n).fill([])` shares one inner array.
- `Math.max(...hugeArray)` can crash; use a loop or `reduce`.
- `parseInt("08")` and `Number("")` surprises: be explicit (`Number(x)`, `parseInt(x, 10)`).
- Objects as map keys become strings: `{1: "a"}["1"]` works; use `Map` for numbers.
- `for...in` iterates keys (strings) in arrays; use `for...of`.
- Bitwise operators truncate to 32 bits.

## 12. A reusable solution template
"""),
C("""function twoSum(a, target) {
  const seen = new Map();
  for (let j = 0; j < a.length; j++) {
    if (seen.has(target - a[j])) return [seen.get(target - a[j]) + 1, j + 1];
    if (!seen.has(a[j])) seen.set(a[j], j);
  }
  return null;
}

const data = require("fs").readFileSync(0, "utf8").split(/\\s+/).filter(Boolean).map(Number);
const n = data[0], t = data[1];
const a = data.slice(2, 2 + n);
const r = twoSum(a, t);
console.log(r === null ? "-1" : `${r[0]} ${r[1]}`);""", "Template: parse, call a pure function, print"),
M("""## 13. What to practise next

1. Re-type the `MinHeap` three times until you can write it without looking; it unlocks top-k, Dijkstra and scheduling problems.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest) and [Island Counter](/problems/island-counter) in JavaScript.
3. Practise explaining why `sort()` needs a comparator and why `shift()` is slow: interviewers like these answers.
"""),
], quiz=[
 Q("`[10, 9, 1].sort()` returns:", ["[1, 10, 9]", "[1, 9, 10]", "[10, 9, 1]", "An error"], 0, "The default sort compares strings."),
 Q("Why is `queue.shift()` a poor choice for a large queue?", ["It is O(n)", "It throws", "It is not a function", "It sorts"], 0, "Use a head index or a linked list."),
 Q("Which structure is missing from JavaScript's standard library?", ["Priority queue (heap)", "Map", "Set", "Array"], 0, "You write your own heap."),
 Q("Integers are exact in `Number` only up to:", ["2^53 - 1", "2^31 - 1", "2^64", "10^9"], 0, "Use BigInt for larger exact integers."),
 Q("To get a 2-D grid of independent rows you should:", ["Use Array.from with a factory", "Use new Array(r).fill(row)", "Use one array", "Use an object"], 0, "fill(row) repeats one reference."),
])
