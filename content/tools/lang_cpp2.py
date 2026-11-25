"""Deep version of the C++ lesson (reuses verified programs from lang_cpp.py, adds new ones)."""
from courses_a import Q, L
from lang_cpp import CPP as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "cpp", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 types,2 strings,3 vectors,4 maps,5 stack/queue/heap,6 sorting,7 nodes,8 template
HDR = """#include <algorithm>
#include <climits>
#include <cmath>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;
"""

REFS = C(HDR + """
void byValue(vector<int> v)      { v[0] = 99; }          // works on a COPY
void byRef(vector<int>& v)       { v[0] = 99; }          // works on the caller's vector
int  sumConstRef(const vector<int>& v) {                 // no copy, no modification allowed
    int s = 0; for (int x : v) s += x; return s;
}
void byPointer(int* p)           { *p = 42; }

int main() {
    vector<int> a = {1, 2, 3};
    byValue(a);   cout << a[0] << "\\n";                  // 1
    byRef(a);     cout << a[0] << "\\n";                  // 99
    cout << sumConstRef(a) << "\\n";                      // 104

    int x = 5; byPointer(&x); cout << x << "\\n";         // 42
    int& r = x; r = 7; cout << x << "\\n";                // 7 (a reference is another name)

    vector<int> b = a;                                    // a full copy (value semantics)
    b[1] = -1; cout << a[1] << " " << b[1] << "\\n";      // 2 -1

    int arr[3] = {1, 2, 3};
    int* p = arr;                                         // arrays decay to pointers
    cout << *(p + 1) << " " << p[2] << " " << sizeof(arr) / sizeof(arr[0]) << "\\n";   // 2 3 3

    int* heap = new int(10);                              // heap memory you must free
    cout << *heap << "\\n"; delete heap;
    int* none = nullptr;
    cout << (none == nullptr) << "\\n";                   // 1
    return 0;
}""", "Pass by value, reference, const reference and pointer; copies; arrays and pointers")

HASH = C(HDR + """
struct Point {
    int r, c;
    bool operator==(const Point& o) const { return r == o.r && c == o.c; }
};
struct PointHash {
    size_t operator()(const Point& p) const { return hash<long long>()(1000003LL * p.r + p.c); }
};

int main() {
    unordered_set<Point, PointHash> seen;
    seen.insert({1, 2});
    cout << seen.count({1, 2}) << " " << seen.count({2, 1}) << "\\n";   // 1 0

    set<pair<int, int>> ordered;                    // pair already has < and ==, so a set of pairs just works
    ordered.insert({3, 4}); ordered.insert({1, 9});
    cout << ordered.begin()->first << "\\n";        // 1

    unordered_map<long long, int> encoded;          // encode (r, c) as one number
    int cols = 100;
    encoded[1LL * 5 * cols + 7] = 1;
    cout << encoded.count(5LL * cols + 7) << "\\n"; // 1

    map<int, int> m = {{10, 1}, {20, 2}, {30, 3}};
    auto it = m.upper_bound(20);                    // first key > 20
    cout << it->first << " " << prev(it)->first << "\\n";   // 30 20
    return 0;
}""", "Custom keys: operator==, a hash functor, pairs in set, encoded coordinates")

GRAPH = C(HDR + """
int main() {
    int n = 6;
    vector<pair<int, int>> edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {2, 5}};
    vector<vector<int>> adj(n);
    for (auto& [u, v] : edges) { adj[u].push_back(v); adj[v].push_back(u); }   // undirected

    vector<int> dist(n, -1);
    queue<int> q;
    dist[0] = 0; q.push(0);
    while (!q.empty()) {
        int u = q.front(); q.pop();
        for (int v : adj[u]) if (dist[v] == -1) { dist[v] = dist[u] + 1; q.push(v); }
    }
    for (int d : dist) cout << d << " ";            // 0 1 1 2 3 2
    cout << "\\n";
    return 0;
}""", "A graph as vector<vector<int>> and breadth-first search")

ERR = C(HDR + """
int parsePositive(const string& s) {
    try {
        size_t used = 0;
        int v = stoi(s, &used);                       // throws invalid_argument / out_of_range
        if (used != s.size()) return -1;              // trailing junk
        if (v <= 0) throw invalid_argument("not positive");
        return v;
    } catch (const out_of_range&) {
        return -1;
    } catch (const invalid_argument& e) {
        if (string(e.what()) == "not positive") throw;
        return -1;
    }
}

int main() {
    cout << parsePositive("42") << " " << parsePositive("abc") << "\\n";   // 42 -1
    try { parsePositive("-5"); } catch (const exception& e) { cout << e.what() << "\\n"; }

    vector<int> v = {1, 2, 3};
    try { cout << v.at(5); } catch (const out_of_range&) { cout << "out of range\\n"; }   // at() checks, [] does not

    cout << INT_MAX << " " << INT_MIN << " " << LLONG_MAX << "\\n";
    cout << numeric_limits<double>::infinity() << " " << (0.1 + 0.2 == 0.3) << " "
         << (fabs(0.1 + 0.2 - 0.3) < 1e-9) << "\\n";               // inf 0 1
    vector<int> empty;
    cout << (empty.empty() ? -1 : *max_element(empty.begin(), empty.end())) << "\\n";   // -1
    return 0;
}""", "Exceptions, checked access with at(), limits and edge cases")

CPP2 = L("cpp-for-dsa", "C++ for DSA", "free", 80, "", blocks=[
M("""# C++ for Data Structures and Algorithms: a complete guide

This lesson explains C++ **as you need it for algorithmic problem solving**: how a C++ program is built and runs, every common data type and its limits, the operators and control flow, functions and the three ways to pass arguments, pointers and references, and then every container of the **Standard Template Library (STL)** with its **time and space complexity for every operation**. Every program was compiled and run.

## 1. What C++ is, and why it behaves the way it does

**C++** is a **compiled, statically typed, multi-paradigm** language that gives you control close to the hardware while offering high-level abstractions. Building a program has three steps: the **preprocessor** expands `#include` and macros; the **compiler** translates each source file into machine code (object files); the **linker** joins them into an executable. There is no virtual machine and no garbage collector, so a well-written C++ program is among the fastest you can write, and memory use is predictable.

| Property | What it means | Consequence |
|---|---|---|
| **Compiled to native code** | Runs directly on the CPU | Fastest runtime; a compile step before running |
| **Statically typed** | Types are fixed at compile time | Type errors are compile errors; use `auto` to let the compiler deduce |
| **Value semantics** | Assigning or passing a variable **copies** it by default | Big containers are copied unless you pass by reference |
| **Manual / RAII memory** | You decide object lifetime; destructors run when scope ends | No GC pauses; but dangling pointers and leaks are possible |
| **Undefined behaviour (UB)** | Some mistakes have no defined result | Out-of-range indexing, signed overflow and uninitialised reads can "work" in tests and fail later |
| **Templates** | Code generic over types, resolved at compile time | The STL containers and algorithms are templates |

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| Fastest of the common interview languages | Easy to cause undefined behaviour |
| A powerful STL: containers, `sort`, `lower_bound`, `accumulate` | Error messages are long and cryptic |
| Value semantics: no hidden boxing | You must think about copies, references and lifetimes |
| Standard in competitive programming and systems roles | More syntax to remember than Python or Go |

## 2. Program structure and input

A C++ program has a `main` function returning `int` (0 means success). `#include <vector>` brings in a header; `using namespace std;` lets you write `vector` instead of `std::vector` (fine in interviews). Compile with `g++ -std=c++17 -O2 main.cpp -o main` or `clang++`, run `./main`. Input and output use the streams `cin` and `cout`. By default these are synchronised with C's `stdio`, which makes them slow; the two lines `ios::sync_with_stdio(false); cin.tie(nullptr);` remove that cost. Print newlines with `'\\n'`, not `endl` (`endl` also flushes, which is slow).

> **Portability note.** You will see `#include <bits/stdc++.h>` in competitive code: a GCC-only header that includes everything. It does not exist on macOS/Clang, so this lesson includes the specific headers; that is also the professional habit.
"""),
codes[0],
M("""**How it works.** `cin >> n` skips whitespace and parses an integer; a failed parse sets an error state. `vector<long long> a(n)` creates `n` zero-initialised elements; `for (auto &x : a)` iterates by **reference**, so assigning to `x` would change the vector (without `&` you would modify a copy).

## 3. Data types in depth

### 3.1 Fundamental types

| Type | Typical size | Typical range | Notes |
|---|---|---|---|
| `bool` | 1 byte | `true`, `false` | |
| `char` | 1 byte | -128..127 or 0..255 (implementation-defined) | a small integer; also a character |
| `short` | 2 bytes | ±32,767 | |
| `int` | 4 bytes | -2,147,483,648 .. 2,147,483,647 | the default integer; use `INT_MAX` from `<climits>` |
| `long long` | 8 bytes | about ±9.2 × 10¹⁸ | guaranteed at least 64 bits; use `LLONG_MAX` |
| `unsigned int`, `unsigned long long` | 4, 8 bytes | 0 .. 4.29 × 10⁹, 0 .. 1.8 × 10¹⁹ | wraps around modulo 2ⁿ |
| `float`, `double` | 4, 8 bytes | ~7 and ~15-16 significant digits | IEEE 754 |
| `size_t` | 8 bytes on 64-bit | unsigned | the type of `.size()` and `sizeof` |

The standard fixes only *minimum* sizes; the numbers above hold on mainstream 64-bit compilers. `<cstdint>` gives exact-width types (`int32_t`, `int64_t`, `uint8_t`).

### 3.2 Initialisation and `auto`
**Uninitialised local variables contain garbage**; always initialise: `int x = 0;` or `int x{};`. Global and static variables are zero-initialised. `auto x = 5;` deduces `int`; `auto& r = v[0];` a reference; `for (const auto& s : strings)` avoids copying each string.

### 3.3 Conversions
* **Implicit** conversions can silently lose information: `int x = 3.9;` gives 3 (truncation).
* **Integer promotion:** `char` and `short` become `int` in arithmetic.
* Mixing signed and unsigned is dangerous: in `int a = -1; unsigned b = 1; a < b` the `-1` is converted to a huge unsigned number, so the comparison is **false**.
* Use explicit casts, preferably `static_cast<long long>(x)`, or the C-style `(long long)x`.

### 3.4 Overflow (very important)
* **Signed integer overflow is undefined behaviour.** `INT_MAX + 1` is not guaranteed to wrap; the optimiser may assume it never happens and silently produce wrong code.
* **Unsigned overflow wraps** modulo 2ⁿ (well-defined).
* Therefore: use `long long` whenever a value might exceed about two billion, and **widen before multiplying**: `1LL * a * b`.
* `v.size()` is **unsigned**: `v.size() - 1` on an empty vector is a huge number, and `for (int i = 0; i < v.size(); i++)` mixes signed/unsigned. Cast: `(int)v.size() - 1`.

Floating point behaves as in every language: `0.1 + 0.2 != 0.3`; compare with a tolerance; `1.0 / 0` is infinity, but **integer division by zero is undefined behaviour**. Integer division truncates toward zero and `%` has the sign of the left operand (`-7 / 2 == -3`, `-7 % 3 == -1`); a non-negative modulo is `((a % m) + m) % m`.
"""),
codes[1],
M("""## 4. Operators and control flow

| Group | Operators | Notes |
|---|---|---|
| Arithmetic | `+ - * / %` | `/` on ints truncates toward zero |
| Comparison | `== != < <= > >=` | `<=>` (C++20) returns the ordering |
| Logical | `&& \\|\\| !` | short-circuit |
| Bitwise | `& \\| ^ ~ << >>` | `x & (x - 1)` clears the lowest set bit; `1 << k` is 2ᵏ (use `1LL << k` for k ≥ 31) |
| Assignment | `= += -= ...` | |
| Increment | `++ --` | prefix returns the new value, postfix the old |
| Conditional | `c ? a : b` | |

**Conditionals:** `if / else if / else`, `switch` (needs `break`, otherwise execution *falls through*). **Loops:** `for (int i = 0; i < n; i++)`, range-based `for (auto x : container)`, `while`, `do { } while`. `break`, `continue`, and `goto` exist. Ranges can be iterated by index, by iterator, or with a range-based `for`.

**Counting the cost.** A loop over n elements is O(n); nested loops over the same data O(n²); a loop that halves the range O(log n). Two pointers moving forward at most n times each is O(n) in total.

## 5. Functions, references and pointers

### 5.1 Three ways to pass an argument
| Style | Syntax | Meaning | Cost |
|---|---|---|---|
| **By value** | `void f(vector<int> v)` | the function gets a **copy** | O(size of the object) |
| **By reference** | `void f(vector<int>& v)` | the function works on the caller's object | O(1) |
| **By const reference** | `void f(const vector<int>& v)` | by reference, but read-only | O(1) |
| **By pointer** | `void f(int* p)` | an address; may be `nullptr` | O(1) |

**Rule of thumb:** pass small types (`int`, `double`, `char`) by value and everything bigger by `const&`. Passing a big `vector` or `string` by value inside a recursive function is the classic accidental O(n) per call.

### 5.2 References and pointers
A **reference** (`int& r = x;`) is another name for an existing variable; it cannot be reseated or null. A **pointer** (`int* p = &x;`) stores an address; `*p` reads or writes the target, `p->field` accesses a member, `nullptr` means "points to nothing" (dereferencing it is UB). An array name **decays** to a pointer to its first element, and `p[i]` equals `*(p + i)`. Memory from `new` must be released with `delete` (`new[]` with `delete[]`); modern code uses **RAII** (a vector, a string, or `std::unique_ptr`/`make_unique`, which free memory automatically when they go out of scope).

Run this program and predict every line first:
"""),
REFS,
M("""### 5.3 Recursion and the call stack
Each call needs a stack frame; the default stack (about 1 to 8 MB) supports roughly 10⁴ to 10⁵ frames depending on frame size, after which the program crashes with a segmentation fault. Convert very deep recursion to a loop with an explicit `stack`. Lambdas `[&](int x) { ... }` capture variables from the surrounding scope (`&` by reference, `=` by copy); they are the usual way to write comparators and local helper functions.

## 6. Strings in depth

`std::string` is a **mutable** sequence of `char` stored contiguously (plus a terminating `'\\0'`). Most implementations use **small-string optimisation**: very short strings live inside the object without heap allocation. Strings have **value semantics**: `string b = a;` copies. `s[i]` is O(1) with no bounds check (`s.at(i)` checks and throws), `+=`/`push_back` is amortised O(1) per character, and you can modify characters in place (`s[0] = 'J'`).

### 6.1 Operations and their costs (`n = s.size()`, `m = t.size()`)

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length | `s.size()` | O(1) | unsigned `size_t` |
| Index | `s[i]` | O(1) | no bounds check |
| Append character | `s.push_back(c)`, `s += c` | amortised O(1) | |
| Append string | `s += t` | O(m) amortised | |
| Concatenate to a new string | `s + t` | O(n + m) | |
| Substring | `s.substr(pos, len)` | O(len) | **copies** |
| Compare | `s == t`, `s < t` | O(min(n, m)) | lexicographic |
| Find | `s.find(t)` | O(n·m) worst | returns `string::npos` if absent |
| Insert / erase in the middle | `s.insert(i, t)`, `s.erase(i, k)` | O(n) | shifts |
| Reverse | `reverse(s.begin(), s.end())` | O(n) | in place |
| Convert | `to_string(x)`, `stoi(s)`, `stoll(s)` | O(length) | `stoi` throws on bad input |
| Space | | O(n) | plus a small header |

`char` arithmetic: `c - 'a'` is 0..25 for lowercase letters; `c - '0'` converts a digit; `isdigit`, `isalpha`, `tolower` live in `<cctype>`. Use a plain array `int cnt[26]` for letter frequencies: O(1) update, 104 bytes.
"""),
codes[2],
M("""## 7. Arrays and `vector` in depth

### 7.1 Built-in arrays and `std::array`
A built-in array `int a[100];` has a size fixed at compile time, lives on the stack (large ones, say a million ints, can overflow the stack: make them `static` or global, or use a vector), is **uninitialised** unless you write `= {0}`, and has no bounds checking. `std::array<int, N>` is the same with a nicer interface.

### 7.2 `vector<T>`: the dynamic array
`vector` stores elements **contiguously** on the heap and tracks `size()` (elements) and `capacity()` (room before reallocation). When full, `push_back` allocates a larger block (GCC doubles the capacity, others use 1.5×), **moves or copies every element** (O(n) for that call) and frees the old block. Geometric growth makes the *average* `push_back` **amortised O(1)**. Because storage is contiguous, indexing is O(1) and iteration is cache-friendly. Call `reserve(n)` in advance if you know the final size to avoid regrowth.

| Operation | Time | Space | Notes |
|---|---|---|---|
| `v[i]`, `v.at(i)` | O(1) | O(1) | `at` checks bounds |
| `push_back`, `emplace_back` | amortised O(1), worst O(n) | O(1) amortised | |
| `pop_back` | O(1) | O(1) | |
| `insert(it, x)`, `erase(it)` | O(n) | O(1) | shifts the tail; **invalidates iterators** |
| `front()`, `back()`, `size()`, `empty()` | O(1) | O(1) | |
| `clear()` | O(n) (destroys elements) | capacity kept | |
| `resize(k)` | O(k) | | |
| `find` (`std::find`) | O(n) | O(1) | |
| `sort` | O(n log n) | O(log n) stack | introsort, not stable |
| Copy (`vector b = a;`) | O(n) | O(n) | deep copy |
| Whole structure | | O(n) | capacity may be up to 2× |

**2-D vectors:** `vector<vector<int>> g(r, vector<int>(c, 0));` allocates `r` separate rows (each `O(c)`), total O(r·c) space.

**Iterator invalidation.** After `push_back` (when it reallocates), `insert` or `erase`, previously obtained iterators and references into the vector may be invalid. Do not keep them across such calls; erase while looping by index or by using the iterator returned from `erase`. The erase-remove idiom removes by value: `v.erase(remove(v.begin(), v.end(), x), v.end());` in O(n).

Other sequence containers: `deque<T>` (O(1) at both ends, O(1) random access, stored in blocks), `list<T>` (doubly linked: O(1) insert/erase at a known iterator, but O(n) access and poor cache behaviour), `forward_list<T>` (singly linked).
"""),
codes[3],
M("""## 8. Associative containers in depth

### 8.1 Ordered: `map`, `set`, `multimap`, `multiset`
These are **balanced binary search trees** (typically red-black trees). Keys are kept sorted, so iteration yields ascending order, and the cost of every operation is **O(log n)**. They also answer *nearest-key* questions: `lower_bound(x)` is the first key ≥ x, `upper_bound(x)` the first key > x, `begin()` is the minimum and `prev(end())` the maximum. Each node is a separate allocation, so they use roughly 32 to 48 bytes of overhead per element. A key type needs `operator<` (`pair`, `tuple`, `string` already have it).

### 8.2 Hash-based: `unordered_map`, `unordered_set`
These are **hash tables with chaining**: an array of buckets, each a linked list of entries. The hash of a key chooses the bucket; when the **load factor** (entries ÷ buckets) exceeds 1.0 the table **rehashes** (grows and re-inserts). Average cost of insert, find and erase is **O(1)**, but the worst case is O(n) if many keys collide. Iteration order is arbitrary. Only types with a hash and `operator==` can be keys; `int`, `long long`, `string` work out of the box. For `pair<int,int>` or a struct you write a hash functor (see below), use an ordered `set<pair<int,int>>`, or encode `r * cols + c` into one `long long`.

### 8.3 Operations and costs

| Operation | `unordered_map/set` | `map/set` |
|---|---|---|
| insert, `emplace` | O(1) average, O(n) worst | O(log n) |
| `find`, `count`, `contains` (C++20) | O(1) average | O(log n) |
| `erase(key)` | O(1) average | O(log n) |
| `operator[]` | O(1) average (**inserts a default if missing**) | O(log n) (inserts) |
| min / max key | O(n) | O(1) at `begin()` / last |
| `lower_bound`, `upper_bound` | not available | O(log n) |
| iterate | O(n + buckets), arbitrary order | O(n), sorted |
| space | O(n), a node per entry plus the bucket array | O(n), a node per entry |

**Trap:** `m[key]` creates the entry if it does not exist, which is perfect for counting (`cnt[x]++`) but a bug (and slower) when you only want to test existence: use `m.find(key) != m.end()` or `m.count(key)`.
"""),
codes[4],
HASH,
M("""## 9. Stacks, queues, deques and heaps in depth

### 9.1 Container adaptors
`stack<T>`, `queue<T>` and `priority_queue<T>` are **adaptors**: thin wrappers around `deque` (the default for stack and queue) or `vector` (for the priority queue) that expose only the operations of the abstract type.

| Structure | LIFO / FIFO / priority | Methods | Underlying |
|---|---|---|---|
| `stack<T>` | LIFO | `push`, `pop`, `top`, `empty`, `size` | `deque` |
| `queue<T>` | FIFO | `push`, `pop`, `front`, `back` | `deque` |
| `deque<T>` | both ends | `push_front/back`, `pop_front/back`, `[]` | blocks |
| `priority_queue<T>` | largest first | `push`, `pop`, `top` | `vector` + heap algorithms |

**`pop()` returns nothing** in C++: read `top()` or `front()` first, then pop. Calling `top`/`pop` on an empty container is undefined behaviour, so check `empty()`.

### 9.2 How the heap works
`priority_queue` keeps a binary heap in a vector using `push_heap`/`pop_heap`: element `i` has children `2i + 1` and `2i + 2`; insertion sifts up (at most log n swaps), removal of the top sifts down (at most log n swaps), `top()` is O(1), and building a heap from n elements (`make_heap`, or the constructor taking a range) takes **O(n)**. The default is a **max**-heap; for a min-heap write `priority_queue<int, vector<int>, greater<int>>`. For pairs, `greater<>` and `pair`'s lexicographic order make `(distance, node)` Dijkstra queues easy. There is no `decrease_key`: push a new entry and skip stale ones when popped.

### 9.3 Operations and costs

| Operation | `stack` / `queue` / `deque` | `priority_queue` | Notes |
|---|---|---|---|
| push | O(1) amortised | O(log n) | |
| pop | O(1) | O(log n) | |
| top / front | O(1) | O(1) | |
| search | O(n) (not supported by adaptors) | O(n) (not supported) | |
| build from n items | O(n) | O(n) via `make_heap` | |
| space | O(n) | O(n) | |
"""),
codes[5],
M("""## 10. Algorithms from `<algorithm>` and `<numeric>`

| Function | Purpose | Time | Notes |
|---|---|---|---|
| `sort(b, e [, cmp])` | sort a range | O(n log n) | introsort: quicksort + heapsort fallback; **not stable** |
| `stable_sort` | stable sort | O(n log n) | uses extra memory |
| `partial_sort`, `nth_element` | k smallest / k-th element | O(n log k), O(n) average | `nth_element` is quickselect |
| `reverse`, `rotate`, `unique`, `remove` | rearrange | O(n) | `unique` needs sorted input |
| `min_element`, `max_element`, `accumulate`, `count`, `find` | scan | O(n) | `accumulate(b, e, 0LL)` sums with a `long long` start |
| `lower_bound`, `upper_bound`, `binary_search`, `equal_range` | search a **sorted** range | O(log n) on random-access iterators | |
| `next_permutation` | next lexicographic permutation | O(n) per call | enumerates all permutations |
| `swap`, `iota`, `fill`, `copy` | utilities | O(n) | `iota` fills 0, 1, 2, ... |

A **comparator** must define a **strict weak ordering**: use `<`, never `<=` (a non-strict comparator can crash `sort`). Write comparators as lambdas. On a sorted range, `lower_bound(v.begin(), v.end(), x) - v.begin()` is the index of the first element ≥ x.
"""),
codes[6],
M("""## 11. Structs, classes and the object model

A `struct` is a class whose members are public by default. Constructors initialise; **destructors** run automatically when an object goes out of scope (RAII). A struct of plain data copies member by member. Use `new`/`delete` or smart pointers for linked structures. **Move semantics** (C++11) let containers transfer ownership of heavy objects cheaply instead of copying (`push_back(std::move(x))`); `emplace_back(args...)` constructs in place.

### 11.1 Linked lists and trees
A node holds a value and pointers. Always check for `nullptr` before dereferencing. Insert/delete after a known node is O(1); reaching the i-th node is O(i). Recursion mirrors the tree shape; the height of a tree with n nodes is between log n and n, which is also the recursion depth.
"""),
codes[7],
M("""### 11.2 Graphs
Store a graph as an **adjacency list** `vector<vector<int>> adj(n)` (O(V + E) space) or, for weighted edges, `vector<vector<pair<int,int>>>` of `(neighbour, weight)`. An adjacency matrix `vector<vector<int>>` is O(V²). Breadth-first search with a `queue` visits each vertex and edge once: **O(V + E) time, O(V) space**, and gives shortest paths in an unweighted graph.
"""),
GRAPH,
M("""## 12. Errors and edge cases

C++ has exceptions (`throw`, `try`, `catch`) used by the standard library (`stoi` throws `invalid_argument` or `out_of_range`, `vector::at` throws `out_of_range`), but the core language mostly leaves mistakes **undefined**: indexing outside a vector with `[]`, dereferencing `nullptr`, reading an uninitialised variable and signed overflow compile fine and may appear to work. Tools that help: compile with `-Wall -Wextra`, and test with `-fsanitize=address,undefined`.

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty container | `v[0]`, `*max_element` on empty is UB | check `empty()` first |
| `size() - 1` | unsigned wrap | cast to `int` |
| Overflow | `int` sum/product | `long long`, `1LL * a * b` |
| Negative modulo | `-7 % 3 == -1` | `((a % m) + m) % m` |
| Missing map key | `m[k]` inserts | use `find` / `count` |
| Iterator invalidation | erase/insert while iterating | use returned iterators or indexes |
| Uninitialised variable | garbage values | initialise everything |
"""),
ERR,
M("""## 13. The master complexity cheat sheet

`n` is the number of elements.

| Operation | `vector` | `deque` | `list` | `unordered_map/set` | `map/set` | `priority_queue` | `string` |
|---|---|---|---|---|---|---|---|
| Access by index | O(1) | O(1) | O(n) | n/a | n/a | n/a | O(1) |
| Push at back | O(1)* | O(1) | O(1) | O(1)* insert | O(log n) | O(log n) | O(1)* |
| Push / pop at front | O(n) | O(1) | O(1) | n/a | n/a | n/a | O(n) |
| Insert / erase in the middle | O(n) | O(n) | O(1) at iterator | O(1)* by key | O(log n) | n/a | O(n) |
| Pop at back | O(1) | O(1) | O(1) | n/a | n/a | O(log n) | O(1) |
| Search by value | O(n) | O(n) | O(n) | O(1)* | O(log n) | O(n) | O(n·m) |
| Min / max | O(n) | O(n) | O(n) | O(n) | O(1) | O(1) top | O(n) |
| Sort | O(n log n) | O(n log n) | `list::sort` O(n log n) | n/a | already sorted | n/a | O(n log n) |
| Space | O(n) | O(n) | O(n) + pointers | O(n) + nodes | O(n) + nodes | O(n) | O(n) |

\\* amortised or average.

**What to say in an interview:** "I'll use an `unordered_map` from value to index: each lookup and insert is O(1) on average, so one pass is O(n) time with O(n) extra space. The brute force is O(n²) time and O(1) space; I'm trading memory for time."

## 14. Pitfalls and idioms checklist

1. `v.size() - 1` on an empty vector wraps; cast to `int`.
2. Signed overflow is undefined: use `long long`, `1LL * a * b`.
3. `m[key]` inserts a default; use `find`/`count` to test.
4. Pass big objects by `const&`; accidental copies hide O(n) costs.
5. `v[i]` is unchecked; debug with `at()` or sanitizers.
6. Initialise variables; locals hold garbage.
7. Comparators use strict `<`.
8. Iterators are invalidated by `insert`, `erase` and reallocation.
9. `-7 / 2 == -3` and `-7 % 3 == -1`; normalise the modulo.
10. `top()`/`front()`/`pop()` on an empty adaptor is undefined.
11. A large local array can overflow the stack: use a vector or a global.
12. Use `'\\n'` instead of `endl`; disable `stdio` sync for fast I/O.
13. `1 << 31` overflows an `int`; write `1LL << k`.
14. `unordered_map` can be slow with adversarial keys; `map` is the safe fallback.

## 15. A reusable solution template
"""),
codes[8],
M("""## 16. How C++ compares with the other languages in this chapter

| Topic | C++ | Java | Python | Go |
|---|---|---|---|---|
| Memory | manual / RAII | GC | GC | GC |
| Dynamic array | `vector` | `ArrayList` | `list` | slice |
| Hash map | `unordered_map` | `HashMap` | `dict` | `map` |
| Sorted map | `map` | `TreeMap` | none | none |
| Heap | `priority_queue` (max) | `PriorityQueue` (min) | `heapq` (min) | `container/heap` |
| Integer overflow | UB (signed) | wraps | none | wraps |
| Speed | fastest | fast | slowest | fast |

## 17. Practice plan

1. Re-type each program, then deliberately break it (index out of range, a forgotten `&`) and watch what happens, ideally with `-fsanitize=address,undefined`.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest), [Cheapest Route](/problems/cheapest-route) and [Course Order](/problems/course-order) in C++; write the time and space complexity of each.
3. Memorise `sort`, `lower_bound`, `priority_queue`, `unordered_map`, `accumulate`, `reverse` and `next_permutation`: they cover most interview needs.
"""),
], quiz=OLD["quiz"] + [
 Q("Why pass a big vector as `const vector<int>&`?", ["It avoids an O(n) copy and prevents modification", "It makes it faster to sort", "It is required by the compiler", "It saves stack space only"], 0, "By-value parameters copy the whole container."),
 Q("Which of these is undefined behaviour?", ["Signed integer overflow", "Unsigned integer overflow", "Using `long long`", "Calling `v.at(i)`"], 0, "Unsigned wraps; signed overflow has no defined result."),
 Q("A `std::map` lookup costs:", ["O(log n)", "O(1) average", "O(n)", "O(n log n)"], 0, "It is a balanced binary search tree."),
 Q("How do you obtain a min-heap from priority_queue?", ["priority_queue<int, vector<int>, greater<int>>", "priority_queue<int>", "stack<int>", "sort then pop_back"], 0, "The default is a max-heap."),
])
