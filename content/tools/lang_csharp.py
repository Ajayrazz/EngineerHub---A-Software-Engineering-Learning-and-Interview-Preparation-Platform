from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "csharp", code, cap)

CSHARP = L("csharp-for-dsa", "C# for DSA", "free", 30, "", blocks=[
M("""## Why C# for interviews?

C# is a **statically typed, garbage-collected** language on the .NET runtime, widely used for backend services, enterprise software, Windows tools and games (Unity). Its standard library (`System.Collections.Generic`) offers all the structures you need, including a built-in **`PriorityQueue`** since .NET 6, and **LINQ** gives concise queries over collections.

| Strengths | Weaknesses |
|---|---|
| Rich, consistent collections and LINQ | More ceremony than Python |
| Built-in `PriorityQueue`, `SortedSet`, `SortedDictionary` | `int` arithmetic wraps silently (unless `checked`) |
| Value types (`struct`) and records | `Dictionary` throws on a missing key with the indexer |
| Great tooling and type safety | No built-in max-heap flag (use a comparer) |

> **Verification note:** the Java, Python, C++, JavaScript and TypeScript programs in this chapter were compiled and run before publishing. This machine has no .NET SDK, so the C# programs were written carefully against the documented APIs but **not executed**; run each one in your own environment (`dotnet new console`) and report any problem.

## 1. Program skeleton and input

Modern C# (9+) allows **top-level statements**: no class or `Main` needed. Read input with `Console.ReadLine()` (returns `null` at end of input). Split with `Split(' ', StringSplitOptions.RemoveEmptyEntries)`.
"""),
C("""using System;
using System.Linq;

var first = Console.ReadLine()!.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(int.Parse).ToArray();
int n = first[0], k = first[1];
var nums = Console.ReadLine()!.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(long.Parse).ToArray();
long sum = nums.Sum();
Console.WriteLine($"n={n} k={k} sum={sum}");""", "Top-level program reading two lines"),
M("""## 2. Types, overflow and modulo

| Type | Size | Notes |
|---|---|---|
| `int` | 32 bits | about ±2.1 × 10⁹; **wraps silently** by default |
| `long` | 64 bits | use for sums and products |
| `double`, `decimal` | 64 / 128 bits | `decimal` is exact for money |
| `char` | 16 bits | UTF-16 code unit; behaves like a number |
| `bool`, `string` | | `string` is immutable |
| `BigInteger` | unlimited | `System.Numerics` |

`checked { ... }` makes arithmetic throw `OverflowException` on overflow: handy while testing. Integer division truncates toward zero; `%` keeps the dividend's sign, so a non-negative modulo is `((a % m) + m) % m`; `Math.DivRem` returns both.
"""),
C("""using System;
using System.Numerics;

int big = int.MaxValue;
Console.WriteLine(unchecked(big + 1));              // -2147483648 (wraps)
Console.WriteLine((long)big + 1);                   // 2147483648 (cast first)

Console.WriteLine($"{7 / 2} {-7 / 2} {-7 % 3}");    // 3 -3 -1
Console.WriteLine(((-7 % 3) + 3) % 3);              // 2

const long MOD = 1_000_000_007L;
long x = 123456789L * 987654321L % MOD;
Console.WriteLine(x);

BigInteger p = BigInteger.Pow(2, 100);
Console.WriteLine(p);

try { checked { int y = int.MaxValue; y += 1; Console.WriteLine(y); } }
catch (OverflowException) { Console.WriteLine("overflow detected"); }""", "Overflow, division, modulo, BigInteger and checked"),
M("""## 3. Control flow, methods and references

`if`, `switch` (also as an expression), `for`, `foreach`, `while`, `do/while`, `break`, `continue`. Methods can be **local functions** inside others. Arguments are passed by value; for classes the value is a reference, and `ref`/`out` pass a variable by reference. `var` infers the type.

## 4. Strings

`string` is **immutable**; use `StringBuilder` for repeated appends. `s[i]` is a `char`.

| Task | Code |
|---|---|
| Length, char | `s.Length`, `s[i]` |
| Compare content | `s == t` (works), `string.Equals` |
| Substring | `s.Substring(a, len)` or `s[a..b]` (range) |
| Search | `s.IndexOf("ab")` (-1 if absent), `s.Contains("ab")` |
| Split / join | `s.Split(' ')`, `string.Join(",", parts)` |
| Reverse | `new string(s.Reverse().ToArray())` (LINQ) |
| Letter index | `c - 'a'` |
"""),
C("""using System;
using System.Linq;
using System.Text;

static bool IsPalindrome(string s)
{
    int i = 0, j = s.Length - 1;
    while (i < j) if (s[i++] != s[j--]) return false;
    return true;
}

Console.WriteLine(IsPalindrome("level"));                  // True

var sb = new StringBuilder();
for (char c = 'a'; c <= 'e'; c++) sb.Append(c).Append('-');
sb.Length--;                                               // drop the last '-'
Console.WriteLine(sb);                                     // a-b-c-d-e

int[] counts = new int[26];
foreach (char c in "banana") counts[c - 'a']++;
Console.WriteLine($"{counts[0]} {counts['n' - 'a']}");     // 3 2

Console.WriteLine(new string("hello".Reverse().ToArray()));   // olleh""", "Strings, StringBuilder and a counting array"),
M("""## 5. Arrays and `List<T>`

Arrays have a fixed length (`int[] a = new int[n];` zero-filled). Rectangular 2-D arrays use `int[,] g = new int[r, c];`; **jagged** arrays (`int[][]`) are arrays of arrays. `List<T>` is the dynamic array: `Add` (amortised O(1)), indexer O(1), `Insert`/`RemoveAt` O(n), `Contains` O(n), `Sort`, `Reverse`, `BinarySearch`, `IndexOf`. `Array` has static helpers: `Array.Sort`, `Array.Fill`, `Array.Reverse`, `Array.IndexOf`.
"""),
C("""using System;
using System.Collections.Generic;

int[] a = { 5, 2, 9, 1 };
Array.Sort(a);
Console.WriteLine(string.Join(" ", a));                    // 1 2 5 9

int[,] grid = new int[3, 4];
grid[1, 2] = 7;
Console.WriteLine($"{grid[1, 2]} {grid.GetLength(0)} {grid.GetLength(1)}");   // 7 3 4

int[][] jag = new int[3][];
for (int i = 0; i < 3; i++) { jag[i] = new int[4]; Array.Fill(jag[i], -1); }
jag[0][0] = 5;
Console.WriteLine(jag[0][0] + jag[1][1]);                  // 4

var list = new List<int> { 3, 1, 2 };
list.Add(10);
list.RemoveAt(0);                                          // remove index 0
list.Remove(10);                                           // remove first VALUE 10
list.Sort();
Console.WriteLine(string.Join(" ", list));                 // 1 2

var pairs = new List<(int Key, string Name)> { (3, "c"), (1, "a"), (2, "b") };
pairs.Sort((x, y) => x.Key.CompareTo(y.Key));
Console.WriteLine(pairs[0].Name);                          // a""", "Arrays, rectangular and jagged grids, List<T> and tuples"),
M("""## 6. Dictionaries and sets

| Structure | Class | Cost | Notes |
|---|---|---|---|
| Hash map | `Dictionary<K,V>` | O(1) average | indexer **throws** `KeyNotFoundException` if missing; use `TryGetValue` |
| Hash set | `HashSet<T>` | O(1) average | `Add` returns bool |
| Sorted map | `SortedDictionary<K,V>` | O(log n) | iterates in key order |
| Sorted set | `SortedSet<T>` | O(log n) | `Min`, `Max`, `GetViewBetween` |

Keys need consistent `Equals`/`GetHashCode`; tuples and records have value equality built in, so `(r, c)` works as a key directly.
"""),
C("""using System;
using System.Collections.Generic;

var freq = new Dictionary<char, int>();
foreach (char c in "mississippi")
{
    freq.TryGetValue(c, out int cur);
    freq[c] = cur + 1;
}
Console.WriteLine($"{freq['s']} {freq['i']} {freq.Count}");   // 4 4 4

int[] nums = { 2, 7, 11, 15 };
int target = 9;
var seen = new Dictionary<int, int>();                       // value -> index
for (int i = 0; i < nums.Length; i++)
{
    if (seen.TryGetValue(target - nums[i], out int j)) { Console.WriteLine($"{j} {i}"); break; }
    seen[nums[i]] = i;
}

var groups = new SortedDictionary<int, List<string>>();
foreach (var w in new[] { "to", "be", "or", "not" })
{
    if (!groups.ContainsKey(w.Length)) groups[w.Length] = new List<string>();
    groups[w.Length].Add(w);
}
foreach (var kv in groups) Console.Write($"{kv.Key}:{kv.Value.Count} ");   // 2:3 3:1
Console.WriteLine();

var visited = new HashSet<(int, int)>();                     // tuples hash by value
visited.Add((0, 1));
Console.WriteLine($"{visited.Contains((0, 1))} {visited.Count}");

var sorted = new SortedSet<int> { 10, 20, 30 };
Console.WriteLine($"{sorted.Min} {sorted.Max}");             // 10 30""", "Counting, two-sum, grouping, tuple keys and a sorted set"),
M("""## 7. Stack, queue, deque and priority queue

| Need | Use | Methods |
|---|---|---|
| Stack | `Stack<T>` | `Push`, `Pop`, `Peek`, `Count` |
| Queue | `Queue<T>` | `Enqueue`, `Dequeue`, `Peek` |
| Deque | `LinkedList<T>` (`AddFirst`, `AddLast`, `RemoveFirst`, `RemoveLast`) | O(1) ends |
| Priority queue | `PriorityQueue<TElement, TPriority>` (.NET 6+) | `Enqueue(element, priority)`, `Dequeue()`, `Peek()`; **min-priority first** |

For a **max-heap** use a comparer: `new PriorityQueue<int, int>(Comparer<int>.Create((a, b) => b.CompareTo(a)))`. `Dequeue()` on an empty queue throws; check `Count`.
"""),
C("""using System;
using System.Collections.Generic;

var stack = new Stack<int>();
stack.Push(1); stack.Push(2);
Console.WriteLine(stack.Pop());                              // 2

var q = new Queue<int>();
q.Enqueue(1); q.Enqueue(2);
Console.WriteLine(q.Dequeue());                              // 1

var pq = new PriorityQueue<string, int>();                   // element, priority (smallest first)
pq.Enqueue("later", 5);
pq.Enqueue("now", 1);
Console.WriteLine(pq.Dequeue());                             // now

var maxHeap = new PriorityQueue<int, int>(Comparer<int>.Create((a, b) => b.CompareTo(a)));
foreach (int x in new[] { 3, 9, 4 }) maxHeap.Enqueue(x, x);
Console.WriteLine(maxHeap.Peek());                           // 9

var dq = new LinkedList<int>();
dq.AddLast(2); dq.AddFirst(1); dq.AddLast(3);
dq.RemoveLast();
Console.WriteLine($"{dq.First!.Value} {dq.Last!.Value}");    // 1 2""", "Stack, queue, PriorityQueue (min and max) and LinkedList as a deque"),
M("""## 8. Sorting, comparers, LINQ and binary search

`Array.Sort` and `List.Sort` are introsort (**not stable**). LINQ's `OrderBy`/`ThenBy` are **stable** and return a new sequence. Custom order: `list.Sort((a, b) => ...)` (return negative, zero, positive) or `Comparer<T>.Create`. Avoid `a - b` in comparers (overflow): use `a.CompareTo(b)`. `BinarySearch` returns the index or the **bitwise complement** of the insertion point (`~idx`).
"""),
C("""using System;
using System.Collections.Generic;
using System.Linq;

var people = new List<(string Name, int Age)> { ("Asha", 30), ("Ben", 25), ("Chen", 30) };
var ordered = people.OrderByDescending(p => p.Age).ThenBy(p => p.Name).ToList();   // stable, new list
Console.WriteLine(string.Join(" ", ordered.Select(p => p.Name)));                   // Asha Chen Ben

int[] sorted = { 1, 3, 5, 7, 9 };
Console.WriteLine(Array.BinarySearch(sorted, 7));            // 3
Console.WriteLine(~Array.BinarySearch(sorted, 4));           // 2 (insertion point)

var intervals = new List<int[]> { new[] { 5, 8 }, new[] { 1, 3 }, new[] { 2, 6 } };
intervals.Sort((x, y) => x[0].CompareTo(y[0]));
Console.WriteLine(string.Join("", intervals.Select(i => i[0])));   // 125

Console.WriteLine(new[] { 4, 8, 15 }.Max() + " " + new[] { 4, 8, 15 }.Sum());       // 15 27""", "Sorting with comparers and LINQ, binary search and intervals"),
M("""## 9. Classes, records and nodes

Use a `class` for nodes (reference type). `record` gives value equality and concise syntax. Nullable reference types (`ListNode?`) document where `null` is allowed; the compiler warns about dereferencing a possible `null`.
"""),
C("""using System;

static ListNode? Reverse(ListNode? head)
{
    ListNode? prev = null;
    while (head != null)
    {
        var next = head.Next;
        head.Next = prev;
        prev = head;
        head = next;
    }
    return prev;
}

static int Height(TreeNode? t) => t == null ? 0 : 1 + Math.Max(Height(t.Left), Height(t.Right));

var h = Reverse(new ListNode(1) { Next = new ListNode(2) { Next = new ListNode(3) } });
for (var p = h; p != null; p = p.Next) Console.Write(p.Val + " ");   // 3 2 1
Console.WriteLine();
Console.WriteLine(Height(new TreeNode(1) { Left = new TreeNode(2) { Left = new TreeNode(3) } }));   // 3

class ListNode { public int Val; public ListNode? Next; public ListNode(int v) { Val = v; } }
class TreeNode { public int Val; public TreeNode? Left, Right; public TreeNode(int v) { Val = v; } }""", "Linked list reversal and tree height"),
M("""## 10. Complexity cheat sheet (.NET collections)

| Operation | List&lt;T&gt; | LinkedList&lt;T&gt; | Dictionary / HashSet | SortedDictionary / SortedSet | Stack / Queue | PriorityQueue |
|---|---|---|---|---|---|---|
| Index | O(1) | O(n) | - | - | - | - |
| Add at end | O(1)* | O(1) | O(1)* | O(log n) | O(1) | O(log n) |
| Insert/remove middle | O(n) | O(1) at node | - | - | - | - |
| Find | O(n) | O(n) | O(1)* | O(log n) | O(n) | O(n) |
| Min | O(n) | O(n) | O(n) | O(log n) | - | O(1) peek |

\\* amortised / average.

## 11. Pitfalls and idioms checklist

- `Dictionary` indexer throws on a missing key: use `TryGetValue` or `ContainsKey`.
- `int` overflow wraps silently; use `long` or `checked`.
- `Array.Sort` / `List.Sort` are not stable; LINQ `OrderBy` is.
- Never write comparers as `a - b`; use `CompareTo`.
- `PriorityQueue` pops the **smallest priority**; flip the comparer for a max-heap.
- Do not modify a collection inside `foreach` over it (`InvalidOperationException`).
- Strings are immutable; use `StringBuilder` in loops.
- `==` on strings compares content, but on arrays and lists compares references.
- A `struct` is copied on assignment; a `class` is shared.
- Check `Count` before `Pop`, `Peek`, `Dequeue`.
- LINQ is expressive but allocates; in hot loops prefer plain loops.

## 12. A reusable solution template
"""),
C("""using System;
using System.Collections.Generic;
using System.Linq;

static (int, int)? TwoSum(int[] a, int target)
{
    var seen = new Dictionary<int, int>();
    for (int j = 0; j < a.Length; j++)
    {
        if (seen.TryGetValue(target - a[j], out int i)) return (i + 1, j + 1);
        seen.TryAdd(a[j], j);
    }
    return null;
}

var first = Console.ReadLine()!.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(int.Parse).ToArray();
int n = first[0], t = first[1];
var a = Console.ReadLine()!.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(int.Parse).Take(n).ToArray();
var r = TwoSum(a, t);
Console.WriteLine(r is null ? "-1" : $"{r.Value.Item1} {r.Value.Item2}");""", "Template: parse, call a pure function, print"),
M("""## 13. What to practise next

1. Create a console project with `dotnet new console`, paste each program and run it; fix anything your SDK version complains about.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest) and [Course Order](/problems/course-order) in C#.
3. Learn `Dictionary`, `PriorityQueue`, `List.Sort` and LINQ `OrderBy`/`GroupBy`/`Select`: they cover most interview needs.
"""),
], quiz=[
 Q("A `PriorityQueue<TElement, TPriority>` in .NET dequeues:", ["The smallest priority first", "The largest priority first", "The oldest first", "A random item"], 0, "Use a reversed comparer for a max-heap."),
 Q("How do you read a Dictionary value safely?", ["TryGetValue", "The indexer without a check", "Cast to int", "Sort first"], 0, "The indexer throws KeyNotFoundException."),
 Q("Which sort is stable?", ["LINQ OrderBy", "Array.Sort", "List.Sort", "None"], 0, "Array.Sort and List.Sort are introsort."),
 Q("`int.MaxValue + 1` in an unchecked context gives:", ["A negative wrapped value", "An exception", "A long", "0"], 0, "Use long or checked to detect it."),
 Q("A good key for a grid coordinate in a HashSet is:", ["A value tuple (int, int)", "An int[]", "A List<int>", "A string only"], 0, "Tuples have value equality; arrays use reference equality."),
])
