"""Deep version of the Go lesson (reuses programs from lang_go.py, adds new ones). Not executed: no Go toolchain here."""
from courses_a import Q, L
from lang_go import GO as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "go", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 types,2 strings,3 slices,4 maps,5 stack/heap,6 sorting,7 nodes,8 template

REFS = C("""package main

import "fmt"

type Counter struct{ n int }

func (c Counter) IncByValue()    { c.n++ } // receives a COPY of the struct
func (c *Counter) IncByPointer() { c.n++ } // receives a pointer: changes the original

func mutate(arr [3]int, s []int, m map[string]int, p *int) {
	arr[0] = 99     // an array is a value: this changes the local copy
	s[0] = 99       // a slice shares its backing array: the caller sees it
	s = append(s, 7) // may allocate a new array: the caller's slice header is unchanged
	m["k"] = 1      // maps are references: the caller sees it
	*p = 42         // through a pointer: the caller sees it
}

func main() {
	arr := [3]int{1, 2, 3}
	s := []int{1, 2, 3}
	m := map[string]int{}
	x := 5
	mutate(arr, s, m, &x)
	fmt.Println(arr[0], s[0], len(s), m["k"], x) // 1 99 3 1 42

	var c Counter
	c.IncByValue()
	c.IncByPointer()
	fmt.Println(c.n) // 1

	a := []int{1, 2, 3, 4}
	b := a[:2]
	b = append(b, 100) // fits in the capacity of a, so it overwrites a[2]!
	fmt.Println(a, b)  // [1 2 100 4] [1 2 100]

	c2 := append([]int(nil), a...) // a safe independent copy
	c2[0] = -1
	fmt.Println(a[0], c2[0]) // 1 -1

	var nilSlice []int
	fmt.Println(nilSlice == nil, len(nilSlice), cap(nilSlice)) // true 0 0
	nilSlice = append(nilSlice, 1)                             // appending to a nil slice works
	fmt.Println(nilSlice)

	var nilMap map[string]int
	fmt.Println(nilMap["x"], len(nilMap)) // 0 0 (reading a nil map is fine; writing panics)

	x1, x2 := [2]int{1, 2}, [2]int{1, 2}
	fmt.Println(x1 == x2) // true (arrays are comparable by value)
}""", "Value versus pointer receivers, arrays versus slices, aliasing through append, nil slices and maps")

HASH = C("""package main

import "fmt"

type Point struct{ R, C int }

func main() {
	seen := map[Point]bool{} // a struct of comparable fields is a valid key
	seen[Point{1, 2}] = true
	fmt.Println(seen[Point{1, 2}], seen[Point{2, 1}]) // true false

	grid := map[[2]int]int{} // arrays are comparable too
	grid[[2]int{0, 1}] = 7
	fmt.Println(grid[[2]int{0, 1}], len(grid)) // 7 1

	set := map[string]struct{}{} // a set: struct{} takes zero bytes
	for _, w := range []string{"a", "b", "a"} {
		set[w] = struct{}{}
	}
	fmt.Println(len(set)) // 2

	// map[[]int]bool would NOT compile: slices are not comparable

	counts := map[string]int{}
	for _, w := range []string{"to", "be", "or", "to"} {
		counts[w]++
	}
	v, ok := counts["missing"]
	fmt.Println(counts["to"], v, ok) // 2 0 false
	delete(counts, "be")
	fmt.Println(len(counts))

	enc := map[int]int{}
	cols := 100
	enc[5*cols+7] = 1 // encoding (r, c) as a single int is fast and simple
	fmt.Println(enc[5*cols+7])
}""", "Struct and array keys, a set from map[T]struct{}, comma-ok lookups and delete")

GRAPH = C("""package main

import "fmt"

func main() {
	n := 6
	edges := [][2]int{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {2, 5}}
	adj := make([][]int, n)
	for _, e := range edges {
		adj[e[0]] = append(adj[e[0]], e[1])
		adj[e[1]] = append(adj[e[1]], e[0]) // undirected
	}

	dist := make([]int, n)
	for i := range dist {
		dist[i] = -1
	}
	queue := []int{0}
	dist[0] = 0
	for head := 0; head < len(queue); head++ {
		u := queue[head]
		for _, v := range adj[u] {
			if dist[v] == -1 {
				dist[v] = dist[u] + 1
				queue = append(queue, v)
			}
		}
	}
	fmt.Println(dist) // [0 1 1 2 3 2]
}""", "A graph as a slice of slices and breadth-first search with a head index")

ERR = C("""package main

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
)

var ErrNotPositive = errors.New("not positive")

func parsePositive(s string) (int, error) {
	v, err := strconv.Atoi(strings.TrimSpace(s))
	if err != nil {
		return 0, fmt.Errorf("parsePositive(%q): %w", s, err) // wrap an error with context
	}
	if v <= 0 {
		return 0, ErrNotPositive
	}
	return v, nil
}

func safeDiv(a, b int) (res int, err error) {
	defer func() { // deferred functions run when the function exits, even on panic
		if r := recover(); r != nil {
			err = fmt.Errorf("recovered: %v", r)
		}
	}()
	return a / b, nil // integer division by zero panics at run time
}

func main() {
	if v, err := parsePositive(" 42 "); err == nil {
		fmt.Println(v) // 42
	}
	if _, err := parsePositive("abc"); err != nil {
		fmt.Println("error:", err != nil) // true
	}
	_, err := parsePositive("-5")
	fmt.Println(errors.Is(err, ErrNotPositive)) // true

	_, err = safeDiv(1, 0)
	fmt.Println(err) // recovered: runtime error: integer divide by zero

	a := []int{1, 2, 3}
	idx := 5
	func() {
		defer func() { fmt.Println("caught:", recover() != nil) }() // true
		fmt.Println(a[idx])                                          // panics: index out of range
	}()

	var empty []int
	fmt.Println(len(empty) == 0, empty == nil) // true true
	best := -1 << 62                           // a very small starting value for a maximum search
	for _, x := range []int{4, 8, 15} {
		best = max(best, x)
	}
	fmt.Println(best) // 15
}""", "Errors as values, wrapping, panic and recover, bounds panics and empty-input handling")

GO2 = L("go-for-dsa", "Go for DSA", "free", 80, "", blocks=[
M("""# Go for Data Structures and Algorithms: a complete guide

This lesson explains Go **as you need it for algorithmic problem solving**: what Go is and how it differs from the other languages here, its types and zero values, integer behaviour, the single `for` loop, functions and pointers, how strings really work (bytes and runes), the inner workings of **slices and maps** (the two types you will use constantly), the standard `sort` and `container/heap` packages, and the **time and space complexity of every operation**.

> **Verification note.** The Java, Python, C++, JavaScript and TypeScript lessons were compiled/type-checked and run before publishing. This machine has no Go toolchain, so every Go program here was written carefully against the standard library (Go 1.21+) but **has not been executed**. Run each at <https://go.dev/play> or with `go run`, and report anything that does not compile.

## 1. What Go is, and why it behaves the way it does

**Go** (Golang) was designed at Google for simple, fast, reliable server software. It is **compiled to native code** (very fast builds), **statically typed**, **garbage collected**, and deliberately **small**: one loop keyword, no classes or inheritance, no exceptions, no operator overloading, no implicit conversions. Concurrency is built in (goroutines and channels), though algorithm problems rarely need it. You compile and run with `go run main.go` or build a binary with `go build`.

| Property | What it means | Consequence |
|---|---|---|
| **Statically typed, no implicit conversions** | Every conversion is written out | `int64(x) * y` needs both operands of the same type |
| **Zero values** | Every variable starts at a defined zero value | No uninitialised memory; a `map` read of a missing key gives the zero value |
| **Garbage collected** | Unreachable memory is reclaimed | No `free`; pointers exist but there is no pointer arithmetic |
| **Composition over inheritance** | `struct`s plus methods plus implicit interfaces | A type satisfies an interface just by having its methods |
| **Errors are values** | Functions return `(result, error)` | Explicit `if err != nil` handling; `panic` is for bugs |
| **Minimal standard data structures** | slice, map, plus `container/heap`, `container/list` | You build stacks, queues and sets from slices and maps |

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| Fast to compile and run; simple, readable | No built-in set, stack, queue, deque or sorted map |
| Slices and maps are built into the language | `container/heap` needs interface boilerplate |
| Clear semantics, no hidden magic | Verbose error handling; generics are newer and limited |
| Great for backend and infrastructure roles | Integer overflow wraps silently |

## 2. Program structure and input

Every program is `package main` with a `func main()`; imports are listed in `import ( ... )` and **an unused import or an unused local variable is a compile error**. Names starting with an uppercase letter are exported; lowercase are package-private. Input and output: `fmt.Fscan(reader, &a, &b)` reads whitespace-separated values into variables; wrap `os.Stdin` in a `bufio.Reader` (and `os.Stdout` in a `bufio.Writer`, flushed with `defer out.Flush()`) for speed. Plain `fmt.Println` per line is slow for large output.
"""),
codes[0],
M("""**How it works.** `bufio.NewReader` reads in large chunks; `fmt.Fscan(in, &n, &k)` parses values into the addresses given (`&` takes an address). `make([]int64, n)` creates a slice of `n` zeros. `for i := range nums` iterates the indexes. `defer out.Flush()` schedules the flush to run when `main` returns.

## 3. Data types in depth

### 3.1 Basic types and zero values

| Type | Size | Range | Zero value |
|---|---|---|---|
| `int`, `uint` | 64 bits on 64-bit systems (32 on 32-bit) | ±9.2 × 10¹⁸, 0 .. 1.8 × 10¹⁹ | `0` |
| `int8`, `int16`, `int32`, `int64` | 8/16/32/64 bits | ±127, ±32,767, ±2.1 × 10⁹, ±9.2 × 10¹⁸ | `0` |
| `uint8` (`byte`), `uint16`, `uint32`, `uint64` | 8..64 bits | 0..255, ... | `0` |
| `float32`, `float64` | 32, 64 bits | ~7, ~15-16 digits | `0` |
| `bool` | 1 byte | `true`/`false` | `false` |
| `string` | 16 bytes header | immutable bytes | `""` |
| `rune` (`int32`) | 32 bits | one Unicode code point | `0` |
| pointers, slices, maps, channels, functions, interfaces | | | `nil` |

Because `int` is 64 bits on modern machines, most interview arithmetic fits without special care, but you must still watch `int32` fields and products of two large values. **Untyped constants** (`const MOD = 1_000_000_007`, `1 << 40`) adapt to the type where they are used and are computed with arbitrary precision at compile time; a constant that does not fit its target type is a **compile** error. Declare with `var x int`, `var x = 5`, or the short form `x := 5` (inside functions only).

### 3.2 Overflow and division
**Integer overflow wraps silently** (two's complement): `var a int32 = math.MaxInt32; a++` gives `-2147483648`. There is no exception and no undefined behaviour. Multiply in a wide enough type: `int64(a) * int64(b)`. Integer division **truncates toward zero** (`-7 / 2 == -3`), `%` keeps the dividend's sign (`-7 % 3 == -1`); a non-negative modulo is `((a % m) + m) % m`. **Integer division by zero causes a run-time panic**; floating-point division by zero gives `+Inf` or `NaN`. Floating point is IEEE 754: `0.1 + 0.2 != 0.3`; compare with a tolerance. `math.MaxInt64`, `math.MinInt64`, `math.MaxInt32` are constants; the built-ins `min` and `max` (Go 1.21+) work on ordered types.

### 3.3 No implicit conversion
`var a int = 5; var b int64 = a` is a compile error; write `int64(a)`. Converting a float to an int truncates (`int(3.9) == 3`). `string(65)` gives `"A"` (a code point), not `"65"`; use `strconv.Itoa(65)` for the number text, `strconv.Atoi("65")` to parse (returns `(int, error)`), `strconv.ParseInt`, `strconv.FormatInt(n, 2)` for binary text.

### 3.4 Operators and bits
Arithmetic `+ - * / %`, comparison `== != < <= > >=`, logical `&& || !` (short-circuit), bitwise `& | ^ &^ << >>` (`^x` is bitwise NOT; `&^` is "and not"), assignment `= := += -= ...`, `++` and `--` as **statements** (not expressions). `x & (x - 1)` clears the lowest set bit; `1 << k` is 2ᵏ (use an `int64`/`int` of enough width). There is no ternary operator: write an `if`. Overflow, division and constants in action:
"""),
codes[1],
M("""## 4. Control flow

Go has **only `for`**:

| Form | Meaning |
|---|---|
| `for i := 0; i < n; i++ { }` | the classic counted loop |
| `for cond { }` | a "while" loop |
| `for { }` | infinite loop, leave with `break` or `return` |
| `for i, v := range slice { }` | index and value (use `_` to ignore one) |
| `for k, v := range someMap { }` | key and value, **random order** |
| `for i, r := range str { }` | byte index and **rune** |
| `for i := range 10 { }` | integer range (Go 1.22+) |

`if` takes an optional initialiser (`if v, ok := m[k]; ok { ... }`). `switch` needs no `break` (cases do not fall through; use `fallthrough` explicitly) and can switch on conditions (`switch { case x < 0: ... }`). `break` and `continue` accept labels for nested loops. `defer f()` schedules `f` to run when the surrounding function returns (used for cleanup). **Counting the cost:** one loop over n items is O(n), nested loops O(n²), halving the range O(log n).

## 5. Functions, pointers and references

Functions are declared `func name(a int, b string) (int, error)`; they can return **multiple values**, have **named results**, accept variadic parameters (`xs ...int`), and be passed around as values (closures capture variables). Methods are functions with a receiver: `func (c *Counter) Inc()`.

### 5.1 Everything is passed by value
Go copies every argument. What differs is *what is being copied*:

| Type | What the callee receives | Can it change the caller's data? |
|---|---|---|
| `int`, `float64`, `bool`, `string` | a copy of the value | no |
| **array** `[3]int`, **struct** | a copy of the whole value (O(size)) | no |
| **pointer** `*T` | a copy of the address | **yes**, through the pointer |
| **slice** `[]T` | a copy of the 3-word header (pointer, length, capacity) | **yes** for elements (shared backing array); `append` may or may not be visible |
| **map**, **channel** | a copy of a reference | **yes** |

Methods with a **value receiver** work on a copy; with a **pointer receiver** they can change the original (use pointer receivers when the struct is large or must be mutated). The program shows each case, including the notorious `append` aliasing; predict the output first.
"""),
REFS,
M("""### 5.2 Recursion
Goroutine stacks start small and **grow dynamically** up to a large limit (1 GB on 64-bit), so deep recursion is far less dangerous in Go than in Java or Python, but a runaway recursion still ends in a fatal "stack overflow". Memoise with a `map` or a slice.

## 6. Strings, bytes and runes in depth

A Go `string` is an **immutable sequence of bytes**, conventionally UTF-8 text; it is stored as a (pointer, length) pair, so copying a string is O(1) and **`s[a:b]` is O(1)** (it shares memory, no copy). `len(s)` is the number of **bytes**, not characters. `s[i]` is a `byte` (`uint8`). A **rune** is one Unicode code point; `for i, r := range s` decodes UTF-8 and yields `r` as a rune while `i` is the byte offset; `[]rune(s)` converts to a slice of code points (O(n)). For ASCII problems bytes are enough; for Unicode use runes.

Strings are **immutable**, so to modify, convert: `b := []byte(s)` (copy, O(n)), change `b`, then `string(b)` (copy, O(n)). Concatenating with `+` in a loop is O(n²); use `strings.Builder` (amortised O(1) per write) or collect pieces and `strings.Join`.

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length in bytes | `len(s)` | O(1) | |
| Byte at index | `s[i]` | O(1) | panics if out of range |
| Substring | `s[a:b]` | O(1) | shares memory; end exclusive |
| Concatenate | `s + t` | O(n + m) | new string |
| Compare | `s == t`, `s < t` | O(min(n, m)) | byte-wise |
| Search | `strings.Index(s, t)`, `strings.Contains` | O(n·m) worst | |
| Split / join | `strings.Fields`, `strings.Split`, `strings.Join` | O(n) | `Fields` splits on any whitespace |
| To bytes / runes | `[]byte(s)`, `[]rune(s)` | O(n) | copies |
| Build | `strings.Builder.WriteString/WriteByte/WriteRune` | amortised O(1) | |
| Reverse | swap in a `[]byte` or `[]rune` | O(n) | |
| Space | | O(n) | 1 byte per ASCII char |

Letter frequencies use `var cnt [26]int` and `cnt[s[i]-'a']++`: O(1) update, 26 words of memory.
"""),
codes[2],
M("""## 7. Arrays and slices in depth

### 7.1 Arrays
`[5]int` is a **fixed-length value**: the length is part of the type, assignment and passing **copy all elements**, and `==` compares element by element. Arrays are rarely used directly except for small fixed tables (`[26]int`, `[2]int` coordinates, `[4][2]int` direction pairs).

### 7.2 Slices: how they really work
A **slice** is a small struct of three words: a **pointer** to an element of an underlying array, a **length** and a **capacity**. `make([]int, n)` creates a zeroed slice of length and capacity `n`; `make([]int, 0, n)` has length 0 and capacity `n`. Operations:

* `a[i]` is O(1) (bounds-checked: out of range **panics**).
* `a[i:j]` creates a new slice header over the **same** array in O(1): **elements are shared**, so writes through one slice are visible through the other.
* `append(a, x)` writes into the next slot if `len < cap` (O(1), and it may overwrite elements visible to another slice, as the program shows), otherwise it **allocates a new array** (growth: about double while small, then roughly 1.25× for large slices), copies all elements (O(n)) and returns a header pointing at the new array. Growth is geometric, so `append` is **amortised O(1)**. **Always assign the result:** `a = append(a, x)`.
* `copy(dst, src)` copies `min(len(dst), len(src))` elements in O(n).
* A **nil slice** (`var a []int`) has length 0, is safe to `append` to and to `range` over, and equals `nil`.

| Operation | Time | Space | Notes |
|---|---|---|---|
| `a[i]` read/write | O(1) | O(1) | bounds-checked |
| `append(a, x)` | amortised O(1), worst O(n) | O(1) amortised | reassign the result |
| `a[i:j]` | O(1) | O(1) | shares the backing array |
| `copy(dst, src)` | O(n) | O(1) | |
| Pop from the end `a = a[:len(a)-1]` | O(1) | O(1) | the element stays in memory until overwritten |
| Pop from the front `a = a[1:]` | O(1) | O(1) | the slice shrinks from the left; the memory is not freed |
| Insert in the middle | O(n) | O(1) | `append(a[:i], append([]int{x}, a[i:]...)...)` or `slices.Insert` |
| Delete in the middle | O(n) | O(1) | `append(a[:i], a[i+1:]...)` or `slices.Delete` |
| Search | O(n) | O(1) | `slices.Index`, `slices.Contains` (Go 1.21+) |
| Sort | O(n log n) | O(log n) | `sort.Ints`, `slices.Sort` |
| Full copy | O(n) | O(n) | `append([]int(nil), a...)` or `slices.Clone` |

**2-D slices** are slices of slices: allocate every row, `grid := make([][]int, r)` then `grid[i] = make([]int, c)`: O(r·c) space. **Pitfall:** the same slice reslice-and-append aliasing can corrupt data silently; when in doubt, copy.
"""),
codes[3],
M("""## 8. Maps in depth

A `map[K]V` is a **hash table**. It maps a key's hash to buckets and grows (rehashes) as it fills, so insert, lookup and delete are **O(1) on average** and amortised; the exact internal layout has changed between Go versions but the complexity has not. Properties:

* **Iteration order is random** (deliberately): sort the keys if you need an order.
* Reading a **missing key returns the zero value**; use the **comma-ok** form `v, ok := m[k]` to distinguish "absent" from "zero". `m[k]++` works as a counter because a missing key starts at zero.
* **Writing to a nil map panics.** Create maps with `make(map[K]V)` or a literal `map[K]V{}`. Reading a nil map is fine.
* Keys must be **comparable**: numbers, strings, bools, pointers, **arrays and structs of comparable fields**. Slices, maps and functions are **not** valid keys (compile error). So a coordinate key is `[2]int` or a small struct, or an encoded `int`.
* There is **no set type**: use `map[T]struct{}` (the empty struct takes zero bytes) or `map[T]bool`.
* Maps are **references**: passing a map to a function lets the function modify the caller's map.
* Deleting while ranging over a map is allowed for the current key.
* Maps are not safe for concurrent writes.

| Operation | Time | Space | Notes |
|---|---|---|---|
| `m[k] = v` | O(1) avg | O(1) amortised | |
| `v := m[k]`, `v, ok := m[k]` | O(1) avg | O(1) | |
| `delete(m, k)` | O(1) avg | O(1) | no-op if absent |
| `len(m)` | O(1) | O(1) | |
| iterate | O(n + capacity) | O(1) | random order |
| min / max key | O(n) | O(1) | no sorted map built in |
| whole structure | | O(n), a few dozen bytes per entry | |

For ordered operations (nearest key, sorted iteration) Go has no tree: keep a sorted slice with `sort.Search` (O(log n) find, O(n) insert) or implement a balanced tree.
"""),
codes[4],
HASH,
M("""## 9. Stacks, queues, deques and heaps in depth

Go has no stack or queue types; slices do the job.

| Structure | Implementation | Operations | Time |
|---|---|---|---|
| **Stack** | slice: `st = append(st, x)`; top `st[len(st)-1]`; pop `st = st[:len(st)-1]` | push, pop, peek | O(1) each |
| **Queue** | slice plus a head index: `q[head]`, `head++`; or `q = q[1:]` | enqueue, dequeue | O(1) each (memory is released only when the slice is reallocated) |
| **Deque** | `container/list` (doubly linked) or a circular buffer | both ends | O(1) each |
| **Priority queue** | `container/heap` over your slice type | `heap.Push`, `heap.Pop`, `h[0]` | O(log n), O(log n), O(1) |

**How `container/heap` works.** You define a type (often `type IntHeap []int`) implementing `Len`, `Less`, `Swap`, plus `Push(x any)` and `Pop() any` (these two only **append and remove the last element**). You then call **`heap.Push(h, x)`** and **`heap.Pop(h)`**, which call your methods and restore the heap order by sifting up or down (≤ log n swaps); **`heap.Init(h)`** builds a heap from existing data in **O(n)**; **`heap.Fix(h, i)`** repairs one element in O(log n), which gives you a *decrease-key*. The root is `h[0]`. `Less(i, j)` decides the order: `h[i] < h[j]` gives a **min**-heap; flip it for a max-heap. A heap of structs ordered by a field is the standard Dijkstra queue.

| Operation | stack / queue (slice) | `container/list` | `container/heap` |
|---|---|---|---|
| Add | O(1) amortised | O(1) | O(log n) |
| Remove next | O(1) | O(1) | O(log n) |
| Peek | O(1) | O(1) | O(1) |
| Search | O(n) | O(n) | O(n) |
| Build from n items | O(n) | O(n) | O(n) (`heap.Init`) |
| Space | O(n) | O(n) + node objects | O(n) |
"""),
codes[5],
M("""## 10. Sorting and searching in depth

The `sort` package sorts **in place**: `sort.Ints`, `sort.Strings`, `sort.Float64s`, and `sort.Slice(s, func(i, j int) bool { ... })` for any slice (the function says whether element `i` must come **before** `j`). `sort.Slice` is **not stable**; `sort.SliceStable` is, and both are O(n log n). Go 1.21+ adds `slices.Sort`, `slices.SortFunc`, `slices.BinarySearch` and the `cmp` package (`cmp.Compare`). The comparison must be a consistent *strict* ordering (`<`, not `<=`). `sort.Search(n, f)` does a binary search for the **smallest index where `f(i)` is true** (requires monotone `f`), O(log n); `sort.SearchInts(a, x)` is the first index with `a[i] >= x`.
"""),
codes[6],
M("""## 11. Structs, methods, pointers and interfaces

A `struct` groups fields; methods attach to any named type. **Pointers** (`*T`, address-of `&x`, dereference `*p`) let you share and mutate; a **nil pointer** dereference panics, so test `p != nil`. There is no pointer arithmetic. `new(T)` and `&T{...}` allocate; **escape analysis** decides whether a value lives on the stack or the heap (you do not choose). **Interfaces** are satisfied **implicitly**: a type that has the methods of `sort.Interface` or `heap.Interface` *is* one. The empty interface `any` holds any value (needs a type assertion to use). **Generics** (Go 1.18+) let you write `func Max[T cmp.Ordered](a, b T) T`.

**Garbage collection** reclaims unreachable memory concurrently; avoid keeping huge slices alive through small sub-slices (a `s[:10]` of a million-element slice still pins the whole array: copy what you need).

### 11.1 Linked lists and trees
Nodes are structs with pointer fields (`Next *ListNode`, `Left, Right *TreeNode`); a `nil` pointer ends a chain. Reaching the i-th node costs O(i); inserting after a held node is O(1); recursion depth equals tree height.
"""),
codes[7],
M("""### 11.2 Graphs
Store a graph as an **adjacency list** `[][]int` (O(V + E) space) or `[][]Edge` for weights; an adjacency matrix `[][]int` is O(V²). Breadth-first search with a slice-queue and a head index visits each vertex and edge once: **O(V + E) time, O(V) space**, shortest paths in an unweighted graph. Dijkstra uses `container/heap` for **O((V + E) log V)**.
"""),
GRAPH,
M("""## 12. Errors, panics and edge cases

Go does **not** use exceptions for ordinary failures. Functions return an `error` as the last result; callers check it: `v, err := strconv.Atoi(s); if err != nil { ... }`. Create errors with `errors.New("...")` or `fmt.Errorf("...: %w", err)` (the `%w` verb **wraps** an error, and `errors.Is(err, target)` / `errors.As` inspect the chain). A **`panic`** is for bugs: out-of-range index, nil map write, nil pointer dereference, integer divide by zero, failed type assertion. A panic unwinds the stack running `defer`red functions; `recover()` inside a deferred function stops it. In algorithm code you mostly **prevent** panics by checking edge cases:

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty slice | `a[0]` panics | check `len(a) == 0` |
| Index out of range | `a[len(a)]` panics | validate indexes; loop with `range` |
| Missing map key | zero value mistaken for a real value | comma-ok `v, ok := m[k]` |
| Write to nil map | panic | `make` the map |
| Overflow | wraps silently | `int64`, check before multiplying |
| `append` aliasing | unexpected overwrite | copy with `slices.Clone` |
| Integer division by zero | panic | check the divisor |
| Unused variable / import | compile error | delete it or use `_` |
"""),
ERR,
M("""## 13. The master complexity cheat sheet

`n` is the number of elements.

| Operation | slice | array | `map` | `container/list` | `container/heap` | `string` |
|---|---|---|---|---|---|---|
| Access by index / key | O(1) | O(1) | O(1) avg | O(n) | root `h[0]` O(1) | O(1) (byte) |
| Append / push at end | O(1)* | n/a | O(1)* insert | O(1) | O(log n) | O(n) (`+`) |
| Pop from end | O(1) | n/a | n/a | O(1) | O(log n) | n/a |
| Pop from front | O(1) (re-slice) | n/a | n/a | O(1) | n/a | O(1) (re-slice) |
| Insert / delete in the middle | O(n) | n/a | O(1) avg by key | O(1) at element | O(log n) (`heap.Fix`/`Remove`) | O(n) |
| Search by value | O(n) | O(n) | O(1) avg by key | O(n) | O(n) | O(n·m) |
| Min / max | O(n) | O(n) | O(n) | O(n) | O(1) | O(n) |
| Sort | O(n log n) | O(n log n) | n/a | n/a | n/a | via `[]byte` |
| Copy | O(n) | O(n) | O(n) | O(n) | O(n) | O(1) (header) |
| Space | O(n) | O(n) | O(n), heavier | O(n) + nodes | O(n) | O(n) |

\\* amortised or average.

**What to say in an interview:** "I'll keep a `map[int]int` from value to index. Each lookup and insert is O(1) on average, so one pass is O(n) time with O(n) extra space. The brute force is O(n²) time and O(1) space; I'm trading memory for time."

## 14. Pitfalls and idioms checklist

1. Always assign the result of `append`: `a = append(a, x)`.
2. Re-slicing **shares memory**; use `copy` / `slices.Clone` for independence.
3. Reading a missing map key returns the zero value; use comma-ok.
4. Writing to a **nil map** panics; `make` it.
5. Map iteration order is random; sort keys when order matters.
6. Integer overflow wraps silently; `int` is 64-bit but `int32` is not.
7. `len(s)` counts **bytes**; use `[]rune(s)` for characters.
8. There are no implicit conversions: write `int64(x)`.
9. Unused variables and imports do not compile.
10. Use pointer receivers when a method must modify the struct.
11. Slices, maps and functions cannot be map keys; use arrays or structs.
12. Call `heap.Push`/`heap.Pop`, not the type's own `Push`/`Pop` methods.
13. `for _, v := range s` gives a **copy** of each element: modify via the index (`s[i].x = ...`).
14. A panic crashes the program unless recovered: check bounds and divisors.

## 15. A reusable solution template
"""),
codes[8],
M("""## 16. How Go compares with the other languages in this chapter

| Topic | Go | Java | C++ | Python |
|---|---|---|---|---|
| Dynamic array | slice | `ArrayList` | `vector` | `list` |
| Hash map | `map` | `HashMap` | `unordered_map` | `dict` |
| Set | `map[T]struct{}` | `HashSet` | `unordered_set` | `set` |
| Sorted map | none | `TreeMap` | `map` | none |
| Heap | `container/heap` | `PriorityQueue` | `priority_queue` | `heapq` |
| Errors | return values | exceptions | exceptions / UB | exceptions |
| Overflow | wraps | wraps | UB (signed) | none |
| Missing key | zero value | `null` | inserts default | `KeyError` |

## 17. Practice plan

1. Run every program on the Go Playground, change the inputs, and explain each output.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest), [Cheapest Route](/problems/cheapest-route) and [Shortest Maze Path](/problems/shortest-maze-path) in Go, writing each time and space complexity; the third needs `container/heap`.
3. Practise the heap boilerplate until you can write it from memory; it is the part of Go interviews people forget.
"""),
], quiz=OLD["quiz"] + [
 Q("After `b := a[:2]; b = append(b, 100)` where `cap(a) > 2`:", ["a[2] is overwritten", "a is unchanged", "A new array is always created", "It panics"], 0, "append writes into the shared backing array when capacity allows."),
 Q("What does a method with a value receiver receive?", ["A copy of the struct", "A pointer", "The original struct", "Nothing"], 0, "Use a pointer receiver to mutate."),
 Q("Which can be a map key in Go?", ["[2]int", "[]int", "map[int]int", "func()"], 0, "Slices, maps and functions are not comparable."),
 Q("How do you build a min-heap with container/heap?", ["Implement Len, Less, Swap, Push, Pop with Less using <", "Call sort.Ints", "Use a map", "Use list.New"], 0, "Then call heap.Push and heap.Pop."),
])
