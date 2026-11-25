"""Deep version of the Python lesson (reuses verified programs from lang_python.py, adds new ones)."""
from courses_a import Q, L
from lang_python import PYTHON as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "python", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 numbers,2 strings,3 lists,4 dict/sets,5 stacks/heap,6 nodes,7 template

REFS = C("""import copy

def mutate(nums, n, s, t):
    nums.append(99)        # mutates the caller's list (same object)
    n += 1                 # rebinds a local name; the caller's int is untouched
    s += "!"               # strings are immutable: creates a new local string
    t = t + (4,)           # tuples too

a = [1, 2, 3]; n = 5; s = "hi"; t = (1, 2, 3)
mutate(a, n, s, t)
print(a, n, s, t)                         # [1, 2, 3, 99] 5 hi (1, 2, 3)

x = [1, 2]; y = x                         # two names, ONE list
y.append(3)
print(x, x is y, x == [1, 2, 3])          # [1, 2, 3] True True

p = [1, 2]; q = [1, 2]
print(p == q, p is q)                     # True False  (equal value, different objects)

a1, a2 = 256, 256
b1, b2 = 10**6, 10**6
print(a1 is a2, b1 == b2)                 # True True (small ints are cached; never rely on `is` for numbers)

def bad_default(x, bucket=[]):            # the default list is created ONCE
    bucket.append(x); return bucket
print(bad_default(1), bad_default(2))     # [1, 2] [1, 2]  (shared!)

def good_default(x, bucket=None):
    if bucket is None: bucket = []
    bucket.append(x); return bucket
print(good_default(1), good_default(2))   # [1] [2]

grid = [[1, 2], [3, 4]]
shallow = grid.copy(); shallow[0][0] = 100
deep = copy.deepcopy(grid); deep[1][1] = -1
print(grid)                               # [[100, 2], [3, 4]]  (rows shared with the shallow copy only)""", "Names and references, mutability, is versus ==, default-argument trap, shallow vs deep copy")

HASH = C("""from dataclasses import dataclass

@dataclass(frozen=True)                   # frozen => hashable, value equality
class Point:
    r: int
    c: int

seen = {Point(1, 2)}
print(Point(1, 2) in seen)                # True

visited = set()
visited.add((0, 1))                       # tuples are hashable
print((0, 1) in visited)                  # True
try:
    visited.add([0, 1])                   # lists are mutable, so unhashable
except TypeError as e:
    print("TypeError:", e)

print(hash(1) == hash(1.0), 1 == 1.0)     # True True  (int and float keys can collide)
d = {1: "int", 1.0: "float", True: "bool"}
print(d)                                  # {1: 'bool'}: all three are the same key

from collections import OrderedDict
lru = OrderedDict()
def get(k):
    if k in lru: lru.move_to_end(k); return lru[k]
def put(k, v, cap=2):
    lru[k] = v; lru.move_to_end(k)
    if len(lru) > cap: lru.popitem(last=False)
put(1, 1); put(2, 2); get(1); put(3, 3)
print(list(lru))                          # [1, 3]""", "Hashable keys, __eq__/__hash__ via dataclass, and a tiny LRU cache")

GRAPH = C("""from collections import defaultdict, deque

n = 6
edges = [(0, 1), (0, 2), (1, 3), (2, 3), (3, 4), (2, 5)]
adj = defaultdict(list)
for u, v in edges:
    adj[u].append(v)
    adj[v].append(u)                      # undirected

dist = {0: 0}
q = deque([0])
while q:
    u = q.popleft()
    for v in adj[u]:
        if v not in dist:
            dist[v] = dist[u] + 1
            q.append(v)
print([dist[i] for i in range(n)])        # [0, 1, 1, 2, 3, 2]""", "A graph as an adjacency dict and breadth-first search")

ERR = C("""def parse_positive(text):
    try:
        v = int(text.strip())
    except ValueError:
        return -1                          # bad text becomes a sentinel
    if v <= 0:
        raise ValueError(f"not positive: {v}")
    return v

print(parse_positive(" 42 "), parse_positive("abc"))     # 42 -1
try:
    parse_positive("-5")
except ValueError as e:
    print(e)

a = [1, 2, 3]
try:
    a[3]
except IndexError:
    print("index error")
d = {"x": 1}
print(d.get("y", 0))                       # 0 (no exception with get)
try:
    d["y"]
except KeyError as k:
    print("key error", k)
try:
    1 / 0
except ZeroDivisionError:
    print("divide by zero")
print(max([], default=-1), sum([]))        # -1 0   (defaults for empty input)
print(next((x for x in [4, 8, 15] if x > 5), -1))   # 8   first match or a default""", "Exceptions, EAFP versus LBYL and defaults for empty input")

PYTHON2 = L("python-for-dsa", "Python for DSA", "free", 75, "", blocks=[
M("""# Python for Data Structures and Algorithms: a complete guide

This lesson explains Python **as you need it for algorithmic problem solving**: what Python is, how it runs your code, how variables and references really work, every built-in type and operator, control flow and functions, and then every data structure available to you (list, tuple, dict, set, deque, heap and more) with the **time and space complexity of every operation**. Each idea comes with a program you can run.

## 1. What Python is, and why it behaves the way it does

**Python** is a high-level, **dynamically typed**, **garbage-collected** language designed for readability. The standard implementation, **CPython**, first compiles your source to **bytecode** and then runs that bytecode in a virtual machine (an interpreter). There is no separate compile step to run: `python3 file.py` is enough. Because the interpreter does extra work for every operation, Python is typically 10 to 100 times slower than C++ for tight loops, which is why built-in functions written in C (`sum`, `sorted`, `max`, `Counter`) are the fastest way to do bulk work.

| Property | What it means | Consequence |
|---|---|---|
| **Dynamic typing** | A variable has no fixed type; the *value* has one | Short code, but type errors appear at run time |
| **Everything is an object** | Numbers, strings, functions and classes are objects with identity, type and value | You can store functions in lists, pass them as arguments, and so on |
| **Reference semantics** | Variables are *names* bound to objects | Assignment never copies an object; aliasing surprises are common |
| **Garbage collected** | Reference counting plus a cycle detector | No manual memory management |
| **Batteries included** | A large standard library | `collections`, `heapq`, `bisect`, `itertools`, `functools` cover most needs |

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| Very concise: more time to think, less to type | Slower than compiled languages; large inputs may time out |
| Arbitrary-precision integers: no overflow | No built-in balanced tree or sorted map |
| Powerful built-ins and comprehension syntax | Recursion limit (default about 1000) |
| Great for prototyping and explaining ideas | Mutable default arguments and aliasing bugs |

## 2. Program structure and input

A Python file runs from top to bottom; there is no required `main`. By convention you put logic in functions and guard the entry point with `if __name__ == "__main__":`. **Indentation** (four spaces) defines blocks; mixing tabs and spaces is an error. For input, `input()` reads one line (slow for big inputs); `sys.stdin.read().split()` reads everything at once and is the standard fast approach.
"""),
codes[0],
M("""**How it works.** `sys.stdin.read()` returns the whole input as one string; `.split()` with no arguments splits on any whitespace and drops empties; `map(int, ...)` converts lazily and `list(...)` materialises. Splitting the entire input costs O(total length) once, far cheaper than thousands of `input()` calls.

## 3. Data types in depth

### 3.1 Names, objects and mutability
In Python `x = 5` does not put 5 "inside" `x`. It **binds the name `x` to an object** that represents 5. Every object has an **identity** (`id(x)`), a **type** (`type(x)`) and a **value**. `==` compares values; `is` compares identity. The most important property of a type is whether its objects are **mutable** (can change in place) or **immutable**.

| Immutable (cannot change) | Mutable (can change in place) |
|---|---|
| `int`, `float`, `bool`, `complex` | `list` |
| `str`, `bytes` | `dict` |
| `tuple`, `frozenset` | `set`, `bytearray` |
| `None` | instances of your own classes (by default) |

Only **immutable** objects are hashable by default, which is why tuples can be dictionary keys and lists cannot.

### 3.2 Numbers
* **`int`** has **unlimited precision**: `2 ** 1000` is exact. There is no overflow, so the "modulo 1 000 000 007" step is needed only because a problem asks for it. Large ints are slower (cost grows with the number of digits), so multiplying two 10⁵-digit numbers is not O(1).
* **`float`** is an IEEE 754 64-bit double (about 15-16 digits): `0.1 + 0.2 == 0.3` is `False`. Use `math.isclose` for comparisons, and integers where exactness matters. `float("inf")` is a handy "infinity" for minimum searches.
* **`bool`** is a subclass of `int` (`True == 1`, `True + True == 2`).
* Conversions are explicit: `int("42")`, `int(3.9)` is `3` (truncates toward zero), `float("1.5")`, `str(12)`, `int("101", 2)` is `5`.

### 3.3 Truthiness
In conditions Python treats these as **false**: `False`, `None`, `0`, `0.0`, `""`, `[]`, `()`, `{}`, `set()`. Everything else is true. So `if not nums:` is the idiomatic empty check. Careful: `0` is false, so `if x:` is the wrong test when `0` is a legal value.

### 3.4 Operators

| Operator | Meaning | Notes |
|---|---|---|
| `/` | true division | always returns a `float` |
| `//` | floor division | rounds toward **minus infinity**: `-7 // 2 == -4` |
| `%` | modulo | result has the sign of the **divisor**: `-7 % 3 == 2` (unlike C, Java, Go) |
| `**` | power | `pow(a, b, m)` is fast modular exponentiation |
| `and`, `or`, `not` | logic | short-circuit and return one of the operands: `x or default` |
| `==`, `!=`, `<`, ... | comparison | **chainable**: `0 <= i < n` |
| `in`, `not in` | membership | cost depends on the container (see below) |
| `&`, `\\|`, `^`, `~`, `<<`, `>>` | bitwise | on unlimited integers; `~x == -x - 1` |
| `:=` | assignment expression ("walrus") | `if (n := len(a)) > 10:` |

Useful built-ins: `abs`, `divmod(a, b)` (both quotient and remainder), `min`, `max`, `sum`, `round` (banker's rounding: `round(2.5) == 2`), `sorted`, `reversed`, `enumerate`, `zip`, `any`, `all`, `len`, `range`, `map`, `filter`.
"""),
codes[1],
M("""## 4. Control flow

**Conditionals:** `if / elif / else`; a one-line form `a if cond else b`. Since Python 3.10 there is `match` for pattern matching.

**Loops.**

| Loop | Use it when |
|---|---|
| `for x in iterable:` | iterate values of any iterable (list, string, dict keys, range, generator) |
| `for i in range(n):` | repeat n times (`range(a, b, step)`; `range` does not build a list, O(1) space) |
| `for i, x in enumerate(a):` | you need index and value |
| `for x, y in zip(a, b):` | walk two sequences together |
| `while cond:` | repeat until something changes (binary search, two pointers) |

`break` and `continue` work as usual, and loops have an `else:` clause that runs **only if the loop finished without `break`** (handy for "no match found"). A **comprehension** builds a collection from a loop in one expression: `[x * x for x in a if x % 2 == 0]`, `{x: i for i, x in enumerate(a)}`, `{x % 3 for x in a}`; a generator expression `(x * x for x in a)` does the same lazily with O(1) memory.

**Counting the cost.** A `for` over n items is O(n). A `for` that contains `x in some_list` is O(n²), because `in` on a list scans; replacing the list by a `set` makes the membership test O(1) and the whole loop O(n). Always check the cost of the operation *inside* your loop.

## 5. Functions in depth

A function is defined with `def name(params): ...` and returns a value with `return` (or `None` if it falls off the end). Parameters can have **defaults** (`def f(x, k=3)`), be **keyword-only**, or collect extras with `*args` (tuple) and `**kwargs` (dict). Functions are objects: pass them, store them, return them. A **lambda** is a small anonymous function (`lambda p: p[1]`), common as a `key=` argument. **Closures** capture variables from the enclosing scope (use `nonlocal` to rebind them).

### 5.1 "Pass by object reference"
When you call `f(x)`, the parameter name is bound to **the same object** the caller's name refers to. If the object is **mutable** and the function changes it in place, the caller sees the change. If the function **rebinds** the parameter (`n += 1` on an int, or `n = something_else`), only the local name changes. This is exactly Java's rule, expressed in Python's vocabulary of names and objects. The program below shows it, together with `is` versus `==`, the infamous mutable default argument and shallow versus deep copies. Predict each output first.
"""),
REFS,
M("""### 5.2 Recursion, the call stack and memoisation
Each call uses a stack frame, and CPython limits depth to about **1000** by default (`RecursionError`). Raise it with `sys.setrecursionlimit(10**6)` for moderately deep recursion, or convert to a loop with an explicit stack. For **dynamic programming**, memoise with `functools.lru_cache(None)` (or `functools.cache`): the first call computes and stores the result, later calls with the same arguments return in O(1); arguments must be hashable.

## 6. Strings in depth

A `str` is an **immutable** sequence of Unicode code points (not bytes). Immutable means every "modification" creates a new string, so building a long string with `+=` in a loop is O(n²) in the worst case; collect pieces in a list and `"".join(parts)` once (O(n)). Indexing `s[i]` returns a one-character string (there is no separate `char` type).

### 6.1 Operations and their costs
Let `n = len(s)`, `m = len(t)`.

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length | `len(s)` | O(1) | stored |
| Index | `s[i]` | O(1) | negative indexes count from the end |
| Slice | `s[a:b]` | O(b - a) | **copies**; avoid slicing in recursion on long strings |
| Concatenate | `s + t` | O(n + m) | new string |
| Join a list | `"".join(parts)` | O(total length) | the right way to build strings |
| Compare / equality | `s == t` | O(min(n, m)) | |
| Membership / find | `t in s`, `s.find(t)` | O(n·m) worst | optimised search; `find` returns -1 if absent, `index` raises |
| Split | `s.split()` | O(n) | |
| Reverse | `s[::-1]` | O(n) | |
| Count | `s.count(t)` | O(n) | |
| Hash | `hash(s)` | O(n) once | cached |
| Case, tests | `lower`, `isdigit`, `isalpha` | O(n) | |

**Space:** a string takes about n bytes for ASCII text (1, 2 or 4 bytes per character depending on the widest character) plus about 50 bytes of overhead.

### 6.2 Characters and numbers
`ord("a")` is 97 and `chr(97)` is `"a"`, so letter frequency tables use `ord(c) - ord("a")` as an index into a list of 26 counters, or simply `collections.Counter`. Strings compare **lexicographically** (dictionary order), and `"B" < "a"` because uppercase letters have smaller code points.
"""),
codes[2],
M("""## 7. Lists in depth (Python's dynamic array)

A `list` is an array of **references** to objects, stored contiguously. Because it holds references, one list can hold mixed types, and `a[i]` is O(1): pointer arithmetic. When the list is full, CPython allocates a larger array (it **over-allocates by about 12.5%** plus a little) and copies the pointers; growth is geometric, so `append` is **amortised O(1)**.

| Operation | Time | Space | Notes |
|---|---|---|---|
| `a[i]`, `a[i] = x` | O(1) | O(1) | negative index from the end |
| `a.append(x)` | amortised O(1) | O(1) | |
| `a.pop()` | O(1) | O(1) | removes the last item |
| `a.pop(0)`, `a.insert(0, x)` | **O(n)** | O(1) | shifts everything; use `collections.deque` |
| `a.insert(i, x)`, `del a[i]`, `a.pop(i)` | O(n) | O(1) | |
| `x in a`, `a.index(x)`, `a.count(x)`, `a.remove(x)` | O(n) | O(1) | linear scan |
| `len(a)` | O(1) | O(1) | |
| `a[i:j]` (slice) | O(j - i) | O(j - i) | creates a new list |
| `a + b`, `a * k` | O(n + m), O(n·k) | same | new list |
| `a.sort()`, `sorted(a)` | O(n log n) | O(1) in place / O(n) | Timsort, stable |
| `a.reverse()` | O(n) | O(1) | |
| `min(a)`, `max(a)`, `sum(a)` | O(n) | O(1) | |
| `a.copy()`, `list(a)` | O(n) | O(n) | shallow |

**Trap:** `[[0] * c] * r` creates `r` references to the *same* inner list. Use `[[0] * c for _ in range(r)]`.

### 7.1 Tuples
A `tuple` is an **immutable** sequence: fixed length, hashable if its items are, slightly smaller and faster than a list. Use tuples for coordinates `(r, c)`, dictionary keys, and returning several values. Unpacking: `r, c = pos`, `first, *rest = items`, and swaps `a, b = b, a`.
"""),
codes[3],
M("""## 8. Dictionaries and sets in depth

### 8.1 How a dict works
A `dict` is a **hash table**. For a key, Python computes `hash(key)`, maps it to a slot in an array, and uses **open addressing** (probing other slots on collision) rather than chaining. Since Python 3.7 a dict also remembers **insertion order** (it stores entries in a compact array and keeps a separate index table). The table **resizes** (roughly doubles) when about two-thirds full, so insertion is amortised O(1). A `set` is a dict without values.

**Keys must be hashable**: immutable types whose `hash` never changes while the object lives. Numbers, strings, tuples of hashables and frozen dataclasses are fine; lists, dicts and sets are not. Equal objects must have equal hashes (`1 == 1.0 == True`, so they are the same key).

| Operation | dict | set | Notes |
|---|---|---|---|
| `d[k]`, `d.get(k)`, `k in d` | O(1) average | O(1) | worst case O(n) with adversarial hashes (rare) |
| `d[k] = v`, `s.add(x)` | O(1) amortised | O(1) | |
| `del d[k]`, `s.remove(x)` | O(1) average | O(1) | `d.pop(k, default)`, `s.discard(x)` do not raise |
| iterate | O(n) | O(n) | in insertion order for dict; arbitrary for set |
| `len` | O(1) | O(1) | |
| `s \\| t`, `s & t`, `s - t` | | O(len(s) + len(t)) | union, intersection, difference |
| copy | O(n) | O(n) | |
| Space | O(n), about 50-100 bytes per entry | similar | memory heavier than a list |

Handy patterns: `d[k] = d.get(k, 0) + 1`, `collections.Counter(iterable)` (frequency map with `most_common(k)`), `collections.defaultdict(list)` (auto-creates missing values), `d.setdefault(k, []).append(x)`, `sorted(d.items(), key=lambda kv: -kv[1])`. **Do not modify a dict while iterating it** (`RuntimeError`); iterate over `list(d)` instead.
"""),
codes[4],
HASH,
M("""## 9. Stacks, queues, deques and heaps in depth

### 9.1 Stack
Use a plain `list`: `append` pushes and `pop()` pops, both O(1); the top is `a[-1]`. Memory is O(n).

### 9.2 Queue and deque
**Never use `list.pop(0)` as a queue**: it shifts all remaining items, O(n). Use `collections.deque`, a **doubly linked list of fixed-size blocks**: `append`, `appendleft`, `pop`, `popleft` are all **O(1)**; indexing in the middle is O(n); `deque(maxlen=k)` keeps only the last `k` items (a sliding window of recent values for free).

### 9.3 Heap (priority queue)
`heapq` implements a **binary min-heap on top of a normal list**: index `i` has children `2i + 1`, `2i + 2`. `heappush` sifts up and `heappop` sifts down, each at most log n steps; `heap[0]` is the smallest, O(1). `heapify(list)` turns a list into a heap in **O(n)**. For a **max-heap** push negated values, or use `heapq.nlargest(k, a)` / `nsmallest` (O(n log k)). Items are compared as tuples, so `(priority, counter, payload)` avoids comparing payloads that cannot be ordered.

| Operation | list (stack) | deque | heapq | Notes |
|---|---|---|---|---|
| push at end | O(1)* | O(1) | `heappush` O(log n) | |
| pop from end | O(1) | O(1) | `heappop` O(log n) | heap pops the **smallest** |
| push/pop at front | **O(n)** | O(1) | n/a | |
| peek | O(1) | O(1) | `h[0]` O(1) | |
| search | O(n) | O(n) | O(n) | |
| build from n items | | | `heapify` O(n) | |
| space | O(n) | O(n) | O(n) | |

### 9.4 Sorted containers
Python has no built-in balanced tree. The `bisect` module keeps a **sorted list**: `bisect_left(a, x)` / `bisect_right(a, x)` find the insertion point in O(log n), and `insort` inserts in O(log n) search plus **O(n)** shifting. For heavy insert/delete workloads interviewers accept `bisect` on a list, or the third-party `sortedcontainers`.
"""),
codes[5],
M("""## 10. Sorting and searching in depth

`list.sort()` (in place, returns `None`) and `sorted(iterable)` (returns a new list) use **Timsort**: O(n log n) worst case, **stable**, O(n) extra space, and near O(n) on data that is already partly ordered. Choose what to sort by with `key=`, which is called **once per element** (faster than a comparison function): `sorted(words, key=len)`, `sorted(points, key=lambda p: (-p[1], p[0]))` (second field descending, then first ascending; negate numbers, tuples compare element by element). `reverse=True` reverses while keeping stability. For a rarely needed custom comparison use `functools.cmp_to_key`.

Searching: `x in a` on a list is O(n); on a **sorted** list use `bisect` (O(log n)). Binary search by hand needs a `lo`/`hi` loop (see the Binary Search chapter).

## 11. Classes, objects and memory

A **class** defines attributes and methods; an **instance** is created by calling the class. `__init__` initialises; `self` is the instance. `@dataclass` generates `__init__`, `__repr__` and `__eq__` from annotated fields (use `frozen=True` to make instances hashable). If you define `__eq__` you must also define `__hash__` to use objects as dictionary keys. `__slots__` saves memory for millions of small objects.

CPython frees memory by **reference counting** (an object is freed when no name refers to it) and a **cycle detector** for objects that refer to each other. Because everything is an object with a header, a Python `int` takes about 28 bytes and a one-element list about 64 bytes: a list of a million ints uses roughly 36 MB (8 MB of pointers plus the int objects), far more than a C++ `vector<int>` (4 MB). Use `array.array('i')` or `numpy` when memory matters; in interviews just remember that Python structures are heavier.

### 11.1 Linked structures, trees and graphs
Nodes are small classes (or dataclasses) with references. A binary tree node has `val`, `left`, `right`. A graph is stored as an **adjacency list**: `defaultdict(list)` or a list of lists, O(V + E) space; an adjacency matrix is O(V²). Breadth-first search with a `deque` visits every node and edge once: **O(V + E) time, O(V) space**.
"""),
codes[6],
GRAPH,
M("""## 12. Errors and edge cases

Python reports problems with **exceptions**: `IndexError` (bad index), `KeyError` (missing dict key), `ValueError` (bad value, e.g. `int("abc")`), `TypeError`, `ZeroDivisionError`, `RecursionError`. Handle them with `try / except / else / finally`. Python's culture prefers **EAFP** ("easier to ask forgiveness than permission": try it and catch the error) to **LBYL** ("look before you leap": check first), but in algorithm code you mostly **prevent** exceptions by handling edge cases:

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty input | `a[0]`, `max([])` raise | `if not a:` first, or `max(a, default=-1)` |
| One element | neighbour comparisons skip it | test n = 1 |
| Duplicates | a dict overwrites an index | use `setdefault` / decide first-wins |
| Negative numbers | `%` and `//` differ from other languages | check with -1 |
| Huge input | O(n²) timeouts | pick O(n) or O(n log n) ideas |
| No answer | returning garbage | return `-1` or `None` consistently |
"""),
ERR,
M("""## 13. The master complexity cheat sheet

`n` is the number of elements. "Space" is what the structure itself uses.

| Operation | `list` | `tuple` | `dict` | `set` | `deque` | `heapq` list | `str` |
|---|---|---|---|---|---|---|---|
| Index `x[i]` | O(1) | O(1) | n/a | n/a | O(n) middle | O(1) | O(1) |
| Append / add at end | O(1)* | n/a | O(1)* | O(1)* | O(1) | `heappush` O(log n) | n/a |
| Insert / pop at front | O(n) | n/a | n/a | n/a | O(1) | n/a | n/a |
| Insert / delete in the middle | O(n) | n/a | O(1)* by key | O(1)* | O(n) | n/a | n/a |
| Pop from end | O(1) | n/a | n/a | n/a | O(1) | `heappop` O(log n) | n/a |
| Search by value (`in`) | O(n) | O(n) | O(1)* by key | O(1)* | O(n) | O(n) | O(n·m) |
| Min / max | O(n) | O(n) | O(n) | O(n) | O(n) | O(1) smallest | O(n) |
| Sort | O(n log n) | n/a | n/a | n/a | n/a | n/a | O(n log n) |
| Iterate | O(n) | O(n) | O(n) | O(n) | O(n) | O(n) | O(n) |
| Space | O(n) | O(n) | O(n), heavier | O(n), heavier | O(n) | O(n) | O(n) |

\\* amortised or average.

**What to say in an interview:** "I'll keep a `dict` from value to index. Each lookup and insert is O(1) on average, so one pass is O(n) time and O(n) extra space. The brute force is O(n²) time and O(1) space; I am trading memory for time."

## 14. Pitfalls and idioms checklist

1. `[[0] * c] * r` shares rows: use a comprehension.
2. Never use a mutable default argument; use `None`.
3. `list.pop(0)`, `insert(0, x)` and `x in list` are O(n): use `deque` and `set`.
4. Use `==` for values and `is` only for `None`.
5. `-7 // 2 == -4` and `-7 % 3 == 2`: floor semantics differ from C, Java and Go.
6. Slicing copies; do not slice repeatedly inside recursion.
7. Do not modify a list or dict while iterating it.
8. `sort()` returns `None`: `a = a.sort()` loses the list.
9. Float comparison: `math.isclose`; avoid floats for exact counting.
10. Use `enumerate` and `zip` instead of index arithmetic.
11. Raise the recursion limit or convert to iteration for deep recursion.
12. A `dict` iterates in insertion order; a `set` does not.
13. Keys must be hashable: convert lists to tuples.
14. Use `Counter`, `defaultdict`, `heapq`, `bisect` and `itertools` instead of writing them by hand.

## 15. A reusable solution template
"""),
codes[7],
M("""## 16. How Python compares with the other languages in this chapter

| Topic | Python | Java | C++ | Go |
|---|---|---|---|---|
| Integer overflow | never | wraps | undefined (signed) | wraps |
| Dynamic array | `list` | `ArrayList` | `vector` | slice |
| Hash map | `dict` | `HashMap` | `unordered_map` | `map` |
| Heap | `heapq` (min) | `PriorityQueue` (min) | `priority_queue` (max) | `container/heap` |
| Sorted map | none | `TreeMap` | `map` | none |
| Speed (loops) | slowest | fast | fastest | fast |

## 17. Practice plan

1. Re-type every program from memory, then change the input so it breaks, and explain why.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Anagram Groups](/problems/anagram-groups), [Merge Meeting Slots](/problems/merge-meeting-slots) and [Island Counter](/problems/island-counter) in Python.
3. For each structure you use, say its insert, delete, search and iterate cost aloud, and say why.
"""),
], quiz=OLD["quiz"] + [
 Q("In `def f(nums): nums.append(1)`, calling `f(a)` with a list `a`:", ["Changes the caller's list", "Copies the list", "Raises an error", "Does nothing"], 0, "The parameter refers to the same mutable object."),
 Q("Why is `x in some_list` inside a loop usually a performance bug?", ["It is O(n) per check, making the loop O(n²)", "It raises errors", "Lists cannot be searched", "It sorts the list"], 0, "Use a set for O(1) membership."),
 Q("What does `max([], default=-1)` return?", ["-1", "An error", "0", "None"], 0, "default supplies a value for empty input."),
 Q("Why can a tuple be a dict key but a list cannot?", ["Tuples are immutable and hashable", "Tuples are shorter", "Lists are slower", "Dicts reject sequences"], 0, "Keys must be hashable."),
])
