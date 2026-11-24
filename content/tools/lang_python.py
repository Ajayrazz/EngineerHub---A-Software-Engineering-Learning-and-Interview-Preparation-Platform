from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "python", code, cap)

PYTHON = L("python-for-dsa", "Python for DSA", "free", 30, "", blocks=[
M("""## Why Python for interviews?

Python is **dynamically typed, interpreted and very concise**. A solution that takes 40 lines in Java often takes 10 in Python, which leaves more of the interview for thinking. Its built-in `list`, `dict`, `set`, `deque` and `heapq` cover almost every structure, and big integers never overflow.

| Strengths | Weaknesses |
|---|---|
| Short, readable code | Slower than compiled languages (matters for tight limits) |
| Built-in big integers | No built-in sorted map/tree set (use `bisect` or a library) |
| Powerful standard library (`collections`, `heapq`, `bisect`, `itertools`) | Recursion limit (about 1000) |
| Great for prototyping ideas quickly | Mutable default arguments and aliasing surprises |

## 1. Program skeleton and input

Python has no `main` boilerplate. Read all input at once with `sys.stdin`; `input()` is fine for small data but slow for large.
"""),
C("""import sys

def main():
    data = sys.stdin.read().split()
    n, k = int(data[0]), int(data[1])
    nums = list(map(int, data[2:2 + n]))
    print(f"n={n} k={k} sum={sum(nums)}")

if __name__ == "__main__":
    main()""", "Read every token once, then convert"),
M("""## 2. Types and numbers

Everything is an object. Basic types: `int` (unlimited precision), `float`, `bool`, `str`, `None`. Because `int` never overflows, "modulo 1 000 000 007" is only needed when the problem asks for it.

| Operator | Meaning |
|---|---|
| `/` | true division (always a float) |
| `//` | floor division (rounds toward minus infinity) |
| `%` | modulo (result has the sign of the divisor, so `-7 % 3 == 2`) |
| `**` | power; `pow(a, b, m)` is fast modular power |
| `divmod(a, b)` | `(a // b, a % b)` |
"""),
C("""print(7 // 2, -7 // 2)          # 3 -4   (floor division)
print(-7 % 3)                   # 2
print(2 ** 100)                 # exact big integer
print(pow(3, 200, 1_000_000_007))
print(divmod(17, 5))            # (3, 2)
print(float("inf") > 10**18)    # True: a safe 'infinity' for min-searches
a, b = 3, 8
a, b = b, a                     # swap without a temp
print(a, b)""", "Numbers, floor division and swapping"),
M("""## 3. Control flow, functions and comprehensions

Blocks are defined by **indentation**. `for x in iterable`, `while`, `if/elif/else`, `break`, `continue`, and `for ... else` (the `else` runs if no `break` happened). Useful built-ins: `range`, `enumerate`, `zip`, `reversed`, `sorted`, `min`, `max`, `sum`, `any`, `all`, `len`, `map`.

**Comprehensions** build collections in one line: `[x * x for x in nums if x % 2 == 0]`, `{x: i for i, x in enumerate(nums)}`, `{x % 3 for x in nums}`.

## 4. Strings

Strings are **immutable** sequences. Slicing `s[a:b]` makes a copy (O(length)). Build large strings with `"".join(list_of_parts)`, never repeated `+=` in a loop.

| Task | Code |
|---|---|
| Reverse | `s[::-1]` |
| Split / join | `s.split()`, `",".join(parts)` |
| Char to number / back | `ord("a")`, `chr(97)` |
| Letter index 0..25 | `ord(c) - ord("a")` |
| Count / find | `s.count("ab")`, `s.find("ab")` (returns -1 if absent) |
| Case and tests | `s.lower()`, `s.isdigit()`, `s.isalpha()` |
"""),
C("""def is_palindrome(s: str) -> bool:
    s = "".join(c.lower() for c in s if c.isalnum())
    return s == s[::-1]

print(is_palindrome("A man, a plan, a canal: Panama"))   # True

parts = []
for c in "abcde":
    parts.append(c.upper())
print("-".join(parts))                                    # A-B-C-D-E

counts = [0] * 26
for c in "banana":
    counts[ord(c) - ord("a")] += 1
print(counts[0], counts[13])                              # 3 2""", "Strings, join and a letter-count list"),
M("""## 5. Lists (dynamic arrays)

A `list` is a dynamic array: `append` and `pop()` at the end are amortised O(1), `a[i]` is O(1), `insert(0, x)` and `pop(0)` are **O(n)** (use `deque` instead). Slices copy. **Careful with 2-D lists:** `[[0] * c] * r` makes `r` references to the *same* row; write `[[0] * c for _ in range(r)]`.
"""),
C("""a = [5, 2, 9, 1]
a.sort()                          # in place, stable
print(a, a[-1], a[1:3])           # [1, 2, 5, 9] 9 [2, 5]

grid = [[0] * 4 for _ in range(3)]
grid[1][2] = 7
print(grid)

bad = [[0] * 2] * 2               # the classic mistake: both rows are the same list
bad[0][0] = 1
print(bad)                        # [[1, 0], [1, 0]]

pairs = [(3, "c"), (1, "a"), (2, "b")]
pairs.sort(key=lambda p: p[0])
print(pairs)
print(sorted(["bb", "a", "ccc"], key=len, reverse=True))""", "Lists, slicing, 2-D grids and sorting with a key"),
M("""## 6. Dictionaries and sets

`dict` is a hash map (insertion-ordered since Python 3.7), `set` a hash set. Average O(1) lookup, insert and delete. Keys must be **hashable**: numbers, strings and tuples are; lists and dicts are not (use a tuple). The `collections` module adds `Counter` (frequencies), `defaultdict` (auto-created values) and `OrderedDict`.
"""),
C("""from collections import Counter, defaultdict

freq = Counter("mississippi")
print(freq.most_common(2))                 # [('i', 4), ('s', 4)]

nums, target = [2, 7, 11, 15], 9
seen = {}                                   # value -> index
for i, x in enumerate(nums):
    if target - x in seen:
        print(seen[target - x], i)          # 0 1
        break
    seen[x] = i

groups = defaultdict(list)
for w in ["to", "be", "or", "not"]:
    groups[len(w)].append(w)
print(dict(groups))                         # {2: ['to', 'be', 'or'], 3: ['not']}

visited = set()
visited.add((0, 1))                         # tuples work as set members
print((0, 1) in visited, len(visited))""", "Counter, two-sum, defaultdict and a set of tuples"),
M("""## 7. Stack, queue, deque and heap

| Need | Use | Notes |
|---|---|---|
| Stack | `list` with `append` / `pop` | O(1) at the end |
| Queue / deque | `collections.deque` | `append`, `appendleft`, `pop`, `popleft` all O(1) |
| Min-heap | `heapq` on a list | `heappush`, `heappop`, `heap[0]` |
| Max-heap | push **negated** values | `heappush(h, -x)`; real value is `-h[0]` |
| Sorted container | `bisect` on a sorted list | `bisect_left`, `bisect_right`, `insort` (insertion is O(n)) |

Heap items can be tuples: `(priority, tie_breaker, payload)`; tuples compare element by element.
"""),
C("""import heapq
from collections import deque
from bisect import bisect_left, bisect_right, insort

stack = [1, 2]; stack.append(3)
print(stack.pop())                         # 3

q = deque([1, 2]); q.append(3)
print(q.popleft(), list(q))                # 1 [2, 3]

h = []
for x in [5, 1, 4]:
    heapq.heappush(h, x)
print(heapq.heappop(h), h[0])              # 1 4

maxh = []
for x in [3, 9, 4]:
    heapq.heappush(maxh, -x)
print(-maxh[0])                            # 9

print(heapq.nlargest(2, [3, 9, 4, 7]))     # [9, 7]

a = [10, 20, 20, 30]
print(bisect_left(a, 20), bisect_right(a, 20))   # 1 3
insort(a, 25); print(a)""", "Stack, deque, heaps and bisect"),
M("""## 8. Sorting and comparing

`list.sort()` (in place) and `sorted(iterable)` (new list) use **Timsort**: O(n log n), stable. Use `key=` to choose what to sort by; tuples sort lexicographically, so `key=lambda p: (-p[1], p[0])` means "by second descending, then first ascending". For a custom comparison function use `functools.cmp_to_key`.

## 9. Classes and nodes

Use a small class or `dataclass`. Python passes **references to objects**: reassigning a parameter does not change the caller's variable, but mutating the object does.
"""),
C("""from dataclasses import dataclass
from typing import Optional

@dataclass
class ListNode:
    val: int
    next: Optional["ListNode"] = None

@dataclass
class TreeNode:
    val: int
    left: Optional["TreeNode"] = None
    right: Optional["TreeNode"] = None

def reverse(head):
    prev = None
    while head:
        head.next, prev, head = prev, head, head.next
    return prev

def height(t):
    return 0 if t is None else 1 + max(height(t.left), height(t.right))

h = ListNode(1, ListNode(2, ListNode(3)))
h = reverse(h)
out = []
while h:
    out.append(h.val); h = h.next
print(out)                                   # [3, 2, 1]
print(height(TreeNode(1, TreeNode(2, TreeNode(3)))))   # 3""", "Dataclass nodes, list reversal and tree height"),
M("""## 10. Recursion limit and speed

Python's default recursion limit is about 1000. For deep recursion raise it (`sys.setrecursionlimit(10**6)`) or rewrite with an explicit stack. Use `functools.lru_cache(None)` for memoised recursion (dynamic programming). Python executes roughly 10⁷ simple operations per second, so for n around 10⁶ prefer built-ins (`sum`, `sorted`, `Counter`) that run in C.

## 11. Complexity cheat sheet (Python)

| Operation | list | deque | dict / set | heapq |
|---|---|---|---|---|
| Index `a[i]` | O(1) | O(n) in the middle | - | - |
| Append / pop at end | O(1) | O(1) | - | push O(log n) |
| Insert / pop at front | O(n) | O(1) | - | - |
| `x in container` | O(n) | O(n) | O(1) avg | O(n) |
| Sort | O(n log n) | - | - | - |
| Min / max | O(n) | O(n) | O(n) | O(1) peek, O(log n) pop |

## 12. Pitfalls and idioms checklist

- `[[0]*c]*r` shares rows; use a comprehension.
- Never use a mutable default argument (`def f(x, seen=[])`); use `None`.
- `list.pop(0)` and `x in list` are O(n); switch to `deque` / `set`.
- `==` compares values and `is` compares identity; use `is` only for `None`.
- `sorted` and `.sort()` are stable; sorting by `key` is faster and clearer than a comparator.
- Integer division of negatives floors (`-7 // 2 == -4`), unlike C, Java and Go.
- Slicing copies; avoid `s[1:]` inside recursion on long strings.
- `for i in range(n)` with `n = 0` runs zero times; check empty inputs explicitly.
- A `dict` is ordered by insertion, but sets are not ordered.
- Use `enumerate` and `zip` instead of index arithmetic.

## 13. A reusable solution template
"""),
C("""import sys

def two_sum(a, target):
    seen = {}
    for j, x in enumerate(a):
        if target - x in seen:
            return seen[target - x] + 1, j + 1
        seen.setdefault(x, j)
    return None

def main():
    data = sys.stdin.read().split()
    n, t = int(data[0]), int(data[1])
    a = list(map(int, data[2:2 + n]))
    r = two_sum(a, t)
    print("-1" if r is None else f"{r[0]} {r[1]}")

main()""", "Template: parse, call a pure function, print"),
M("""## 14. What to practise next

1. Re-type each program without looking, then change inputs to break them.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Anagram Groups](/problems/anagram-groups) and [Merge Meeting Slots](/problems/merge-meeting-slots) in Python.
3. Learn `collections`, `heapq`, `bisect` and `itertools` names by heart: they save minutes in every interview.
"""),
], quiz=[
 Q("Why is `[[0] * c] * r` dangerous for a grid?", ["All rows are the same list", "It is too slow", "It does not compile", "It creates columns"], 0, "Use a list comprehension to create independent rows."),
 Q("Which structure gives O(1) pops from the front?", ["collections.deque", "list", "tuple", "str"], 0, "list.pop(0) is O(n)."),
 Q("How do you get a max-heap from heapq?", ["Push negated values", "Pass reverse=True", "Use sorted()", "It is impossible"], 0, "heapq is a min-heap only."),
 Q("-7 // 2 equals:", ["-4", "-3", "3", "4"], 0, "Floor division rounds toward minus infinity."),
 Q("What makes a value usable as a dict key?", ["It is hashable (e.g. a tuple, not a list)", "It is a string only", "It is positive", "It has a name"], 0, "Mutable containers are not hashable."),
])
