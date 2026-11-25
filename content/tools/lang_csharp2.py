"""Deep version of the C# lesson (reuses programs from lang_csharp.py, adds new ones). Not executed: no .NET SDK here."""
from courses_a import Q, L
from lang_csharp import CSHARP as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "csharp", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 overflow,2 strings,3 arrays,4 dict,5 stack/pq,6 sorting,7 nodes,8 template

REFS = C("""using System;
using System.Collections.Generic;

static void Mutate(int[] arr, int x, List<int> list, string s)
{
    arr[0] = 99;          // reference type: the caller sees the change
    x = 99;               // value type: a local copy
    list.Add(7);          // reference type
    s += "!";             // strings are immutable: creates a new local string
}

static void Bump(ref int n) { n++; }                       // ref: pass the variable itself
static bool TryDouble(int x, out int result) { result = x * 2; return true; }   // out: return an extra value

var arr = new[] { 1, 2, 3 };
int x = 5;
var list = new List<int>();
string s = "hi";
Mutate(arr, x, list, s);
Console.WriteLine($"{arr[0]} {x} [{string.Join(",", list)}] {s}");   // 99 5 [7] hi

Bump(ref x);
Console.WriteLine(x);                                       // 6
TryDouble(21, out int d);
Console.WriteLine(d);                                       // 42

var p = new PointClass { X = 1 }; var q = p; q.X = 2;       // class: q and p are the SAME object
var a = new PointStruct { X = 1 }; var b = a; b.X = 2;      // struct: b is a COPY
Console.WriteLine($"{p.X} {a.X}");                          // 2 1

object boxed = 42;                                          // boxing: an int copied into a heap object
int unboxed = (int)boxed;
Console.WriteLine(unboxed);

string s1 = new string('a', 3), s2 = "aaa";
Console.WriteLine($"{s1 == s2} {ReferenceEquals(s1, s2)}");  // True False (== compares content for strings)

int? maybe = null;                                          // nullable value type
Console.WriteLine(maybe ?? -1);                             // -1

class PointClass { public int X; }
struct PointStruct { public int X; }""", "Value versus reference types, ref and out, boxing, string equality and nullable ints")

HASH = C("""using System;
using System.Collections.Generic;

var seen = new HashSet<Point>();
seen.Add(new Point(1, 2));
Console.WriteLine(seen.Contains(new Point(1, 2)));          // True: records have value equality

var set2 = new HashSet<(int, int)> { (0, 1) };
Console.WriteLine(set2.Contains((0, 1)));                   // True: tuples hash by value

var set3 = new HashSet<int[]> { new[] { 1, 2 } };
Console.WriteLine(set3.Contains(new[] { 1, 2 }));           // False: arrays use reference equality

var comparer = EqualityComparer<int[]>.Create(
    (a, b) => a!.AsSpan().SequenceEqual(b),
    a => { var h = new HashCode(); foreach (var v in a) h.Add(v); return h.ToHashCode(); });
var set4 = new HashSet<int[]>(comparer) { new[] { 1, 2 } };
Console.WriteLine(set4.Contains(new[] { 1, 2 }));           // True: a custom comparer

var dict = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase) { ["Apple"] = 1 };
Console.WriteLine(dict["APPLE"]);                           // 1

var ordered = new SortedDictionary<int, string> { [30] = "c", [10] = "a", [20] = "b" };
foreach (var kv in ordered) Console.Write(kv.Key + " ");    // 10 20 30
Console.WriteLine();

record Point(int R, int C);""", "Value equality for records and tuples, reference equality for arrays, custom comparers")

GRAPH = C("""using System;
using System.Collections.Generic;

int n = 6;
var edges = new (int, int)[] { (0, 1), (0, 2), (1, 3), (2, 3), (3, 4), (2, 5) };
var adj = new List<int>[n];
for (int i = 0; i < n; i++) adj[i] = new List<int>();
foreach (var (u, v) in edges) { adj[u].Add(v); adj[v].Add(u); }   // undirected

var dist = new int[n];
Array.Fill(dist, -1);
var q = new Queue<int>();
dist[0] = 0; q.Enqueue(0);
while (q.Count > 0)
{
    int u = q.Dequeue();
    foreach (int v in adj[u])
        if (dist[v] == -1) { dist[v] = dist[u] + 1; q.Enqueue(v); }
}
Console.WriteLine(string.Join(" ", dist));                   // 0 1 1 2 3 2""", "A graph as an array of lists and breadth-first search")

ERR = C("""using System;
using System.Collections.Generic;
using System.Linq;

static int ParsePositive(string s)
{
    if (!int.TryParse(s.Trim(), out int v)) return -1;       // TryParse never throws
    if (v <= 0) throw new ArgumentOutOfRangeException(nameof(s), "not positive");
    return v;
}

Console.WriteLine($"{ParsePositive(" 42 ")} {ParsePositive("abc")}");   // 42 -1
try { ParsePositive("-5"); } catch (ArgumentOutOfRangeException e) { Console.WriteLine(e.ParamName); }

int[] a = new int[3];
try { a[3] = 1; } catch (IndexOutOfRangeException) { Console.WriteLine("index error"); }
var d = new Dictionary<string, int>();
try { Console.WriteLine(d["x"]); } catch (KeyNotFoundException) { Console.WriteLine("key error"); }
try { Console.WriteLine(1 / int.Parse("0")); } catch (DivideByZeroException) { Console.WriteLine("divide by zero"); }
Console.WriteLine(1.0 / 0);                                  // Infinity (no exception for doubles)

var empty = new List<int>();
Console.WriteLine(empty.Count == 0 ? -1 : empty.Max());      // Max() on an empty list throws, so check first
Console.WriteLine(empty.DefaultIfEmpty(-1).Max());           // -1
Console.WriteLine(new[] { 4, 8, 15 }.FirstOrDefault(x => x > 5));   // 8

string? name = null;
Console.WriteLine(name?.Length ?? 0);                        // 0 (null-conditional and null-coalescing)
try { checked { int big = int.MaxValue; big++; } } catch (OverflowException) { Console.WriteLine("overflow"); }""", "TryParse, exceptions, empty-sequence LINQ pitfalls and null operators")

CSHARP2 = L("csharp-for-dsa", "C# for DSA", "free", 80, "", blocks=[
M("""# C# for Data Structures and Algorithms: a complete guide

This lesson explains C# **as you need it for algorithmic problem solving**: what C# and .NET are and how a program runs, every numeric type and the overflow rules, value types versus reference types (the most important idea in C#), control flow, methods and parameter passing, strings, and every collection in `System.Collections.Generic` with the **time and space complexity of every operation**.

> **Verification note.** The Java, Python, C++, JavaScript and TypeScript lessons in this chapter were compiled/type-checked and run before publishing. This machine has no .NET SDK, so every C# program here was written carefully against the documented APIs (.NET 8, C# 12) but **has not been executed**. Create a console project (`dotnet new console`), paste a program, run it, and report anything that does not compile.

## 1. What C# is, and why it behaves the way it does

**C#** is a modern, statically typed, object-oriented and functional-friendly language created by Microsoft for the **.NET** platform. The compiler translates C# into **IL** (intermediate language) stored in assemblies; the **CLR** (Common Language Runtime) **JIT-compiles** IL to machine code as the program runs and manages memory with a **generational garbage collector** (young objects are collected cheaply and often, old ones rarely). C# is used for backend services (ASP.NET), desktop software, games (Unity) and cloud tools.

| Property | What it means | Consequence |
|---|---|---|
| **Statically typed** | Types are checked at compile time | Type mistakes are compile errors; `var` infers the type |
| **Value types vs reference types** | `int`, `struct`, tuples are values (copied); `class`, arrays, strings, collections are references | Assignment semantics differ; this explains most surprises |
| **Garbage collected** | Unreachable objects are freed automatically | No manual freeing |
| **Rich standard library** | Collections, LINQ, async, a built-in `PriorityQueue` | Most structures exist already |
| **Nullable reference types** | The compiler tracks which references may be `null` | Fewer `NullReferenceException`s |

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| Consistent, well-named collections and LINQ | More ceremony than Python |
| Built-in `PriorityQueue`, `SortedSet`, `SortedDictionary` | `int` arithmetic **wraps silently** by default |
| Value types and `struct`s avoid allocations | `Dictionary` indexer throws on a missing key |
| Great tooling and type safety | No built-in max-heap flag (use a comparer) |

## 2. Program structure and input

Modern C# (version 9+) allows **top-level statements**: the file's statements form the program, with no `class Program` or `Main`, and you can declare methods ("local functions", marked `static`) and types after them. Run with `dotnet run`. Read input with `Console.ReadLine()` (returns `null` at end of input; the `!` tells the compiler you expect a value). `string.Split(' ', StringSplitOptions.RemoveEmptyEntries)` splits on spaces; `int.Parse` converts (throws `FormatException` on bad text, while `int.TryParse` returns a bool). For large input wrap `Console.In` in a `StreamReader` with a big buffer; `Console.WriteLine` per line is slow, so use a `StringBuilder`.
"""),
codes[0],
M("""**How it works.** `Select(int.Parse)` is **LINQ**: it lazily maps each string to an int; `ToArray()` runs the query once and stores the result. LINQ methods are convenient and allocate small objects; in a hot loop a plain `for` is faster.

## 3. Data types in depth

### 3.1 Value types and reference types
This is the central idea in C#.

| | **Value types** | **Reference types** |
|---|---|---|
| Examples | `int`, `long`, `double`, `bool`, `char`, `decimal`, `struct`, `enum`, tuples `(int, int)` | `class`, `string`, arrays, `List<T>`, `Dictionary<K,V>`, delegates |
| A variable holds | the value itself | a reference to an object on the heap (or `null`) |
| Assignment `b = a` | **copies** the value | copies the **reference**: both names point to one object |
| Default value | zero / false | `null` |
| `==` | compares values | identity by default (but `string` and records compare content) |

Locals of value types usually live on the stack (or inside the object that contains them); reference-type objects live on the garbage-collected heap. **Boxing** converts a value type to `object` (allocates and copies); **unboxing** copies it back: avoid it in hot loops (non-generic collections box; `List<int>` does not).

### 3.2 Built-in numeric types

| Type | Size | Range | Notes |
|---|---|---|---|
| `sbyte`, `byte` | 8 bits | -128..127, 0..255 | |
| `short`, `ushort` | 16 bits | ±32,767, 0..65,535 | |
| `int` | 32 bits | -2,147,483,648 .. 2,147,483,647 | the default integer |
| `uint` | 32 bits | 0 .. 4,294,967,295 | |
| `long` | 64 bits | about ±9.2 × 10¹⁸ | use for sums and products |
| `ulong` | 64 bits | 0 .. 1.8 × 10¹⁹ | |
| `float` | 32 bits | ~7 digits | |
| `double` | 64 bits | ~15-16 digits | default for decimals |
| `decimal` | 128 bits | 28-29 digits, exact base-10 | money |
| `char` | 16 bits | one UTF-16 unit | behaves like a number |
| `bool` | 1 byte | `true`/`false` | |
| `BigInteger` | unlimited | `System.Numerics` | exact big integers |
| `nint`, `nuint` | native size | | |

Literals: `1_000_000`, `123L`, `1.5f`, `1.5m`, `'a'`, `0xFF`, `0b1010`. `var x = 5;` infers `int`. Locals must be **definitely assigned** before use. Fields and array elements start at zero/false/`null`.

### 3.3 Conversions
* **Implicit** (widening, lossless): `int` to `long` to `double`.
* **Explicit** (narrowing, may lose data): a cast `(int)3.9` is `3` (truncation); `(byte)300` is `44` (wraps).
* `int` arithmetic is done in `int` even when assigned to a `long`: `long x = a * b;` multiplies as `int` first. Cast first: `(long)a * b`.
* `Convert.ToInt32`, `int.Parse`, `int.TryParse(s, out v)` convert text; `ToString()` formats.

### 3.4 Overflow
Integer arithmetic **wraps around silently** in the default **unchecked** context: `int.MaxValue + 1 == int.MinValue`. Use `checked { ... }` (or the `checked(expr)` operator, or the project setting) to throw `OverflowException`, which is excellent while testing. Constant expressions that overflow are compile errors. Division by zero throws `DivideByZeroException` for integers and gives `Infinity` or `NaN` for floating point. Integer division truncates toward zero and `%` keeps the dividend's sign (`-7 / 2 == -3`, `-7 % 3 == -1`); a non-negative modulo is `((a % m) + m) % m`. Floating point is IEEE 754: `0.1 + 0.2 != 0.3`, so compare with a tolerance or use `decimal`. `int.MinValue` has no positive counterpart: `Math.Abs(int.MinValue)` throws.
"""),
codes[1],
M("""## 4. Operators and control flow

| Group | Operators | Notes |
|---|---|---|
| Arithmetic | `+ - * / %` | `/` on ints truncates |
| Comparison | `== != < <= > >=` | |
| Logical | `&& \\|\\| !` | short-circuit; `&` and `\\|` on bools do not short-circuit |
| Bitwise | `& \\| ^ ~ << >> >>>` | `>>>` unsigned shift (C# 11) |
| Null operators | `?.` (null-conditional), `??` (coalescing), `??=` | `name?.Length ?? 0` |
| Conditional | `c ? a : b` | |
| Pattern matching | `x is int n`, `switch` expressions | |

**Conditionals:** `if / else`, `switch` statement or switch expression (`x switch { 1 => "a", _ => "b" }`). **Loops:** `for`, `foreach (var x in items)`, `while`, `do/while`, `break`, `continue`, `goto`. You cannot modify a collection while enumerating it with `foreach` (`InvalidOperationException`). **Counting the cost:** one loop over n is O(n), nested loops O(n²), halving the range O(log n); LINQ calls inside loops can hide O(n) work (`list.Contains`, `Count()`, `Max()` on an enumerable).

## 5. Methods, parameters and references

Methods have a return type, name and parameters; local functions can be declared inside other code (mark `static` to forbid capturing variables). Features: optional parameters, named arguments (`Foo(k: 3)`), expression-bodied members (`int Sq(int x) => x * x;`), tuples to return several values (`(int Lo, int Hi) Range()`), and `params int[] xs`.

### 5.1 How arguments are passed
By default **everything is passed by value**: a copy of the variable. For a value type that is a copy of the data; for a reference type it is a copy of the **reference**, so the method can mutate the shared object but cannot make the caller's variable point elsewhere. The keywords change this: **`ref`** passes the variable itself (the callee may read and reassign it), **`out`** is a return value the callee must assign, and **`in`** passes by read-only reference (avoids copying a big struct). The program below shows each, together with class versus struct, boxing, string equality and nullable value types; predict the output first.
"""),
REFS,
M("""### 5.2 Recursion
Each call uses a stack frame; the default 1 MB stack gives roughly 10⁴ to 10⁵ frames before `StackOverflowException` (which cannot be caught and ends the process). Convert very deep recursion to a loop with an explicit `Stack<T>`. Memoise with a `Dictionary` or an array.

## 6. Strings in depth

`string` is an **immutable** reference type holding UTF-16 code units. Every "change" creates a new string, so appending in a loop is O(n²); use `StringBuilder` (amortised O(1) per append). Literals are **interned** (equal literals share one object). Unlike Java, `==` on strings compares **content**; `ReferenceEquals` compares identity. Strings can be indexed (`s[i]` is a `char`), sliced with ranges (`s[1..4]`, C# 8), and interpolated (`$"n={n}"`).

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length | `s.Length` | O(1) | |
| Index | `s[i]` | O(1) | `IndexOutOfRangeException` if invalid |
| Substring | `s.Substring(a, len)`, `s[a..b]` | O(len) | copies |
| Concatenate | `s + t`, `string.Concat` | O(n + m) | new string |
| Join | `string.Join(",", parts)` | O(total) | |
| Compare | `s == t`, `string.CompareOrdinal` | O(min(n, m)) | `==` is ordinal and case sensitive |
| Search | `IndexOf`, `Contains`, `StartsWith` | O(n·m) worst | culture-sensitive by default for some overloads: prefer `StringComparison.Ordinal` |
| Split | `s.Split(' ')` | O(n) | allocates an array |
| Reverse | `new string(s.Reverse().ToArray())` | O(n) | LINQ |
| `StringBuilder.Append` | | amortised O(1) | `Insert(0, ..)` is O(n) |
| Space | | O(n) | 2 bytes per `char` plus overhead |

`char` arithmetic: `c - 'a'` is 0..25, `c - '0'` a digit; `char.IsDigit`, `IsLetter`, `ToUpper`. A letter-frequency table is an `int[26]`: O(1) update, 26 ints.
"""),
codes[2],
M("""## 7. Arrays and `List<T>` in depth

### 7.1 Arrays
`int[] a = new int[n];` allocates `n` zero-initialised ints in one contiguous block: `a[i]` is O(1) and bounds-checked (`IndexOutOfRangeException`), `a.Length` is the length. Arrays are **reference types**, so `int[] b = a;` aliases; `a.Clone()` or `a[..]` makes a **shallow** copy. **Rectangular** `int[,]` allocates one block of `r × c`; **jagged** `int[][]` is an array of row arrays (each row allocated separately, rows may differ in length; usually the faster and more flexible choice). Static helpers in `Array`: `Sort`, `Fill`, `Reverse`, `IndexOf`, `BinarySearch`, `Copy`, `Resize`.

### 7.2 `List<T>`
A `List<T>` wraps an array that **doubles** when full (copying the elements, O(n) for that one `Add`), so `Add` is **amortised O(1)**.

| Operation | Time | Space | Notes |
|---|---|---|---|
| `list[i]` get/set | O(1) | O(1) | |
| `Add(x)` | amortised O(1), worst O(n) | O(1) amortised | |
| `Insert(i, x)` | O(n) | O(1) | shifts the tail |
| `RemoveAt(i)` | O(n) | O(1) | `RemoveAt(Count - 1)` is O(1) |
| `Remove(x)`, `Contains(x)`, `IndexOf(x)` | O(n) | O(1) | linear scan with `Equals` |
| `Count` | O(1) | O(1) | |
| `Sort()` | O(n log n) | O(log n) | introsort, **not stable** |
| `BinarySearch` | O(log n) | O(1) | sorted input; returns `~insertionPoint` if absent |
| `Reverse()` | O(n) | O(1) | |
| `ToArray()`, copy | O(n) | O(n) | |
| Whole structure | | O(n) | up to 2× capacity |

`List<T>` of a value type stores the values contiguously (no boxing). Set `capacity` in the constructor when you know the size. Do not add or remove while iterating with `foreach`.
"""),
codes[3],
M("""## 8. `Dictionary`, `HashSet` and sorted collections in depth

### 8.1 How `Dictionary<K,V>` works
A `Dictionary` is a **hash table with separate chaining stored in arrays**: a `buckets` array of indexes plus an `entries` array holding key, value, hash and a `next` index for collisions. A key's `GetHashCode()` picks the bucket; `Equals` confirms the match. When the entries array fills, the table **resizes to the next prime** at least double the size and rehashes (O(n), amortised away). Insert, lookup and delete are **O(1) on average**, O(n) in the worst case with terrible hashes. Iteration follows the entries array (usually insertion order if nothing was removed, but **not guaranteed**).

**Keys** need a consistent `Equals` and `GetHashCode`. Strings, numbers, `record`s, value tuples and enums have value-based equality; **arrays and plain classes use reference equality** unless you override or supply an `IEqualityComparer`. The indexer `d[k]` **throws** `KeyNotFoundException` if missing: use `TryGetValue`, `ContainsKey`, `GetValueOrDefault` or `TryAdd`.

`HashSet<T>` is the same table without values. `SortedDictionary<K,V>` and `SortedSet<T>` are **red-black trees**: O(log n) for add/remove/lookup, keys iterate in order, and `SortedSet` offers `Min`, `Max`, `GetViewBetween`. `SortedList<K,V>` is two sorted arrays: O(log n) lookup but **O(n) insertion**.

| Operation | `Dictionary` / `HashSet` | `SortedDictionary` / `SortedSet` | `SortedList` |
|---|---|---|---|
| Add | O(1) avg | O(log n) | O(n) |
| Lookup / `Contains` | O(1) avg | O(log n) | O(log n) |
| Remove | O(1) avg | O(log n) | O(n) |
| Min / max key | O(n) | O(log n) (`Min`/`Max`) | O(1) |
| Iterate | O(n) | O(n) in key order | O(n) in key order |
| Space | O(n) | O(n) node objects | O(n) arrays |
"""),
codes[4],
HASH,
M("""## 9. Stacks, queues, deques and priority queues in depth

| Structure | Class | Operations | Time | Implementation |
|---|---|---|---|---|
| Stack (LIFO) | `Stack<T>` | `Push`, `Pop`, `Peek`, `Count` | O(1) amortised each | array that doubles |
| Queue (FIFO) | `Queue<T>` | `Enqueue`, `Dequeue`, `Peek` | O(1) amortised each | **circular array** |
| Deque | `LinkedList<T>` | `AddFirst/Last`, `RemoveFirst/Last` | O(1) each | doubly linked nodes |
| Priority queue | `PriorityQueue<TElement, TPriority>` (.NET 6+) | `Enqueue`, `Dequeue`, `Peek` | O(log n), O(log n), O(1) | **array-based 4-ary min-heap** |

Calling `Pop`/`Dequeue`/`Peek` on an empty collection throws `InvalidOperationException`: check `Count` or use `TryPop`/`TryDequeue`/`TryPeek`. **How the heap works:** a complete tree stored in an array where each parent is ≤ its children (.NET uses four children per node, so the tree is shallower); **Enqueue** appends and sifts up; **Dequeue** moves the last element to the root and sifts down, both O(log n); **Peek** is the root, O(1); **building** from n items (`new PriorityQueue(items)`) is O(n). It is a **min**-heap by priority; for a max-heap pass a comparer that reverses the comparison. There is no decrease-key: enqueue a new entry and ignore stale ones. To keep the k largest of n items use a min-heap of size k: O(n log k) time, O(k) space.

| Operation | `Stack` / `Queue` | `LinkedList` | `PriorityQueue` |
|---|---|---|---|
| Add | O(1)* | O(1) | O(log n) |
| Remove next | O(1) | O(1) | O(log n) |
| Peek | O(1) | O(1) | O(1) |
| Search | O(n) | O(n) | O(n) |
| Build from n items | O(n) | O(n) | O(n) |
| Space | O(n) | O(n) + node objects | O(n) |
"""),
codes[5],
M("""## 10. Sorting and searching in depth

`Array.Sort` and `List.Sort` use **introsort** (quicksort with a heapsort fallback and insertion sort for small ranges): O(n log n) worst case, in place, **not stable**. LINQ `OrderBy`/`ThenBy` are **stable** (they use a merge-style approach), O(n log n), and return a new sequence (`ToList()` to materialise). A custom order is a `Comparison<T>` lambda `(a, b) => ...` returning negative, zero or positive, or an `IComparer<T>` via `Comparer<T>.Create`. **Never** subtract in a comparer (`a - b` can overflow): use `a.CompareTo(b)`. `Array.BinarySearch`/`List.BinarySearch` need sorted input, run in O(log n) and return the index, or the **bitwise complement of the insertion point** (`~result`) when absent; they may return any one of several equal elements, so hand-write a `lo`/`hi` loop for first or last position.
"""),
codes[6],
M("""## 11. Classes, structs, records and the object model

* A **class** is a reference type; `new` allocates it on the heap; the garbage collector reclaims it when unreachable. A **struct** is a value type, copied on assignment: good for small immutable data (a point), dangerous when large or mutated through copies. A **record** (C# 9+) is a class (or `record struct`) with **value equality**, `ToString` and `with`-copy generated for you.
* **Equality and hashing:** to use your type as a dictionary or set key, override `Equals` and `GetHashCode` together (or use a `record`).
* **Nullable reference types** (`ListNode?`) let the compiler warn about possible `null` dereferences; `null` access throws `NullReferenceException`.
* **Properties** (`public int X { get; set; }`) replace getters and setters; `readonly` and `init` express immutability.
* **Interfaces and generics:** program to `IList<T>`, `IDictionary<K,V>` or `IEnumerable<T>`; generics are **reified** (the runtime keeps the type argument), unlike Java.
* **Garbage collection:** generations 0, 1 and 2; short-lived objects are cheap. Avoid allocating inside a hot loop (LINQ lambdas, string concatenation, boxing).

### 11.1 Linked lists and trees
A node class holds a value and references; reaching the i-th node costs O(i), inserting after a held node O(1). Check for `null` before dereferencing.
"""),
codes[7],
M("""### 11.2 Graphs
Store a graph as an **adjacency list** `List<int>[]` or `List<List<int>>` (O(V + E) space) or `List<(int to, int w)>[]` for weights; an adjacency matrix `int[,]` is O(V²). Breadth-first search with a `Queue<int>` visits each vertex and edge once: **O(V + E) time, O(V) space**, and yields shortest paths in an unweighted graph. For weighted graphs use Dijkstra with `PriorityQueue<int, long>`.
"""),
GRAPH,
M("""## 12. Errors and edge cases

C# reports runtime problems with **exceptions**: `IndexOutOfRangeException`, `KeyNotFoundException`, `NullReferenceException`, `DivideByZeroException`, `InvalidOperationException` (empty `Stack`/`Queue`, `Max()` of an empty sequence), `FormatException`, `OverflowException` (checked only). Use `try / catch / finally` (or `using` for resources). Prefer `TryParse`, `TryGetValue`, `TryPop` patterns over catching, because exceptions are slow when they actually occur.

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty collection | `Max()`, `First()`, `Pop()` throw | check `Count`, or `DefaultIfEmpty`, `FirstOrDefault`, `TryPop` |
| Missing key | `d[k]` throws | `TryGetValue`, `GetValueOrDefault` |
| Integer overflow | wraps silently | `long`, `checked` |
| Null | `NullReferenceException` | nullable reference types, `?.`, `??` |
| Array equality | `==` compares references | `SequenceEqual`, comparer, tuple/record key |
| Modifying while iterating | `InvalidOperationException` | iterate a copy or by index |
| Culture-sensitive string ops | surprising results | `StringComparison.Ordinal` |
"""),
ERR,
M("""## 13. The master complexity cheat sheet

`n` is the number of elements.

| Operation | `T[]` | `List<T>` | `LinkedList<T>` | `Dictionary` / `HashSet` | `SortedDictionary` / `SortedSet` | `Stack` / `Queue` | `PriorityQueue` |
|---|---|---|---|---|---|---|---|
| Index | O(1) | O(1) | O(n) | n/a | n/a | n/a | n/a |
| Add at end | n/a | O(1)* | O(1) | O(1)* avg | O(log n) | O(1)* | O(log n) |
| Insert at front | n/a | O(n) | O(1) | n/a | n/a | n/a | n/a |
| Insert in the middle | n/a | O(n) | O(1) at node | n/a | n/a | n/a | n/a |
| Remove at end / next | n/a | O(1) | O(1) | O(1) avg by key | O(log n) | O(1) | O(log n) |
| Remove at front | n/a | O(n) | O(1) | n/a | n/a | queue O(1) | n/a |
| Search by value | O(n) | O(n) | O(n) | O(1) avg by key | O(log n) | O(n) | O(n) |
| Min / max | O(n) | O(n) | O(n) | O(n) | O(log n) | O(n) | O(1) peek |
| Sort | O(n log n) | O(n log n) | n/a | n/a | in order | n/a | n/a |
| Space | O(n) | O(n) | O(n) + nodes | O(n) + entries | O(n) + nodes | O(n) | O(n) |

\\* amortised or average.

**What to say in an interview:** "I'll use a `Dictionary<int, int>` from value to index: each `TryGetValue` and insert is O(1) on average, so one pass is O(n) time and O(n) extra space. The brute force is O(n²) time and O(1) space; I'm trading memory for time."

## 14. Pitfalls and idioms checklist

1. `Dictionary` indexer throws on a missing key: use `TryGetValue`.
2. `int` overflow wraps silently: use `long` or `checked`; cast before multiplying.
3. `Array.Sort`/`List.Sort` are not stable; LINQ `OrderBy` is.
4. Never write comparers as `a - b`; use `CompareTo`.
5. `PriorityQueue` dequeues the smallest priority; reverse the comparer for a max-heap.
6. Do not modify a collection inside `foreach` over it.
7. Strings are immutable: use `StringBuilder` in loops.
8. `==` on arrays and most classes compares references; strings and records compare content.
9. A `struct` is copied on assignment; a `class` is shared.
10. `Max()`/`First()`/`Pop()` on empty sources throw: check first.
11. LINQ allocates; prefer loops in hot paths.
12. Use `StringComparison.Ordinal` for deterministic string comparison.
13. Boxing (`object`, non-generic collections) hurts performance.
14. A `StackOverflowException` cannot be caught: avoid deep recursion.

## 15. A reusable solution template
"""),
codes[8],
M("""## 16. How C# compares with the other languages in this chapter

| Topic | C# | Java | TypeScript | Go |
|---|---|---|---|---|
| Value types | `struct`, primitives | primitives only | primitives | structs, arrays |
| Generics | reified | erased | erased | yes (1.18+) |
| Dynamic array | `List<T>` | `ArrayList` | `T[]` | slice |
| Hash map | `Dictionary` | `HashMap` | `Map` | `map` |
| Sorted map | `SortedDictionary` | `TreeMap` | none | none |
| Heap | `PriorityQueue<E,P>` (min) | `PriorityQueue` (min) | write your own | `container/heap` |
| Overflow | wraps (unchecked) | wraps | none (floats) | wraps |

## 17. Practice plan

1. Create a console project with `dotnet new console`, paste each program, run it, and fix anything your SDK version complains about.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest), [Course Order](/problems/course-order) and [Island Counter](/problems/island-counter) in C#, writing each time and space complexity.
3. Learn `Dictionary`, `HashSet`, `PriorityQueue`, `List.Sort` and LINQ `OrderBy`/`GroupBy`/`Select` by heart.
"""),
], quiz=OLD["quiz"] + [
 Q("When you assign one struct variable to another:", ["The data is copied", "Both refer to one object", "A compile error occurs", "It is boxed"], 0, "Structs are value types."),
 Q("What does the `out` keyword do?", ["The callee must assign a value that the caller receives", "Passes by read-only reference", "Marks a field private", "Deletes the variable"], 0, "out is an extra return value."),
 Q("The indexer `dict[key]` on a missing key:", ["Throws KeyNotFoundException", "Returns default", "Returns null", "Inserts it"], 0, "Use TryGetValue."),
 Q(".NET's PriorityQueue is by default a:", ["Min-heap by priority", "Max-heap", "Sorted list", "Hash set"], 0, "Reverse the comparer for a max-heap."),
])
