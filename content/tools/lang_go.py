from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "go", code, cap)

GO = L("go-for-dsa", "Go for DSA", "free", 30, "", blocks=[
M("""## Why Go for interviews?

Go is a **small, compiled, statically typed, garbage-collected** language designed at Google for simple, fast, concurrent servers. It is common in backend and infrastructure roles (cloud tooling, microservices). Go is deliberately minimal: there is **one loop keyword (`for`)**, no classes, no exceptions, and a short standard library, so DSA code is explicit and a little longer than in Python, but easy to read.

| Strengths | Weaknesses |
|---|---|
| Fast compile and run; simple syntax | No built-in set, stack, queue or deque types |
| Slices and maps are built in | `container/heap` needs an interface boilerplate |
| Goroutines and channels for concurrency | Verbose error handling |
| Clear, predictable semantics | Generics are newer (Go 1.18+) and limited |

> **Verification note:** the Java, Python, C++, JavaScript and TypeScript programs in this chapter were compiled and run before publishing. This machine has no Go toolchain, so the Go programs were written carefully against the standard library but **not executed**; run each at <https://go.dev/play> or with `go run` and report any problem.

## 1. Program skeleton and input

Every program is `package main` with `func main()`. Read input with `bufio` (fast) and `fmt.Fscan`. Output with `fmt.Println` / `fmt.Printf` (use a buffered writer for large output).
"""),
C("""package main

import (
	"bufio"
	"fmt"
	"os"
)

func main() {
	in := bufio.NewReader(os.Stdin)
	out := bufio.NewWriter(os.Stdout)
	defer out.Flush()

	var n, k int
	fmt.Fscan(in, &n, &k)
	nums := make([]int64, n)
	var sum int64
	for i := range nums {
		fmt.Fscan(in, &nums[i])
		sum += nums[i]
	}
	fmt.Fprintf(out, "n=%d k=%d sum=%d\\n", n, k, sum)
}""", "Fast input with bufio.Reader and fmt.Fscan"),
M("""## 2. Types, overflow and integer division

| Type | Notes |
|---|---|
| `int` | 64 bits on 64-bit systems (the default integer) |
| `int32`, `int64`, `uint` ... | explicit sizes; **overflow wraps silently** (no panic) |
| `float64`, `bool` | as usual |
| `string` | immutable bytes (UTF-8) |
| `byte` (`uint8`), `rune` (`int32`) | a byte, and a Unicode code point |
| `math.MaxInt64`, `math.MinInt64` | constants in package `math` |

`/` on integers truncates toward zero; `%` keeps the dividend's sign (`-7 % 3 == -1`). Go **never converts types implicitly**: `int64(x) * y` needs both sides the same type. Unused variables and imports are **compile errors**.
"""),
C("""package main

import (
	"fmt"
	"math"
)

func main() {
	var a int32 = math.MaxInt32
	a++
	fmt.Println(a) // -2147483648 (wraps)

	fmt.Println(7/2, -7/2, -7%3) // 3 -3 -1
	m := ((-7 % 3) + 3) % 3
	fmt.Println(m) // 2

	const MOD = 1_000_000_007
	x := int64(123456789) * int64(987654321) % MOD
	fmt.Println(x)

	fmt.Println(math.MaxInt64, math.Sqrt(144), math.Abs(-3.5))
	var inf = math.MaxInt64 // a common "infinity" for minimum searches
	fmt.Println(inf > 1<<40)
}""", "Wrap-around, division, modulo and constants"),
M("""## 3. Control flow, functions and multiple returns

Only `for` exists: `for i := 0; i < n; i++ {}`, `for cond {}` (while), `for {}` (infinite), and `for i, v := range slice {}`. `if` can have an initialiser: `if v, ok := m[k]; ok {}`. `switch` needs no `break`. Functions can return **multiple values**, commonly `(result, error)` or `(value, ok)`. Slices, maps and pointers are passed as small headers/references; arrays are copied.

## 4. Strings, bytes and runes

A `string` is an immutable sequence of **bytes**. `s[i]` is a `byte`; `for i, r := range s` yields **runes** (Unicode characters). For ASCII problems use bytes; convert with `[]byte(s)` to mutate, and `string(b)` back. Build with `strings.Builder`.

| Task | Code |
|---|---|
| Length (bytes) | `len(s)` |
| Substring | `s[a:b]` (shares memory, O(1)) |
| Search / contains | `strings.Index(s, "ab")`, `strings.Contains` |
| Split / join | `strings.Fields(s)`, `strings.Split(s, ",")`, `strings.Join(parts, ",")` |
| Reverse | convert to `[]rune` or `[]byte` and swap |
| Letter index | `s[i] - 'a'` |
"""),
C("""package main

import (
	"fmt"
	"strings"
)

func isPalindrome(s string) bool {
	i, j := 0, len(s)-1
	for i < j {
		if s[i] != s[j] {
			return false
		}
		i++
		j--
	}
	return true
}

func reverse(s string) string {
	b := []byte(s)
	for i, j := 0, len(b)-1; i < j; i, j = i+1, j-1 {
		b[i], b[j] = b[j], b[i]
	}
	return string(b)
}

func main() {
	fmt.Println(isPalindrome("level")) // true
	fmt.Println(reverse("hello"))      // olleh

	var counts [26]int
	for i := 0; i < len("banana"); i++ {
		counts["banana"[i]-'a']++
	}
	fmt.Println(counts[0], counts['n'-'a']) // 3 2

	var sb strings.Builder
	for c := 'a'; c <= 'e'; c++ {
		sb.WriteRune(c)
	}
	fmt.Println(sb.String(), strings.Fields(" a  b c "))
}""", "Strings, bytes, a counting array and strings.Builder"),
M("""## 5. Arrays and slices

An **array** `[5]int` has a fixed length that is part of its type and is **copied** on assignment. A **slice** `[]int` is a view (pointer, length, capacity) over an array and is what you use for dynamic arrays: `append` is amortised O(1) (`a = append(a, x)`: always reassign), `len(a)`, `cap(a)`, `a[i:j]` re-slices without copying (changes are shared!), `copy(dst, src)` copies. 2-D slices need each row allocated: `g := make([][]int, r)` then `g[i] = make([]int, c)`.
"""),
C("""package main

import (
	"fmt"
	"sort"
)

func main() {
	a := []int{5, 2, 9, 1}
	sort.Ints(a)
	fmt.Println(a, len(a), a[1:3]) // [1 2 5 9] 4 [2 5]

	a = append(a, 10) // always reassign the result of append
	fmt.Println(a)

	b := a[:2] // a re-slice SHARES memory with a
	b[0] = 100
	fmt.Println(a[0]) // 100

	c := make([]int, len(a)) // an independent copy
	copy(c, a)
	c[0] = 7
	fmt.Println(a[0], c[0]) // 100 7

	grid := make([][]int, 3)
	for i := range grid {
		grid[i] = make([]int, 4)
		for j := range grid[i] {
			grid[i][j] = -1
		}
	}
	grid[1][2] = 7
	fmt.Println(grid[1])

	// remove index 1 while keeping order
	a = append(a[:1], a[2:]...)
	fmt.Println(a)
}""", "Slices: append, sharing, copy, 2-D grids and deletion"),
M("""## 6. Maps and sets

`map[K]V` is a hash map (average O(1)). Reading a missing key returns the **zero value**, so use the comma-ok form to tell the difference: `v, ok := m[k]`. Maps are **unordered** (iteration order is random). There is no set type: use `map[T]struct{}` or `map[T]bool`. Keys must be comparable: ints, strings, arrays and structs of comparable fields work; slices do not (use `[2]int` for coordinates).
"""),
C("""package main

import (
	"fmt"
	"sort"
)

func main() {
	freq := map[rune]int{}
	for _, c := range "mississippi" {
		freq[c]++ // missing key starts at zero
	}
	fmt.Println(freq['s'], freq['i'], len(freq)) // 4 4 4

	nums := []int{2, 7, 11, 15}
	target := 9
	seen := map[int]int{} // value -> index
	for i, x := range nums {
		if j, ok := seen[target-x]; ok {
			fmt.Println(j, i)
			break
		}
		seen[x] = i
	}

	groups := map[int][]string{}
	for _, w := range []string{"to", "be", "or", "not"} {
		groups[len(w)] = append(groups[len(w)], w)
	}
	keys := make([]int, 0, len(groups))
	for k := range groups {
		keys = append(keys, k)
	}
	sort.Ints(keys) // maps iterate in random order: sort the keys
	for _, k := range keys {
		fmt.Print(k, ":", len(groups[k]), " ") // 2:3 3:1
	}
	fmt.Println()

	visited := map[[2]int]struct{}{} // a set of coordinates
	visited[[2]int{0, 1}] = struct{}{}
	_, has := visited[[2]int{0, 1}]
	fmt.Println(has, len(visited)) // true 1
}""", "Counting, two-sum, grouping, sorted iteration and a set of arrays"),
M("""## 7. Stack, queue, deque and heap

Go has no stack or queue types: use **slices**.

| Need | How |
|---|---|
| Stack | `st = append(st, x)`; top is `st[len(st)-1]`; pop is `st = st[:len(st)-1]` |
| Queue | slice with a head index (`q[head]`, `head++`), or `container/list` |
| Deque | `container/list` (doubly linked) |
| Heap | `container/heap`: implement `Len`, `Less`, `Swap`, `Push`, `Pop` on your slice type |

`heap.Push(h, x)` / `heap.Pop(h)` call your methods and keep the invariant. `Less` decides the order: `a[i] < a[j]` gives a min-heap; flip it for a max-heap.
"""),
C("""package main

import (
	"container/heap"
	"container/list"
	"fmt"
)

type IntHeap []int

func (h IntHeap) Len() int           { return len(h) }
func (h IntHeap) Less(i, j int) bool { return h[i] < h[j] } // min-heap
func (h IntHeap) Swap(i, j int)      { h[i], h[j] = h[j], h[i] }
func (h *IntHeap) Push(x any)        { *h = append(*h, x.(int)) }
func (h *IntHeap) Pop() any {
	old := *h
	n := len(old)
	x := old[n-1]
	*h = old[:n-1]
	return x
}

func main() {
	stack := []int{1, 2}
	stack = append(stack, 3)
	top := stack[len(stack)-1]
	stack = stack[:len(stack)-1]
	fmt.Println(top, len(stack)) // 3 2

	queue := []int{1, 2, 3}
	head := 0
	fmt.Println(queue[head], len(queue)-head-1) // 1 2
	head++

	h := &IntHeap{5, 1, 4}
	heap.Init(h)
	heap.Push(h, 0)
	smallest := heap.Pop(h) // 0
	fmt.Println(smallest, (*h)[0]) // 0 1

	dq := list.New()
	dq.PushBack(2)
	dq.PushFront(1)
	dq.PushBack(3)
	dq.Remove(dq.Back())
	fmt.Println(dq.Front().Value, dq.Back().Value, dq.Len()) // 1 2 2
}""", "Stack and queue on slices, container/heap and container/list"),
M("""## 8. Sorting and binary search

Package `sort`: `sort.Ints`, `sort.Strings`, `sort.Slice(s, less)` (not stable), `sort.SliceStable`, `sort.Search(n, f)` (first index where `f` is true) and `sort.SearchInts(a, x)` (the first index with `a[i] >= x`). From Go 1.21 the `slices` package adds `slices.Sort`, `slices.BinarySearch` and the built-ins `min` and `max`.
"""),
C("""package main

import (
	"fmt"
	"sort"
)

type Person struct {
	Name string
	Age  int
}

func main() {
	people := []Person{{"Asha", 30}, {"Ben", 25}, {"Chen", 30}}
	sort.Slice(people, func(i, j int) bool {
		if people[i].Age != people[j].Age {
			return people[i].Age > people[j].Age // older first
		}
		return people[i].Name < people[j].Name
	})
	for _, p := range people {
		fmt.Print(p.Name, " ") // Asha Chen Ben
	}
	fmt.Println()

	v := []int{1, 3, 5, 5, 7, 9}
	fmt.Println(sort.SearchInts(v, 5))                                // 2 (first index with v[i] >= 5)
	fmt.Println(sort.Search(len(v), func(i int) bool { return v[i] > 5 })) // 4 (first index with v[i] > 5)

	intervals := [][]int{{5, 8}, {1, 3}, {2, 6}}
	sort.Slice(intervals, func(i, j int) bool { return intervals[i][0] < intervals[j][0] })
	fmt.Println(intervals) // [[1 3] [2 6] [5 8]]
}""", "sort.Slice with multiple keys, SearchInts and sort.Search"),
M("""## 9. Structs, pointers and nodes

There are no classes: use `struct` and methods. A pointer `*T` lets you share and mutate; `nil` is the zero value of pointers, slices and maps (a nil map **panics on write**, so `make` it). Use `&T{...}` to allocate. Access fields through a pointer with `p.field` (Go dereferences automatically).
"""),
C("""package main

import "fmt"

type ListNode struct {
	Val  int
	Next *ListNode
}

type TreeNode struct {
	Val         int
	Left, Right *TreeNode
}

func reverse(head *ListNode) *ListNode {
	var prev *ListNode
	for head != nil {
		next := head.Next
		head.Next = prev
		prev = head
		head = next
	}
	return prev
}

func height(t *TreeNode) int {
	if t == nil {
		return 0
	}
	return 1 + max(height(t.Left), height(t.Right)) // built-in max (Go 1.21+)
}

func main() {
	h := reverse(&ListNode{1, &ListNode{2, &ListNode{3, nil}}})
	for p := h; p != nil; p = p.Next {
		fmt.Print(p.Val, " ") // 3 2 1
	}
	fmt.Println()

	root := &TreeNode{Val: 1, Left: &TreeNode{Val: 2, Left: &TreeNode{Val: 3}}}
	fmt.Println(height(root)) // 3
}""", "Linked list reversal and tree height with pointers"),
M("""## 10. Complexity cheat sheet (Go built-ins and standard library)

| Operation | Slice | map | container/list | container/heap |
|---|---|---|---|---|
| Index | O(1) | - | O(n) | - |
| Append at end | O(1)* | O(1)* insert | O(1) | O(log n) push |
| Insert/delete middle | O(n) | O(1)* | O(1) at element | - |
| Find by value | O(n) | O(1)* | O(n) | - |
| Min | O(n) | O(n) | O(n) | O(1) at index 0 |

\\* amortised / average.

## 11. Pitfalls and idioms checklist

- Always assign the result of `append`: `a = append(a, x)`.
- Re-slicing **shares memory**; use `copy` for an independent slice.
- Reading a missing map key returns the zero value; use `v, ok := m[k]`.
- Writing to a **nil map** panics; create maps with `make` or a literal.
- Map iteration order is random; sort the keys when you need order.
- Integer overflow wraps silently; use `int64` and watch for `int32`.
- `len(s)` of a string counts **bytes**, not characters; use `[]rune(s)` for Unicode.
- Unused variables and imports do not compile.
- There are no implicit conversions: convert explicitly.
- `container/heap` methods `Push` and `Pop` are for the heap package: call `heap.Push` / `heap.Pop`, not the methods directly.
- Deep recursion is fine (stacks grow), but very deep recursion still uses memory.

## 12. A reusable solution template
"""),
C("""package main

import (
	"bufio"
	"fmt"
	"os"
)

func twoSum(a []int, target int) (int, int, bool) {
	seen := map[int]int{}
	for j, x := range a {
		if i, ok := seen[target-x]; ok {
			return i + 1, j + 1, true
		}
		if _, has := seen[x]; !has {
			seen[x] = j
		}
	}
	return 0, 0, false
}

func main() {
	in := bufio.NewReader(os.Stdin)
	var n, t int
	fmt.Fscan(in, &n, &t)
	a := make([]int, n)
	for i := range a {
		fmt.Fscan(in, &a[i])
	}
	if i, j, ok := twoSum(a, t); ok {
		fmt.Println(i, j)
	} else {
		fmt.Println(-1)
	}
}""", "Template: parse, call a pure function, print"),
M("""## 13. What to practise next

1. Run every program on the Go Playground and change the inputs.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest) and [Cheapest Route](/problems/cheapest-route) in Go; the last one needs `container/heap`.
3. Practise the heap boilerplate until you can write it from memory; it is the part of Go interviews people forget.
"""),
], quiz=[
 Q("Why must you write `a = append(a, x)`?", ["append may return a new slice", "append mutates a global", "It is optional", "It sorts a"], 0, "The slice header can change when capacity grows."),
 Q("Reading a missing key from a Go map returns:", ["The zero value of the value type", "An error", "nil always", "A panic"], 0, "Use v, ok := m[k] to tell the difference."),
 Q("Go's closest thing to a set is:", ["map[T]struct{}", "[]T", "list.List", "A built-in set type"], 0, "There is no set type."),
 Q("Writing to a nil map:", ["Panics", "Creates it", "Is ignored", "Returns false"], 0, "Create maps with make or a literal."),
 Q("`for i, v := range s` over a string yields:", ["Byte offsets and runes", "Bytes only", "Words", "Lines"], 0, "A rune is a Unicode code point."),
])
