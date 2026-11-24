from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "java", code, cap)

JAVA = L("java-for-dsa", "Java for DSA", "free", 30, "", blocks=[
M("""## Why Java for interviews?

Java is a **statically typed, compiled, garbage-collected** language that runs on the JVM. Most large companies use it in production, so interviewers are comfortable reading it, and its standard library (the *Java Collections Framework*) already contains almost every data structure you need: dynamic arrays, hash maps, sorted maps, heaps, deques.

| Strengths | Weaknesses |
|---|---|
| Rich, well-named collections | More typing than Python (boilerplate) |
| Static types catch many bugs before you run | No operator overloading: comparing strings needs `.equals` |
| Predictable performance | Generics cannot hold primitives (boxing cost) |
| Great for object-oriented design questions | Integer overflow wraps silently |

This lesson walks through everything you need to solve DSA problems in Java: the program skeleton, types, strings, arrays, every collection, sorting, node classes and the performance facts to quote in an interview. All programs below are complete: copy one, run it, change it.

## 1. The program skeleton and reading input

Every Java program lives in a class with a `main` method. Practice problems read from standard input; `Scanner` is easy, `BufferedReader` is faster for large input.
"""),
C("""import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(in.readLine());
        int n = Integer.parseInt(st.nextToken());
        int k = Integer.parseInt(st.nextToken());

        st = new StringTokenizer(in.readLine());
        long sum = 0;
        for (int i = 0; i < n; i++) sum += Integer.parseInt(st.nextToken());

        System.out.println("n=" + n + " k=" + k + " sum=" + sum);
    }
}""", "Fast input with BufferedReader and StringTokenizer"),
M("""Use `System.out.println` for output. For many lines, collect into a `StringBuilder` and print once; thousands of `println` calls are slow.

## 2. Types, variables and overflow

| Type | Size | Range / use |
|---|---|---|
| `byte`, `short` | 8, 16 bits | rarely needed |
| `int` | 32 bits | about ±2.1 × 10⁹ (default integer) |
| `long` | 64 bits | about ±9.2 × 10¹⁸; use for sums and products |
| `double` | 64 bits | decimals (not exact) |
| `boolean` | 1 bit logically | `true` / `false` |
| `char` | 16 bits | one UTF-16 character; behaves like a number |

The most common Java bug in interviews is **`int` overflow**: `int` arithmetic wraps around silently. Use `long` when a sum or product can exceed about two billion, and add `L` to literals (`1_000_000_007L`).
"""),
C("""public class Main {
    public static void main(String[] args) {
        int big = Integer.MAX_VALUE;          // 2147483647
        System.out.println(big + 1);          // wraps to -2147483648
        long safe = (long) big + 1;           // cast BEFORE adding
        System.out.println(safe);             // 2147483648

        System.out.println(7 / 2);            // 3  (integer division)
        System.out.println(7 / 2.0);          // 3.5
        System.out.println(-7 % 3);           // -1 (remainder keeps the sign of the dividend)
        System.out.println(Math.floorMod(-7, 3)); // 2 (the "mathematical" modulo)

        final int MOD = 1_000_000_007;
        long x = 123456789L * 987654321L % MOD;
        System.out.println(x);
    }
}""", "Overflow, division and modulo"),
M("""## 3. Control flow and methods

Java has `if/else`, `switch`, `for`, enhanced `for (T x : collection)`, `while`, `do/while`, `break`, `continue` and labelled breaks for nested loops. Methods are declared with a return type; make helpers `static` inside `Main`. Arguments are passed **by value**; for objects the value is a reference, so a method can mutate the object's contents but cannot make the caller's variable point elsewhere.

## 4. Strings

`String` is **immutable**: every change creates a new string. Building a string with `+=` in a loop is O(n²); use `StringBuilder`.

| Task | Code |
|---|---|
| Length, char at index | `s.length()`, `s.charAt(i)` |
| Compare content | `s.equals(t)` (never `==`) |
| Substring `[a, b)` | `s.substring(a, b)` |
| Search | `s.indexOf("ab")`, `s.contains("ab")` |
| Split / join | `s.split(" ")`, `String.join(",", list)` |
| To char array and back | `s.toCharArray()`, `new String(chars)` |
| Digit to number | `c - '0'` |
| Letter to index 0..25 | `c - 'a'` |
"""),
C("""import java.util.*;

public class Main {
    static boolean isPalindrome(String s) {
        int i = 0, j = s.length() - 1;
        while (i < j) {
            if (s.charAt(i++) != s.charAt(j--)) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(isPalindrome("level"));      // true
        StringBuilder sb = new StringBuilder();
        for (char c = 'a'; c <= 'e'; c++) sb.append(c).append('-');
        sb.setLength(sb.length() - 1);                  // drop the last '-'
        System.out.println(sb);                         // a-b-c-d-e
        System.out.println(sb.reverse());               // e-d-c-b-a

        int[] counts = new int[26];                     // letter frequencies
        for (char c : "banana".toCharArray()) counts[c - 'a']++;
        System.out.println(counts['a' - 'a'] + " " + counts['n' - 'a']); // 3 2
    }
}""", "Strings, StringBuilder and a letter-count array"),
M("""## 5. Arrays and lists

An **array** has a fixed length: `int[] a = new int[n];` (zero-filled). Its length is `a.length`. 2-D arrays are arrays of arrays: `int[][] g = new int[r][c];`. The class `java.util.Arrays` helps: `Arrays.sort`, `Arrays.fill`, `Arrays.toString`, `Arrays.copyOfRange`, `Arrays.asList`.

`ArrayList<Integer>` is a **dynamic array**: `add` is amortised O(1), `get(i)` is O(1), inserting or removing in the middle is O(n). Remember that generics hold objects: use `Integer`, not `int` (autoboxing converts, at some cost).
"""),
C("""import java.util.*;

public class Main {
    public static void main(String[] args) {
        int[] a = {5, 2, 9, 1};
        Arrays.sort(a);
        System.out.println(Arrays.toString(a));            // [1, 2, 5, 9]

        int[][] grid = new int[3][4];
        for (int[] row : grid) Arrays.fill(row, -1);
        grid[1][2] = 7;
        System.out.println(Arrays.deepToString(grid));

        List<Integer> list = new ArrayList<>();
        for (int x : a) list.add(x);
        list.remove(Integer.valueOf(5));                    // remove VALUE 5
        list.remove(0);                                     // remove INDEX 0
        System.out.println(list);                           // [2, 9]

        List<int[]> pairs = new ArrayList<>();              // list of small arrays
        pairs.add(new int[]{1, 2});
        System.out.println(pairs.get(0)[1]);                // 2
    }
}""", "Arrays, 2-D arrays and ArrayList (note remove(int) vs remove(Object))"),
M("""## 6. Hash maps and sets

| Structure | Class | Typical cost | Notes |
|---|---|---|---|
| Hash map | `HashMap<K,V>` | O(1) average get/put | no order |
| Sorted map | `TreeMap<K,V>` | O(log n) | keys sorted; `floorKey`, `ceilingKey` |
| Insertion-ordered map | `LinkedHashMap` | O(1) | iteration in insertion order (LRU caches) |
| Hash set | `HashSet<T>` | O(1) average | uniqueness |
| Sorted set | `TreeSet<T>` | O(log n) | `floor`, `ceiling`, `first`, `last` |

Useful methods: `getOrDefault`, `merge`, `putIfAbsent`, `computeIfAbsent`, `containsKey`, `entrySet()`. Keys must have consistent `equals` and `hashCode`; arrays are bad keys (identity hashing), so convert to a `List` or a `String`.
"""),
C("""import java.util.*;

public class Main {
    public static void main(String[] args) {
        // frequency counting
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : "mississippi".toCharArray()) freq.merge(c, 1, Integer::sum);
        System.out.println(freq);                          // {p=2, s=4, i=4, m=1} (order may vary)

        // two-sum with a map: value -> index
        int[] nums = {2, 7, 11, 15};
        int target = 9;
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            Integer j = seen.get(target - nums[i]);
            if (j != null) { System.out.println(j + " " + i); break; }
            seen.put(nums[i], i);
        }

        // grouping with computeIfAbsent
        Map<Integer, List<String>> byLen = new TreeMap<>();
        for (String w : new String[]{"to", "be", "or", "not"})
            byLen.computeIfAbsent(w.length(), k -> new ArrayList<>()).add(w);
        System.out.println(byLen);                          // {2=[to, be, or], 3=[not]}

        TreeSet<Integer> set = new TreeSet<>(List.of(10, 20, 30));
        System.out.println(set.floor(25) + " " + set.ceiling(25)); // 20 30
    }
}""", "Counting, two-sum, grouping and a sorted set"),
M("""## 7. Stack, queue, deque and heap

| Need | Use | Methods |
|---|---|---|
| Stack (LIFO) | `ArrayDeque<T>` | `push`, `pop`, `peek` |
| Queue (FIFO) | `ArrayDeque<T>` or `LinkedList<T>` | `offer`, `poll`, `peek` |
| Deque | `ArrayDeque<T>` | `addFirst`, `addLast`, `pollFirst`, `pollLast` |
| Min-heap | `PriorityQueue<T>` | `offer`, `poll`, `peek` (O(log n), O(log n), O(1)) |
| Max-heap | `new PriorityQueue<>(Comparator.reverseOrder())` | same |

Avoid the legacy `Stack` class; `ArrayDeque` is faster and cleaner. `PriorityQueue` does **not** iterate in sorted order; only `poll` returns the minimum.
"""),
C("""import java.util.*;

public class Main {
    public static void main(String[] args) {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1); stack.push(2);
        System.out.println(stack.pop());                    // 2

        Queue<Integer> q = new ArrayDeque<>();
        q.offer(1); q.offer(2);
        System.out.println(q.poll());                       // 1

        PriorityQueue<int[]> pq = new PriorityQueue<>((x, y) -> x[0] - y[0]); // min by first field
        pq.offer(new int[]{5, 100});
        pq.offer(new int[]{2, 200});
        System.out.println(pq.poll()[1]);                   // 200

        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        maxHeap.addAll(List.of(3, 9, 4));
        System.out.println(maxHeap.peek());                 // 9
    }
}""", "Stack, queue, heaps with comparators"),
M("""## 8. Sorting, comparators and binary search

`Arrays.sort(int[])` uses dual-pivot quicksort (not stable). Sorting objects with `Arrays.sort(T[], comparator)` or `Collections.sort(list, comparator)` uses a **stable** merge-sort variant (TimSort). Build comparators with `Comparator.comparingInt(...).thenComparing(...)`. **Never** write `(a, b) -> a - b` when values can be large or negative: subtraction can overflow. Use `Integer.compare(a, b)`.

`Arrays.binarySearch(a, key)` returns the index if found, otherwise `-(insertionPoint) - 1`. For first/last boundaries write your own `lo/hi` loop (see the Binary Search chapter).
"""),
C("""import java.util.*;

public class Main {
    record Person(String name, int age) {}

    public static void main(String[] args) {
        Integer[] a = {5, 2, 9, 1};
        Arrays.sort(a, Collections.reverseOrder());
        System.out.println(Arrays.toString(a));             // [9, 5, 2, 1]

        List<Person> people = new ArrayList<>(List.of(
            new Person("Asha", 30), new Person("Ben", 25), new Person("Chen", 30)));
        people.sort(Comparator.comparingInt(Person::age).reversed().thenComparing(Person::name));
        System.out.println(people);                         // oldest first, then by name

        int[] sorted = {1, 3, 5, 7, 9};
        System.out.println(Arrays.binarySearch(sorted, 7)); // 3
        System.out.println(Arrays.binarySearch(sorted, 4)); // -3 -> insertion point 2

        int[][] intervals = {{5, 8}, {1, 3}, {2, 6}};
        Arrays.sort(intervals, (x, y) -> Integer.compare(x[0], y[0]));
        System.out.println(Arrays.deepToString(intervals));
    }
}""", "Comparators, records and binary search"),
M("""## 9. Classes, nodes and recursion

Problems often give you a node class. A `static` nested class keeps everything in one file. Java passes object references by value; setting `node = node.next` changes only your local variable.
"""),
C("""public class Main {
    static class ListNode {
        int val; ListNode next;
        ListNode(int v) { val = v; }
    }
    static class TreeNode {
        int val; TreeNode left, right;
        TreeNode(int v) { val = v; }
    }

    static ListNode reverse(ListNode head) {
        ListNode prev = null;
        while (head != null) {
            ListNode nxt = head.next;
            head.next = prev;
            prev = head;
            head = nxt;
        }
        return prev;
    }

    static int height(TreeNode t) {
        return t == null ? 0 : 1 + Math.max(height(t.left), height(t.right));
    }

    public static void main(String[] args) {
        ListNode h = new ListNode(1); h.next = new ListNode(2); h.next.next = new ListNode(3);
        h = reverse(h);
        for (ListNode p = h; p != null; p = p.next) System.out.print(p.val + " "); // 3 2 1
        System.out.println();

        TreeNode root = new TreeNode(1); root.left = new TreeNode(2); root.left.left = new TreeNode(3);
        System.out.println(height(root));                   // 3
    }
}""", "Linked list reversal and tree height"),
M("""## 10. Complexity cheat sheet (Java collections)

| Operation | ArrayList | LinkedList | HashMap/HashSet | TreeMap/TreeSet | ArrayDeque | PriorityQueue |
|---|---|---|---|---|---|---|
| Access by index | O(1) | O(n) | - | - | - | - |
| Add at end | O(1)* | O(1) | O(1)* | O(log n) | O(1) | O(log n) |
| Insert/remove middle | O(n) | O(1) after finding | - | - | - | - |
| Search by value | O(n) | O(n) | O(1)* | O(log n) | O(n) | O(n) |
| Min/max | O(n) | O(n) | O(n) | O(log n) | - | O(1) peek |

\\* amortised / average.

## 11. Pitfalls and idioms checklist

- Compare strings and boxed numbers with `.equals`, not `==` (small `Integer` values are cached, which hides the bug until large values).
- Cast to `long` **before** multiplying: `(long) a * b`, not `(long) (a * b)`.
- `list.remove(int)` removes by index; `list.remove(Object)` by value.
- Do not modify a collection while iterating it with for-each (`ConcurrentModificationException`); use an index loop or an iterator.
- `char + int` is an `int`; cast back with `(char)`.
- `int[]` has no `contains`/`indexOf`; use loops or convert to a list.
- Recursion depth is limited (default stack about 10,000 to 20,000 frames); convert very deep recursion to loops.
- Use `StringBuilder` for repeated appends and `String.valueOf(char[])` to build from arrays.
- `Math.abs(Integer.MIN_VALUE)` is still negative.

## 12. A reusable solution template
"""),
C("""import java.io.*;
import java.util.*;

public class Main {
    static int[] twoSum(int[] a, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            Integer j = seen.get(target - a[i]);
            if (j != null) return new int[]{j + 1, i + 1};
            seen.putIfAbsent(a[i], i);
        }
        return new int[]{-1};
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(in.readLine());
        int n = Integer.parseInt(st.nextToken()), t = Integer.parseInt(st.nextToken());
        int[] a = new int[n];
        st = new StringTokenizer(in.readLine());
        for (int i = 0; i < n; i++) a[i] = Integer.parseInt(st.nextToken());
        int[] r = twoSum(a, t);
        System.out.println(r.length == 1 ? "-1" : r[0] + " " + r[1]);
    }
}""", "Template: parse input, call a pure function, print the answer"),
M("""This is exactly the shape of the practice problem [Pair With Target Sum](/problems/pair-with-target-sum). Keep your logic in a small pure function and keep input and output in `main`; it is easier to test and to explain.

## 13. What to practise next

1. Re-type each program above without looking.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Anagram Groups](/problems/anagram-groups) and [Kth Largest](/problems/kth-largest) in Java using only the structures in this lesson.
3. State the complexity of every structure you use, out loud.
"""),
], quiz=[
 Q("Why must you use `.equals` for strings in Java?", ["`==` compares references, not content", "`==` is slower", "`.equals` is shorter", "Strings are numbers"], 0, "Two equal strings can be different objects."),
 Q("Which class is the recommended stack and queue in modern Java?", ["ArrayDeque", "Stack", "Vector", "Hashtable"], 0, "ArrayDeque is faster and has a cleaner API than Stack."),
 Q("`(a, b) -> a - b` as a comparator is risky because:", ["Subtraction can overflow", "It is too slow", "It cannot compile", "It sorts descending"], 0, "Use Integer.compare(a, b)."),
 Q("A PriorityQueue in Java is by default a:", ["Min-heap", "Max-heap", "Sorted list", "Hash set"], 0, "Pass Comparator.reverseOrder() for a max-heap."),
 Q("To avoid int overflow when multiplying two ints into a long you:", ["Cast one operand to long before multiplying", "Cast the result after multiplying", "Use float", "Do nothing"], 0, "The multiplication itself must happen in long."),
])
