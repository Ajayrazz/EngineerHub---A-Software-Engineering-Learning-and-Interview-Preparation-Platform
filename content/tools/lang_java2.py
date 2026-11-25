"""Deep version of the Java lesson. Reuses the verified programs of lang_java.py (by position) and adds new ones."""
from courses_a import Q, L
from lang_java import JAVA as OLD

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "java", code, cap)
codes = [b for b in OLD["blocks"] if b[0] == "code"]   # 0 input,1 overflow,2 strings,3 arrays,4 maps,5 stacks/heap,6 sorting,7 nodes,8 template

REFS = C("""import java.util.*;

public class Main {
    static void mutate(int[] arr, int x, List<Integer> list, StringBuilder sb) {
        arr[0] = 99;          // changes the caller's array (same object)
        x = 99;               // changes only the local copy of the int
        list.add(7);          // changes the caller's list
        sb.append("!");       // changes the caller's builder
        sb = new StringBuilder("new");   // rebinding the local reference: caller unaffected
    }

    public static void main(String[] args) {
        int[] arr = {1, 2, 3};
        int x = 5;
        List<Integer> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder("hi");
        mutate(arr, x, list, sb);
        System.out.println(arr[0] + " " + x + " " + list + " " + sb);   // 99 5 [7] hi!

        String a = new String("abc"), b = new String("abc");
        System.out.println((a == b) + " " + a.equals(b));               // false true
        Integer p = 127, q = 127, r = 1000, s = 1000;
        System.out.println((p == q) + " " + (r == s) + " " + r.equals(s)); // true false true

        int[] copy = arr.clone();                                        // shallow copy of the array
        copy[0] = -1;
        System.out.println(arr[0] + " " + copy[0]);                      // 99 -1
        int[][] grid = {{1, 2}, {3, 4}};
        int[][] shallow = grid.clone();                                  // rows are SHARED
        shallow[0][0] = 100;
        System.out.println(grid[0][0]);                                  // 100
    }
}""", "Pass by value, reference semantics, == versus equals, shallow copies")

HASH = C("""import java.util.*;

public class Main {
    static final class Point {
        final int r, c;
        Point(int r, int c) { this.r = r; this.c = c; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return r == p.r && c == p.c;
        }
        @Override public int hashCode() { return Objects.hash(r, c); }
    }

    public static void main(String[] args) {
        Set<Point> seen = new HashSet<>();
        seen.add(new Point(1, 2));
        System.out.println(seen.contains(new Point(1, 2)));          // true (equals + hashCode)

        Set<int[]> bad = new HashSet<>();
        bad.add(new int[]{1, 2});
        System.out.println(bad.contains(new int[]{1, 2}));           // false: arrays use identity

        Set<List<Integer>> ok = new HashSet<>();
        ok.add(List.of(1, 2));
        System.out.println(ok.contains(List.of(1, 2)));              // true: List has value equality

        record Cell(int r, int c) {}                                  // records get equals/hashCode for free
        Set<Cell> cells = new HashSet<>(List.of(new Cell(0, 0)));
        System.out.println(cells.contains(new Cell(0, 0)));          // true

        Map<Integer, Integer> lru = new LinkedHashMap<>(16, 0.75f, true) {   // access-ordered: an LRU cache
            protected boolean removeEldestEntry(Map.Entry<Integer, Integer> e) { return size() > 2; }
        };
        lru.put(1, 1); lru.put(2, 2); lru.get(1); lru.put(3, 3);
        System.out.println(lru.keySet());                            // [1, 3]
    }
}""", "Keys need equals and hashCode; records; a LinkedHashMap LRU cache")

GRAPH = C("""import java.util.*;

public class Main {
    public static void main(String[] args) {
        int n = 6;
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {2, 5}};
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int[] e : edges) { adj.get(e[0]).add(e[1]); adj.get(e[1]).add(e[0]); }   // undirected

        int[] dist = new int[n];
        Arrays.fill(dist, -1);
        Deque<Integer> q = new ArrayDeque<>();
        dist[0] = 0; q.add(0);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : adj.get(u)) {
                if (dist[v] == -1) { dist[v] = dist[u] + 1; q.add(v); }
            }
        }
        System.out.println(Arrays.toString(dist));                   // [0, 1, 1, 2, 3, 2]
    }
}""", "A graph as an adjacency list, and breadth-first search for shortest distances")

ERR = C("""import java.util.*;

public class Main {
    static int parsePositive(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v <= 0) throw new IllegalArgumentException("not positive: " + v);
            return v;
        } catch (NumberFormatException e) {
            return -1;                                               // bad text becomes a sentinel
        } finally {
            // runs always: close resources here in real code
        }
    }

    public static void main(String[] args) {
        System.out.println(parsePositive(" 42 "));                   // 42
        System.out.println(parsePositive("abc"));                    // -1
        try { parsePositive("-5"); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }

        int[] a = new int[3];
        try { a[3] = 1; } catch (ArrayIndexOutOfBoundsException e) { System.out.println("index error"); }
        String s = null;
        try { s.length(); } catch (NullPointerException e) { System.out.println("null error"); }
        System.out.println(10 / 3 + " " + (10 % 3));
        try { System.out.println(1 / 0); } catch (ArithmeticException e) { System.out.println("divide by zero"); }
        System.out.println(1.0 / 0);                                 // Infinity (no exception for doubles)

        Optional<Integer> first = Arrays.asList(4, 8, 15).stream().filter(x -> x > 5).findFirst();
        System.out.println(first.orElse(-1));                        // 8
    }
}""", "Exceptions, sentinel values and the edge cases that crash programs")

JAVA2 = L("java-for-dsa", "Java for DSA", "free", 75, "", blocks=[
M("""# Java for Data Structures and Algorithms: a complete guide

This lesson is a full tour of Java **as you need it for algorithmic problem solving**. It explains what Java is, how a Java program actually runs, every data type and operator you will use, how control flow and methods behave (including the often-misunderstood "pass by value" rule), and then every data structure in the Java Collections Framework with its **time and space complexity for every operation**. Each idea comes with a program you can run. Read it once from top to bottom, then keep it as a reference.

## 1. What Java is, and why it behaves the way it does

**Java** was created in the 1990s with the slogan *write once, run anywhere*. You write source code in `.java` files; the compiler `javac` translates it into **bytecode** (`.class` files), a compact instruction set for an imaginary machine. The **Java Virtual Machine (JVM)** executes that bytecode. A modern JVM starts by interpreting bytecode and then uses a **just-in-time (JIT) compiler** to translate frequently used ("hot") methods into native machine code while the program runs, so well-written Java is typically only a small factor slower than C++.

Four properties explain most of Java's behaviour:

| Property | What it means | Consequence for you |
|---|---|---|
| **Statically typed** | Every variable and expression has a type known at compile time | Type mistakes are compile errors; you must declare types (`int x`, `List<String> names`) |
| **Object-oriented** | Code lives in classes; almost everything (except primitives) is an object | A program needs at least one class with a `main` method |
| **Garbage collected** | The JVM frees unreachable objects automatically | No `free`/`delete`; memory leaks mostly come from forgotten references |
| **Strongly specified** | Integer sizes, overflow behaviour and evaluation order are defined by the language | The same program gives the same answers on every machine |

### Where Java is used
Large backend systems, Android (through Kotlin and Java), financial software, big-data tools (Hadoop, Spark, Kafka) and enterprise platforms. Because so many companies use it, interviewers read Java comfortably, and many "design a class" questions are naturally expressed in it.

### Strengths and weaknesses for interviews

| Strengths | Weaknesses |
|---|---|
| A rich, consistent standard library (the Collections Framework) | More typing than Python: declarations, generics, boilerplate |
| Static types catch many bugs before you run | No operator overloading: strings need `.equals`, not `==` |
| Predictable performance and no undefined behaviour | `int` arithmetic **overflows silently** |
| Excellent for object-oriented design questions | Generics cannot hold primitives, so collections box `int` into `Integer` (extra memory and time) |

## 2. Anatomy of a Java program

Every program is a **class** containing a method called `main`. The line `public static void main(String[] args)` means: *public* (visible everywhere), *static* (belongs to the class, no object needed), *void* (returns nothing), taking the command-line arguments. You compile with `javac Main.java` and run with `java Main`, or run a single file directly with `java Main.java` (Java 11+).

Practice problems give input on **standard input** and expect output on **standard output**. `Scanner` is the easiest reader but slow for large input; `BufferedReader` with `StringTokenizer` is the fast, standard choice. Output with `System.out.println` is slow when called hundreds of thousands of times, so collect lines in a `StringBuilder` and print once. Reading the first two numbers and then an array looks like this:
"""),
codes[0],
M("""**How it works.** `BufferedReader` reads big chunks of bytes into a buffer instead of asking the operating system for every character, which is why it is fast. `readLine()` returns one line (or `null` at end of input); `StringTokenizer` splits it into tokens; `Integer.parseInt` converts text to a number and throws `NumberFormatException` if the text is not a valid integer.

## 3. Data types in depth

Java has two families of types.

1. **Primitive types** hold a value directly (they live in a variable or inside an array, not as separate objects).
2. **Reference types** (classes, interfaces, arrays, enums) hold a *reference* (like an address) to an object on the **heap**. The special value `null` means "no object".

### 3.1 The eight primitive types

| Type | Size | Range | Default | Typical use |
|---|---|---|---|---|
| `byte` | 8 bits | -128 to 127 | 0 | raw bytes, small arrays |
| `short` | 16 bits | -32,768 to 32,767 | 0 | rarely needed |
| `int` | 32 bits | -2,147,483,648 to 2,147,483,647 (about ±2.1 × 10⁹) | 0 | the default integer |
| `long` | 64 bits | about ±9.2 × 10¹⁸ | 0L | large sums, products, timestamps |
| `float` | 32 bits | about 7 decimal digits of precision | 0.0f | rarely used |
| `double` | 64 bits | about 15-16 decimal digits of precision | 0.0 | decimals, averages |
| `char` | 16 bits | 0 to 65,535 (a UTF-16 code unit) | '\\u0000' | single characters; behaves like a number |
| `boolean` | JVM-defined | `true` or `false` | false | conditions |

**Local variables have no default value**: using one before assigning it is a compile error. **Fields and array elements** get the defaults above (arrays of objects default to `null`).

### 3.2 Literals and readability
`123` is an `int`; `123L` is a `long`; `1.5` is a `double`; `1.5f` is a `float`; `'a'` is a `char`; `"abc"` is a `String`. Underscores make big numbers readable: `1_000_000_007`. Hexadecimal `0xFF`, binary `0b1010`, and octal `017` (a leading zero means octal!) are also allowed.

### 3.3 Type inference with `var`
Since Java 10 you can write `var list = new ArrayList<Integer>();` inside methods; the compiler infers the type. It is still statically typed. Use it to shorten long generic types, not to hide meaning.

### 3.4 Conversion and casting
* **Widening** (smaller to larger: `int` to `long` to `double`) happens automatically and loses no information (except precision when `long` goes to `float`/`double`).
* **Narrowing** (larger to smaller) needs an explicit cast and can lose information: `int x = (int) 3.9;` gives `3` (truncation toward zero), and `(byte) 200` gives `-56` (the low 8 bits reinterpreted).
* In an expression Java first promotes `byte`, `short` and `char` operands to `int`. So `char c = 'a'; c + 1` is the `int` 98; write `(char) (c + 1)` to get `'b'`.
* `int` divided by `int` is **integer division**; make one operand a `double` to get a fraction.

### 3.5 Overflow, underflow and floating-point error (very important)
Integer arithmetic in Java is **two's complement and wraps around silently**. `Integer.MAX_VALUE + 1` equals `Integer.MIN_VALUE`. No exception is thrown. This is the number-one source of wrong answers in Java interviews: a sum of 10⁵ numbers each up to 10⁹ needs a `long`. Rules of thumb:

1. If a value can exceed about two billion, use `long`.
2. **Cast before you multiply**: `(long) a * b`, not `(long) (a * b)` (the second multiplies as `int` first and overflows).
3. Use `Math.addExact`, `Math.multiplyExact` while debugging; they throw `ArithmeticException` on overflow.
4. `Math.abs(Integer.MIN_VALUE)` is still negative, because +2³¹ does not fit in an `int`.

Floating-point numbers follow the IEEE 754 standard: many decimals such as 0.1 cannot be stored exactly, so `0.1 + 0.2 == 0.3` is **false**. Compare with a tolerance (`Math.abs(a - b) < 1e-9`) and avoid doubles when exact integer answers are required. `1.0 / 0` gives `Infinity` and `0.0 / 0` gives `NaN` (not an exception), while `1 / 0` (integers) throws `ArithmeticException`.

### 3.6 Wrapper classes and boxing
Each primitive has a wrapper class (`Integer`, `Long`, `Double`, `Character`, `Boolean`). Collections hold objects, so `List<Integer>` stores `Integer` objects; Java converts automatically (**autoboxing**/**unboxing**). Boxing costs memory (an `Integer` object is about 16 bytes versus 4 for an `int`) and time, so use primitive arrays (`int[]`) in hot loops. Beware `==` on wrappers: `Integer` values from -128 to 127 are cached, so `==` happens to work for small numbers and fails for large ones. Always use `.equals` on wrapper objects. The following program demonstrates overflow, division and modulo:
"""),
codes[1],
M("""## 4. Operators and expressions

| Group | Operators | Notes |
|---|---|---|
| Arithmetic | `+ - * / %` | `/` on two ints truncates toward zero; `%` keeps the sign of the left operand (`-7 % 3 == -1`). Use `Math.floorMod(a, m)` for a non-negative modulo |
| Comparison | `== != < <= > >=` | `==` compares primitives by value and objects by reference |
| Logical | `&& \\|\\| !` | `&&` and `\\|\\|` **short-circuit**: the right side runs only if needed, so `i < n && a[i] > 0` is safe |
| Bitwise | `& \\| ^ ~ << >> >>>` | `>>` keeps the sign, `>>>` fills with zeros; shifts use the low 5 bits (int) or 6 bits (long) of the count |
| Assignment | `= += -= *= /= %= <<= ...` | compound assignment includes an implicit cast: `byte b = 1; b += 1;` compiles |
| Increment | `++ --` | `i++` yields the old value, `++i` the new one; avoid using them inside bigger expressions |
| Conditional | `cond ? a : b` | an expression, not a statement |

Operator precedence is the usual one (multiplication before addition, comparison before logical). When in doubt add parentheses; clarity beats cleverness.

## 5. Control flow

**Conditionals.** `if (cond) {...} else if (cond) {...} else {...}`. The condition must be a `boolean` (unlike C, `if (x)` with an int is an error). `switch` compares one value against constants; since Java 14 the arrow form `case 1, 2 -> ...;` needs no `break` and can be used as an expression.

**Loops.**

| Loop | Use it when | Example |
|---|---|---|
| `for (init; cond; update)` | you know the count | `for (int i = 0; i < n; i++)` |
| `for (T x : collection)` | you want each element, not the index | `for (int v : arr)` |
| `while (cond)` | repeat until something changes | binary search, two pointers |
| `do { } while (cond)` | run at least once | reading until valid |

`break` leaves the innermost loop; `continue` skips to the next iteration; a **label** lets you break out of nested loops (`outer: for (...) { for (...) { break outer; } }`).

**Counting the cost.** A single loop over `n` items is O(n); a loop nested inside another over the same data is O(n²); a loop that halves the range each time (binary search) is O(log n); two pointers that each move forward at most `n` times total O(n), even though the code contains a loop inside a loop. Always count how many times the *innermost statement* runs in the worst case.

## 6. Methods in depth

A method has a return type, a name, parameters and a body. Overloading allows several methods with the same name and different parameter lists. `static` methods belong to the class; instance methods need an object. Variable arguments use `int... nums`. Recursion is allowed; every call uses a **stack frame**, and the default stack allows roughly 10,000 to 20,000 nested calls, so very deep recursion (for example a linked list of 100,000 nodes) throws `StackOverflowError`: use a loop.

### 6.1 Java is *always* pass-by-value
This is the most misunderstood rule. When you call `f(x)`, Java **copies the value of `x`** into the parameter. For a primitive, the value is the number itself. For an object, the value is the **reference** (the address), so the method gets a copy of the reference that points to the *same object*. Consequences:

1. The method can **mutate** the object (add to the list, change array elements) and the caller sees it.
2. The method **cannot make the caller's variable point to a different object**: reassigning the parameter changes only the local copy.

Run the program below and predict each line before reading the output. It also shows `==` versus `.equals`, the `Integer` cache and shallow copies.
"""),
REFS,
M("""## 7. Strings in depth

A `String` is an **immutable** sequence of UTF-16 characters. Immutable means that no method changes a string; methods return a *new* string. This makes strings safe to share and to use as hash-map keys (the hash code is computed once and cached), but it has a performance consequence: `s = s + "x"` in a loop copies the whole string each time, so n appends cost O(n²). Use `StringBuilder`, a mutable buffer with amortised O(1) append.

Java keeps a **string pool**: literals such as `"abc"` that are equal share one object, so `"abc" == "abc"` is true, but `new String("abc") == "abc"` is false. Never rely on this; use `.equals`.

### 7.1 Operations and their costs
Let `n` be the length of the string (and `m` the length of the other argument).

| Operation | Code | Time | Notes |
|---|---|---|---|
| Length | `s.length()` | O(1) | stored in the object |
| Character at index | `s.charAt(i)` | O(1) | throws `StringIndexOutOfBoundsException` if out of range |
| Compare content | `s.equals(t)`, `s.compareTo(t)` | O(min(n, m)) | `equals` first checks length |
| Substring `[a, b)` | `s.substring(a, b)` | O(b - a) | copies the characters (since Java 7) |
| Concatenate | `s + t`, `s.concat(t)` | O(n + m) | new string |
| Search | `s.indexOf(t)`, `s.contains(t)` | O(n·m) worst | naive scan |
| Split | `s.split(" ")` | O(n) | uses a regular expression; slow for tiny splits in hot loops |
| To char array | `s.toCharArray()` | O(n) | copy; then you may mutate and `new String(chars)` |
| Hash code | `s.hashCode()` | O(n) first time, then O(1) | cached |
| `StringBuilder.append` | `sb.append(x)` | amortised O(1) | `insert(0, x)` and `deleteCharAt(0)` are O(n) |
| `StringBuilder.reverse` | `sb.reverse()` | O(n) | in place |

**Space:** a string of length n uses about n bytes for Latin-1 text (compact strings, Java 9+) or 2n bytes otherwise, plus a small object header.

### 7.2 Characters are numbers
`char` is a 16-bit unsigned number: `'a' - 'a' == 0`, `'c' - 'a' == 2`. That is why a frequency table is an `int[26]` indexed by `c - 'a'` (O(1) update, 26 ints of space), and why `c - '0'` converts a digit character to its numeric value. `Character.isDigit`, `isLetter`, `toUpperCase` help with classification. Characters outside the basic plane (many emoji) take **two** `char`s (a surrogate pair), which matters only for Unicode-heavy problems.
"""),
codes[2],
M("""## 8. Arrays and `ArrayList` in depth

### 8.1 Arrays
An array is a **fixed-length block of contiguous memory**: `int[] a = new int[n];` allocates `n` zeroed ints. Because elements are contiguous, `a[i]` is computed as *start address + i × element size*: a single step, **O(1)**, and iterating is fast thanks to the CPU cache. The length is fixed at creation and available as `a.length` (a field, not a method). Out-of-range access throws `ArrayIndexOutOfBoundsException`; Java never reads memory it should not.

* Multi-dimensional arrays are **arrays of arrays**: `int[][] g = new int[r][c]` makes `r` row objects, so rows can even have different lengths (**jagged** arrays).
* Arrays are reference types: `int[] b = a;` makes `b` point to the same array; `a.clone()` or `Arrays.copyOf(a, n)` makes a **shallow copy** (for an array of arrays the inner rows are still shared).
* `java.util.Arrays` provides `sort`, `fill`, `toString`/`deepToString`, `copyOfRange`, `equals`, `binarySearch`, `asList`, `stream`.

### 8.2 `ArrayList<E>`: the dynamic array
`ArrayList` wraps an array that **grows when full**: when the internal array is full, it allocates a new one about **1.5×** larger and copies the elements (O(n) for that one `add`). Because growth is geometric, the *average* cost of `add` over many calls is **amortised O(1)**.

*Why amortised O(1)?* If the capacity multiplies by 1.5 each time, the copy costs form a geometric series 1 + 1.5 + 1.5² + ..., whose total is proportional to the final size n. Spread over n additions that is a constant per addition.

| Operation | Time | Space | Notes |
|---|---|---|---|
| `get(i)`, `set(i, x)` | O(1) | O(1) | direct indexing |
| `add(x)` (at end) | amortised O(1), worst O(n) | O(1) amortised | occasional resize |
| `add(i, x)` (insert) | O(n) | O(1) | shifts the tail right |
| `remove(i)` | O(n) | O(1) | shifts the tail left; removing the last element is O(1) |
| `remove(Object)`, `contains`, `indexOf` | O(n) | O(1) | linear search using `equals` |
| `size()`, `isEmpty()` | O(1) | O(1) | |
| `sort` | O(n log n) | O(n) | TimSort, stable |
| iterate | O(n) | O(1) | |
| whole structure | | O(n) | plus about 25% extra capacity on average, plus boxing |

**Trap:** `list.remove(1)` removes **index 1**, while `list.remove(Integer.valueOf(1))` removes the **value 1**. And never remove elements inside a for-each loop over the same list (`ConcurrentModificationException`); use an index loop or `removeIf`.
"""),
codes[3],
M("""## 9. Hash tables: `HashMap` and `HashSet` in depth

### 9.1 How hashing works
A **hash map** stores key-value pairs so that lookup does not require scanning. Java's `HashMap` keeps an array of **buckets** (initially 16). To store a key it computes `hashCode()`, mixes the bits, and takes the result modulo the table size to pick a bucket. If two keys land in the same bucket (a **collision**) they are chained in a linked list; since Java 8 a bucket that grows beyond 8 entries is converted to a balanced tree, bounding the worst case at O(log n). When the number of entries exceeds **load factor (0.75) × capacity**, the table **doubles** and every entry is re-inserted (**rehash**, O(n), amortised away over many inserts).

A **`HashSet<E>`** is a `HashMap<E, Object>` that only uses the keys.

### 9.2 What makes a good key
Keys must have a consistent `equals` and `hashCode`: equal objects must return equal hash codes. Strings, boxed numbers, lists, records and enums already do. **Arrays do not** (they use identity), and mutating a key after inserting it breaks the map. For coordinates use a `record`, a `List<Integer>`, or encode `r * cols + c` as a single `int`/`long`.

### 9.3 Operations and costs

| Operation | `HashMap` / `HashSet` | `TreeMap` / `TreeSet` | `LinkedHashMap` |
|---|---|---|---|
| `put`, `add` | O(1) average, O(n) worst (O(log n) with tree buckets) | O(log n) | O(1) average |
| `get`, `containsKey`, `contains` | O(1) average | O(log n) | O(1) average |
| `remove` | O(1) average | O(log n) | O(1) average |
| Iterate all | O(capacity + n) | O(n) in key order | O(n) in insertion order |
| `firstKey`, `lastKey`, `floorKey`, `ceilingKey` | not available | O(log n) | not available |
| Space | O(n) with about 40-50 bytes of overhead per entry | O(n) | O(n) plus links |

`TreeMap`/`TreeSet` are **red-black trees**: self-balancing binary search trees, which is why every operation is O(log n) and keys come out sorted. Use them when you need the nearest key (`floorKey(x)` = greatest key ≤ x, `ceilingKey(x)` = least key ≥ x) or sorted iteration. `LinkedHashMap` adds a linked list through the entries to remember insertion (or access) order, which makes LRU caches easy.

Handy methods: `getOrDefault(k, d)`, `merge(k, 1, Integer::sum)` (counting), `putIfAbsent`, `computeIfAbsent(k, f)` (grouping), `entrySet()` for iterating pairs, `keySet()`, `values()`. Iteration order of `HashMap` is unspecified: never depend on it.
"""),
codes[4],
HASH,
M("""## 10. Stacks, queues, deques and heaps in depth

### 10.1 What each structure promises
* **Stack**: last in, first out (LIFO): `push`, `pop`, `peek`. Models undo, bracket matching, DFS, expression evaluation.
* **Queue**: first in, first out (FIFO): `offer`, `poll`, `peek`. Models waiting lines and breadth-first search.
* **Deque** ("double-ended queue"): add and remove at both ends. Models sliding-window maximum and palindromes.
* **Priority queue**: always gives the element with the *highest priority* (smallest by default) first. Models scheduling, top-k and Dijkstra's shortest paths.

### 10.2 Which Java class to use
`ArrayDeque<E>` is a **circular array** that grows by doubling; it implements the stack, queue and deque interfaces and is the right default for all three. (The old `Stack` and `Vector` classes are synchronised and slower; `LinkedList` allocates a node per element.) `PriorityQueue<E>` is a **binary min-heap** stored in an array.

### 10.3 How a binary heap works
A heap is a complete binary tree stored in an array where each parent is ≤ its children. For index `i`, the children are `2i + 1` and `2i + 2` and the parent is `(i - 1) / 2`. **Insert:** put the element at the end and **sift up** (swap with its parent while smaller): at most log n swaps. **Remove-min:** take the root, move the last element to the root and **sift down**: at most log n swaps. **Peek** is the root: O(1). **Build a heap** from n elements by sifting down from the middle takes only **O(n)**, which is what `new PriorityQueue<>(collection)` does.

### 10.4 Operations and costs

| Operation | `ArrayDeque` | `PriorityQueue` | `LinkedList` |
|---|---|---|---|
| push / add at an end | O(1) amortised | `offer` O(log n) | O(1) |
| pop / poll from an end | O(1) | `poll` O(log n) | O(1) |
| peek | O(1) | O(1) | O(1) |
| search / `contains` | O(n) | O(n) | O(n) |
| remove an arbitrary element | O(n) | O(n) | O(1) given the node, O(n) to find it |
| iterate | O(n) | O(n), **not sorted** | O(n) |
| space | O(n) | O(n) | O(n), with a node object per element |

For a max-heap pass `Comparator.reverseOrder()`. For objects pass a comparator, for example by the first field of an `int[]`. Never iterate a `PriorityQueue` expecting sorted order; only `poll` returns the smallest. To find the k largest of n numbers keep a **min-heap of size k**: O(n log k) time, O(k) space.
"""),
codes[5],
M("""## 11. Sorting and searching in depth

### 11.1 What Java sorts with
* `Arrays.sort(int[])` and other primitive arrays use **dual-pivot quicksort**: O(n log n) on average, in place (O(log n) extra stack), **not stable** (stability is irrelevant for primitives).
* `Arrays.sort(T[], comparator)`, `Collections.sort(list)` and `list.sort(c)` use **TimSort**: a merge-sort hybrid, O(n log n) worst case, **stable** (equal elements keep their order), about O(n) extra space, and close to O(n) on nearly sorted data.

### 11.2 Writing comparators
A comparator returns a negative number if the first argument should come first, zero if equal, positive otherwise. Build them with `Comparator.comparingInt(...)`, `.thenComparing(...)`, `.reversed()`. **Never** write `(a, b) -> a - b` for general ints: the subtraction can overflow and give the wrong sign. Use `Integer.compare(a, b)`. Sorting a `List<int[]>` of intervals by start: `(x, y) -> Integer.compare(x[0], y[0])`.

### 11.2.1 Binary search
`Arrays.binarySearch(a, key)` and `Collections.binarySearch(list, key)` need a **sorted** input and take O(log n). They return the index if found, otherwise `-(insertionPoint) - 1`, so a negative result tells you where the key would go. They give *a* matching index, not necessarily the first; for "first position ≥ x" write your own `lo/hi` loop (see the Binary Search chapter).
"""),
codes[6],
M("""## 12. Classes, objects and the memory model

A **class** is a blueprint with **fields** (data) and **methods** (behaviour); an **object** is an instance created with `new`. Objects live on the **heap**; local variables and method frames live on the **stack**. A variable of class type holds a reference or `null`. Calling a method on `null` throws `NullPointerException`; guard with `if (node != null)`.

* **`static` nested classes** (`static class ListNode`) do not hold a hidden reference to an outer object, so they are the right choice for nodes.
* **`record Point(int r, int c)`** (Java 16+) defines an immutable data class with a constructor, accessors, `equals`, `hashCode` and `toString` generated for you.
* **`equals` and `hashCode`** must agree. The default `equals` is identity (same object). Override both for value equality, otherwise sets and maps misbehave.
* **Garbage collection** reclaims objects that nothing can reach. Long-lived structures that keep growing (an unbounded cache) are the usual "leak".
* **Interfaces and generics.** `List<E>` is an interface; `ArrayList<E>` is one implementation. Program to the interface (`List<Integer> a = new ArrayList<>();`) so you can swap implementations.

### 12.1 Linked structures: lists and trees
A **linked list node** stores a value and a reference to the next node. Insert or delete **after a node you already hold** is O(1); reaching the i-th node costs O(i). A **binary tree node** stores a value and two child references; recursion mirrors the structure.
"""),
codes[7],
M("""### 12.2 Graphs
A graph is usually stored as an **adjacency list**: for each node a list of neighbours. It uses O(V + E) space (V nodes, E edges) and lets you enumerate neighbours in time proportional to their count. An **adjacency matrix** (`boolean[V][V]`) uses O(V²) space and answers "is there an edge u-v?" in O(1); choose it only for dense graphs or small V. Breadth-first search with a queue visits each node and edge once: **O(V + E) time, O(V) space**, and in an unweighted graph the first time you reach a node is a shortest path.
"""),
GRAPH,
M("""## 13. Handling errors and edge cases

Java reports problems with **exceptions**. *Checked* exceptions (like `IOException`) must be declared or caught; *unchecked* ones (subclasses of `RuntimeException`, such as `NullPointerException`, `ArrayIndexOutOfBoundsException`, `ArithmeticException`, `NumberFormatException`) do not. Use `try { ... } catch (SomeException e) { ... } finally { ... }`; `finally` always runs. In algorithm problems you mostly *prevent* exceptions by checking the **edge cases** first:

| Edge case | Typical bug | Defence |
|---|---|---|
| Empty input or `n = 0` | `a[0]` throws | check `if (n == 0) return ...` |
| Single element | loops that compare neighbours skip it | test with n = 1 |
| Duplicates | a map overwrites an index | decide first-wins or last-wins |
| Negative numbers | `%` or `Math.abs` surprises | `Math.floorMod`, test with -1 |
| Maximum size | `int` overflow, timeouts | use `long`, estimate the complexity |
| No answer exists | returning garbage | return a clear sentinel such as -1 |
"""),
ERR,
M("""## 14. The master complexity cheat sheet

Time (average unless noted) and the space the structure itself uses. `n` is the number of elements.

| Operation | `int[]` | `ArrayList` | `LinkedList` | `HashMap`/`HashSet` | `TreeMap`/`TreeSet` | `ArrayDeque` | `PriorityQueue` |
|---|---|---|---|---|---|---|---|
| Access by index | O(1) | O(1) | O(n) | n/a | n/a | n/a | n/a |
| Insert at end | n/a | O(1)* | O(1) | O(1)* | O(log n) | O(1)* | O(log n) |
| Insert at front | n/a | O(n) | O(1) | n/a | n/a | O(1)* | n/a |
| Insert in the middle | n/a | O(n) | O(1) after finding | n/a | n/a | n/a | n/a |
| Delete from end | n/a | O(1) | O(1) | O(1)* | O(log n) | O(1) | `poll` O(log n) |
| Delete from front | n/a | O(n) | O(1) | n/a | n/a | O(1) | n/a |
| Search by value | O(n) | O(n) | O(n) | O(1)* | O(log n) | O(n) | O(n) |
| Min or max | O(n) | O(n) | O(n) | O(n) | O(log n) | O(n) | O(1) peek |
| Sorted order | sort O(n log n) | sort O(n log n) | sort O(n log n) | n/a | iteration O(n) | n/a | repeated poll O(n log n) |
| Space for the structure | O(n) | O(n) | O(n) + node objects | O(n) + entry objects | O(n) + node objects | O(n) | O(n) |

\\* amortised or average. Boxed collections (`ArrayList<Integer>`) use roughly 4 to 5 times the memory of `int[]`.

### What to say in an interview
"I will use a `HashMap` from value to index. Each lookup and insert is O(1) on average, so one pass is O(n) time, and the map holds up to n entries, so O(n) extra space. A brute force would be O(n²) time and O(1) space; I am trading memory for time."

## 15. Pitfalls and idioms checklist

1. Compare strings and boxed numbers with `.equals`, never `==`.
2. Cast to `long` **before** multiplying: `(long) a * b`.
3. `list.remove(int)` removes by index, `remove(Object)` by value.
4. Do not modify a collection while iterating it with for-each.
5. `char + int` is an `int`; cast back with `(char)`.
6. `int[]` has no `contains` or `indexOf`; loop, or use `Arrays.stream`.
7. `Math.abs(Integer.MIN_VALUE)` is negative; `-Integer.MIN_VALUE` too.
8. Prefer `ArrayDeque` over `Stack` and `LinkedList` for stacks and queues.
9. `PriorityQueue` iteration order is **not** sorted.
10. Use `StringBuilder` for repeated appends; call `setLength` or `deleteCharAt(sb.length() - 1)` to remove the last character.
11. Use `Arrays.fill(dist, Integer.MAX_VALUE)`, but beware `MAX_VALUE + 1` overflow in distance updates: use `long` or check first.
12. Arrays of generics cannot be created (`new List<Integer>[n]` is an error); use `List<List<Integer>>`.
13. Recursion depth is limited; use loops for deep structures.
14. Declare variables in the smallest scope; initialise before use.

## 16. A reusable solution template

Keep input and output in `main`, and the algorithm in a small pure method. It is easier to test, to explain, and to reuse:
"""),
codes[8],
M("""## 17. How Java compares with the other languages in this chapter

| Topic | Java | Python | C++ | Go |
|---|---|---|---|---|
| Typing | static | dynamic | static | static |
| Integer overflow | wraps silently | never (big integers) | undefined for signed | wraps silently |
| Dynamic array | `ArrayList` | `list` | `vector` | slice |
| Hash map | `HashMap` | `dict` | `unordered_map` | `map` |
| Sorted map | `TreeMap` | none built in | `map` | none built in |
| Heap | `PriorityQueue` (min) | `heapq` (min) | `priority_queue` (max) | `container/heap` |
| Memory | garbage collected | garbage collected | manual / RAII | garbage collected |

## 18. Practice plan

1. Re-type each program from memory; then change the input to break it, and explain why it broke.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Anagram Groups](/problems/anagram-groups), [Kth Largest](/problems/kth-largest) and [Shortest Maze Path](/problems/shortest-maze-path) using only the structures from this lesson, and write the time and space complexity of each on paper.
3. Quiz yourself: for each structure, name the cost of insert, delete, search, min and iteration without looking at the tables.
"""),
], quiz=OLD["quiz"] + [
 Q("In Java, when you pass an object to a method:", ["The method receives a copy of the reference (pass by value)", "The method receives the object itself", "The object is deep-copied", "Nothing is passed"], 0, "The method can mutate the object but cannot rebind the caller's variable."),
 Q("What is the amortised cost of ArrayList.add?", ["O(1)", "O(n)", "O(log n)", "O(n log n)"], 0, "Geometric growth spreads the copying cost over many adds."),
 Q("A Java HashMap resizes when entries exceed:", ["Load factor 0.75 times the capacity", "10 entries", "Half the heap", "Never"], 0, "It doubles the table and rehashes."),
 Q("Which structure should you use to find the k largest of n numbers in O(n log k)?", ["A min-heap of size k", "A sorted array", "A HashSet", "A stack"], 0, "Pop the smallest whenever the heap exceeds k."),
 Q("Why can a HashSet<int[]> not find an equal array?", ["Arrays use identity equality and hashing", "Arrays are too large", "Sets reject arrays", "int[] is not an object"], 0, "Use a List<Integer>, a record or an encoded long."),
])
