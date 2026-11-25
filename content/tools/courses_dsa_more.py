"""Rearranges the DSA course into the full chapter list and adds the remaining original lessons."""
from courses_a import Q, L
from courses_dsa import DSA, P

old = {l["slug"]: l for _, ls in DSA["sections"] for l in ls}
def keep(*slugs): return [old[s] for s in slugs]
def W(slug, title, minutes, md, quiz, code=None, viz=None, tip=None):
    return L(slug, title, "free", minutes, md, code=code, viz=viz, tip=tip, quiz=quiz)

new = {}
new["lang"] = [
 W("choosing-a-language", "Choosing a language for DSA", 7,
   "## Pick one and know it well\n\nInterviews let you choose a language. **Python** is the shortest to write; **Java** and **C#** are explicit and common in industry; **C++** gives control and a rich standard library; **Go**, **JavaScript** and **TypeScript** are popular for backend and web roles.\n\nWhatever you choose, learn its **built-in tools** cold: dynamic arrays, hash map and set, sorting with a key, a heap, a queue or deque, and string building. Knowing them saves interview minutes.",
   [Q("What matters most when choosing a language for interviews?", ["Knowing it and its standard library well", "Using the newest one", "Using the longest syntax", "Never switching"], 0, "Fluency beats novelty.")]),
 W("input-output-and-templates", "Reading input and printing output", 6,
   "## Practice problems read standard input\n\nOur problems give the input format and expect the answer on standard output. Read all input, split into tokens, convert to numbers, and print exactly the required format (spaces, newlines).",
   [Q("If a problem says \"print two numbers separated by a space\", you should print:", ["Exactly that, nothing extra", "Any format", "Words describing them", "One number per line"], 0, "Output formats are checked exactly.")],
   code=("python", "import sys\n\ndata = sys.stdin.read().split()\nn, k = int(data[0]), int(data[1])\na = list(map(int, data[2:2 + n]))\nprint(sum(a) + k)", "Read all tokens once")),
]
new["warm"] = [
 W("variables-io-operators", "Variables, I/O and operators", 8,
   "## The first building blocks\n\nA **variable** names a value. Arithmetic operators `+ - * / %` work as you expect; `//` or integer division drops the fraction, and `%` gives the remainder. Watch for **integer overflow** in languages with fixed-size integers.\n\nTiny programs to try: swap two numbers, convert seconds to hours-minutes-seconds, compute simple interest."
   + P(("largest-of-three", "Largest of Three")),
   [Q("`17 % 5` equals:", ["2", "3", "12", "5"], 0, "17 = 3×5 + 2.")]),
 W("conditionals-and-decisions", "Conditionals and decisions", 8,
   "## if / else\n\nConditionals choose between paths. Combine tests with `and`, `or`, `not`, order them from most specific to most general, and handle edge cases (zero, negative, equal values) explicitly."
   + P(("even-or-odd", "Even or Odd"), ("leap-year-check", "Leap Year Check")),
   [Q("A leap-year test must check the century rule because:", ["1900 is divisible by 4 but not a leap year", "Years are random", "100 is prime", "It is optional"], 0, "Century years need divisibility by 400.")]),
 W("loops-and-iteration", "Loops and iteration", 8,
   "## Repeating work\n\n`for` loops repeat a known number of times, `while` loops repeat until a condition changes. Typical tasks: count digits, sum digits, reverse a number by peeling off the last digit with `% 10` and dropping it with `// 10`."
   + P(("count-digits", "Count Digits"), ("sum-of-digits", "Sum of Digits"), ("reverse-a-number", "Reverse a Number"), ("palindrome-number", "Palindrome Number")),
   [Q("To drop the last digit of a positive integer n you use:", ["n // 10", "n % 10", "n * 10", "n - 10"], 0, "Integer division by 10.")],
   code=("python", "def digit_sum(n):\n    s = 0\n    while n > 0:\n        s += n % 10\n        n //= 10\n    return s", "Peel digits off")),
 W("number-theory-warmups", "Number theory warm-ups", 12,
   "## Divisors, primes and gcd\n\n- **gcd** by Euclid: `gcd(a, b) = gcd(b, a % b)`; `lcm = a / gcd * b`.\n- **Prime test:** try divisors only up to √n.\n- **Sieve of Eratosthenes:** cross out multiples of each prime to list all primes ≤ n in about O(n log log n).\n- **Binary representation:** repeatedly divide by 2.\n- **Factorial** grows fast: use a modulo when asked."
   + P(("gcd-of-two-numbers", "GCD of Two Numbers"), ("lcm-of-two-numbers", "LCM of Two Numbers"), ("prime-number-check", "Prime Number Check"), ("primes-up-to-n", "Count Primes Up To N"), ("perfect-number-check", "Perfect Number Check"), ("factorial", "Factorial"), ("nth-fibonacci", "Nth Fibonacci Number"), ("decimal-to-binary", "Decimal to Binary"), ("armstrong-number", "Armstrong Number"), ("trailing-zeroes-of-factorial", "Trailing Zeroes of a Factorial")),
   [Q("To test whether n is prime you only need divisors up to:", ["√n", "n/2", "n", "log n"], 0, "A larger factor pairs with a smaller one below √n."),
    Q("Euclid's algorithm computes:", ["The greatest common divisor", "The prime factors", "The square root", "The factorial"], 0, "gcd(a, b) = gcd(b, a mod b).")],
   code=("python", "def gcd(a, b):\n    while b:\n        a, b = b, a % b\n    return a", "Euclid's algorithm")),
 W("array-and-string-warmups", "Array and string warm-ups", 10,
   "## One pass is often enough\n\nSum, count, max, second max, reverse, check sortedness: each is a single scan carrying a little state. For strings: count vowels, check palindromes, count words, toggle case."
   + P(("sum-of-array", "Sum of Array Elements"), ("second-largest-element", "Second Largest Element"), ("count-even-and-odd", "Count Even and Odd Numbers"), ("reverse-an-array", "Reverse an Array"), ("is-array-sorted", "Check if an Array Is Sorted"), ("count-vowels", "Count Vowels"), ("palindrome-string-check", "Palindrome String Check"), ("count-words", "Count Words in a Sentence"), ("toggle-case", "Toggle Case")),
   [Q("Finding the largest element takes:", ["One pass, O(n)", "O(n²)", "O(log n)", "Sorting only"], 0, "Keep the best seen so far.")]),
 W("recursion-warmups", "Recursion warm-ups", 8,
   "## A function that calls itself\n\nEvery recursion needs a **base case** (stop) and a **recursive case** that moves toward it. Sum of first n: `sum(n) = n + sum(n-1)`, `sum(0) = 0`. The call stack grows with depth, so very deep recursion can overflow it."
   + P(("sum-of-first-n-recursive", "Sum of First N Numbers")),
   [Q("Without a base case a recursive function:", ["Never stops (stack overflow)", "Runs once", "Returns 0", "Is faster"], 0, "Nothing ends the calls.")]),
]
new["math"] = [
 W("logarithms-and-modular-arithmetic", "Logarithms and modular arithmetic", 10,
   "## Two tools for big numbers\n\n**log₂ n** is how many times you can halve n: it explains binary search and balanced trees. **Modular arithmetic** keeps numbers small: `(a + b) % m = ((a % m) + (b % m)) % m`, and the same holds for multiplication. Answers \"modulo 1 000 000 007\" avoid overflow in counting problems.",
   [Q("log₂(1024) is:", ["10", "1024", "2", "100"], 0, "2^10 = 1024.")]),
 W("primes-and-the-sieve", "Primes and the sieve", 8,
   "## Listing primes quickly\n\nStart with all numbers marked prime. For each prime p, mark p², p²+p, ... as not prime. What remains is the list of primes. This **sieve of Eratosthenes** handles ranges up to millions in milliseconds.",
   [Q("The sieve marks as composite:", ["Multiples of each prime", "Only even numbers", "Squares only", "Random numbers"], 0, "Every multiple of a prime (after the prime) is composite.")]),
]
new["sort"] = keep("sorting-algorithms") + [
 W("elementary-sorts", "Bubble, selection and insertion sort", 10,
   "## The O(n²) trio\n\n- **Bubble sort** swaps adjacent out-of-order pairs; the largest item floats to the end each pass.\n- **Selection sort** repeatedly selects the minimum of the unsorted part and swaps it into place.\n- **Insertion sort** grows a sorted prefix by inserting each new item where it belongs; excellent on nearly sorted input.\n\nThey are simple and in place; use them to understand sorting, not for large inputs.",
   [Q("Which is fastest on an already sorted array?", ["Insertion sort (about O(n))", "Selection sort", "Bubble sort always", "Merge sort only"], 0, "It does one comparison per element.")],
   viz=("insertion-sort", "8, 3, 5, 1, 9, 2", "")),
 W("quicksort-and-quickselect", "Quick sort and quickselect", 10,
   "## Partition around a pivot\n\n**Quick sort** picks a pivot, moves smaller items left and larger right, then sorts both sides: O(n log n) on average, O(n²) in the worst case (mitigate by random pivots). **Quickselect** uses the same partition to find the k-th smallest element in average O(n) without fully sorting."
   + P(("kth-largest", "Kth Largest")),
   [Q("Quickselect finds the k-th smallest element in average:", ["O(n)", "O(n²)", "O(log n)", "O(n log n)"], 0, "It only recurses into one side.")]),
]
new["bits"] = [
 W("bit-basics-and-xor", "Bit basics and the XOR trick", 9,
   "## Working with binary\n\nBitwise `&` (and), `|` (or), `^` (xor), `~` (not), `<<` and `>>` shifts. Handy facts: `x & 1` tests oddness, `1 << k` is 2^k, `x & (x-1)` clears the lowest set bit.\n\n**XOR** cancels equal pairs (`a ^ a = 0`, `a ^ 0 = a`), so XOR-ing all numbers in a list where each value appears twice except one leaves that one.",
   [Q("`a ^ a` equals:", ["0", "a", "1", "2a"], 0, "XOR of equal values cancels.")]
   , code=("python", "def single_number(nums):\n    r = 0\n    for x in nums:\n        r ^= x\n    return r", "Everything cancels except the single value")),
 W("bit-masks-and-counting", "Bit masks and counting bits", 8,
   "## Subsets as numbers\n\nA number's bits can represent a **set** (bit i set = item i chosen), so `n` items have 2^n masks. Count set bits with `x & (x-1)` in a loop (each iteration removes one). Use masks to enumerate subsets for small n.",
   [Q("How many subsets does a set of 10 items have?", ["1024", "100", "10", "512"], 0, "2^10.")]),
]
new["slide"] = [
 W("sliding-window-fixed-and-variable", "Sliding window: fixed and variable size", 12,
   "## Two kinds of windows\n\n**Fixed size k:** add the entering item, remove the leaving one, update the answer (best sum of k in a row).\n\n**Variable size:** expand `right` until the rule breaks, then shrink `left` until it is valid again; record the best length (longest substring without repeats, smallest subarray with sum ≥ target for positive numbers)."
   + P(("calm-stretch", "Calm Stretch"), ("longest-unique-stretch", "Longest Unique Stretch")),
   [Q("A variable window typically shrinks when:", ["The window becomes invalid", "It is empty", "It reaches k", "Never"], 0, "You restore validity by moving left.")],
   viz=("sliding-window", "2, 1, 5, 1, 3, 2, 8, 1", "3")),
]
new["kadane"] = [
 W("kadanes-algorithm", "Kadane's algorithm", 9,
   "## Maximum subarray sum in O(n)\n\nAt each position, the best subarray **ending here** is either the current value alone or the current value plus the best ending at the previous position. Track the best overall. Variants: circular arrays, maximum product (track min and max), one deletion.",
   [Q("Kadane's algorithm runs in:", ["O(n)", "O(n²)", "O(n log n)", "O(1)"], 0, "One pass with two variables.")],
   code=("python", "def max_subarray(a):\n    best = cur = a[0]\n    for x in a[1:]:\n        cur = max(x, cur + x)\n        best = max(best, cur)\n    return best", "Best sum ending here")),
]
new["matrix"] = [
 W("matrix-traversal", "Matrix traversal and transformation", 9,
   "## Grids are arrays of arrays\n\nTraverse row by row, column by column, by diagonals or in a spiral (keep four boundaries and shrink them). A **transpose** swaps rows and columns; rotating 90° clockwise = transpose then reverse each row. For neighbours use direction arrays `(±1,0)`, `(0,±1)` and check bounds.",
   [Q("Rotate a square matrix 90° clockwise by:", ["Transposing, then reversing each row", "Sorting rows", "Reversing columns only", "Doubling it"], 0, "Two simple steps compose the rotation.")]),
]
new["rec"] = [
 W("recursion-fundamentals", "Recursion fundamentals", 10,
   "## Break it down\n\nSolve a problem by solving smaller copies. Define the **base case**, the **recursive case**, and make sure each call moves toward the base. Examples: power(x, n) by halving the exponent, merging two sorted lists, decoding nested strings.",
   [Q("Computing power(x, n) by halving n takes:", ["O(log n)", "O(n)", "O(n²)", "O(1)"], 0, "The exponent halves each call.")]),
 W("backtracking-subsets-permutations", "Backtracking: subsets and permutations", 12,
   "## Explore, then undo\n\n**Backtracking** builds a solution step by step, abandons a path as soon as it cannot work, and **undoes** the last choice before trying the next. Patterns: subsets (include or exclude each item), permutations (choose any unused item), combination sum (reuse allowed or not). Prune early to save time."
   + P(("subset-target-count", "Subset Target Count")),
   [Q("A set of n distinct items has how many subsets?", ["2^n", "n!", "n²", "n"], 0, "Each item is in or out.")]),
]
new["tree"] = keep("binary-tree-traversals")
new["bst"] = keep("binary-search-trees")
new["heap"] = [
 W("heaps-and-priority-queues", "Heaps and priority queues", 11,
   "## Always the smallest (or largest) first\n\nA **binary heap** is a complete tree stored in an array where each parent is ≤ its children (min-heap). Push and pop cost O(log n), peek O(1). Uses: **top-k** (keep a heap of size k), merging k sorted lists, scheduling, Dijkstra's algorithm, running median with two heaps."
   + P(("kth-largest", "Kth Largest"), ("cheapest-route", "Cheapest Route")),
   [Q("Popping the minimum from a binary heap costs:", ["O(log n)", "O(1)", "O(n)", "O(n²)"], 0, "The last item moves to the root and sifts down.")]),
]
new["interval"] = [
 W("merging-intervals", "Merging and scheduling intervals", 9,
   "## Sort first\n\nSort intervals by start. Walk through them, comparing each with the last merged interval: if it starts at or before that interval's end, extend it; otherwise start a new one. Variants: insert an interval, count meeting rooms (sweep line or a min-heap of end times), remove the fewest intervals to avoid overlap."
   + P(("merge-meeting-slots", "Merge Meeting Slots"), ("most-meetings", "Most Meetings")),
   [Q("Before merging intervals you should:", ["Sort them by start", "Shuffle them", "Reverse them", "Remove the first"], 0, "Overlaps then appear next to each other.")]),
]
new["trie"] = [
 W("trie-basics", "Tries (prefix trees)", 10,
   "## Fast prefix lookups\n\nA **trie** stores words character by character along tree edges; a flag marks where a word ends. Insert, search and prefix-search cost O(length of the word), independent of how many words are stored. Uses: autocomplete, spell checking, word search on boards.",
   [Q("Searching a word of length L in a trie costs:", ["O(L)", "O(number of words)", "O(L²)", "O(1) always"], 0, "You follow one edge per character.")]),
]
new["design"] = [
 W("lru-cache-design", "Design: an LRU cache", 10,
   "## Hash map plus linked list\n\nAn **LRU cache** evicts the least recently used entry when full. Combine a **hash map** (key to node, O(1) lookup) with a **doubly linked list** ordered by recency (move to front on use, evict from the back). Both `get` and `put` run in O(1). Many design problems are a clever pairing of two simple structures.",
   [Q("Why a doubly linked list in an LRU cache?", ["To remove and move nodes in O(1)", "To sort keys", "To hash keys", "To save memory"], 0, "Given a node, unlinking it needs no scan.")]),
]
new["graph"] = keep("bfs-and-dfs", "topological-sort-and-shortest-paths") + [
 W("union-find", "Union-find (disjoint sets)", 10,
   "## Who is connected to whom?\n\n**Union-find** keeps disjoint sets with two operations: `find(x)` returns the set representative, `union(a, b)` merges two sets. With **path compression** and **union by size** both are almost O(1). Uses: connected components, detecting a redundant edge, Kruskal's minimum spanning tree."
   + P(("friend-circles", "Friend Circles")),
   [Q("Union-find answers quickly:", ["Are these two nodes in the same set?", "What is the shortest path?", "Is the array sorted?", "What is the median?"], 0, "find(a) == find(b).")]),
]
new["dp"] = keep("dp-first-steps") + [
 W("knapsack-and-subsequences", "Knapsack and subsequence DP", 12,
   "## Classic families\n\n- **0/1 knapsack:** each item once; `dp[w]` = best value with capacity w (iterate w downward).\n- **Unbounded knapsack / coin change:** items reusable.\n- **LIS:** longest increasing subsequence, O(n²) or O(n log n).\n- **LCS / edit distance:** two-string DP tables.\n\nIn each, name the state, write the transition, and decide the fill order."
   + P(("fewest-coins", "Fewest Coins"), ("longest-common-run", "Longest Common Run"), ("edit-distance", "Edit Distance")),
   [Q("In 0/1 knapsack each item can be used:", ["At most once", "Unlimited times", "Twice", "Never"], 0, "That distinguishes it from unbounded knapsack.")]),
]
new["adv"] = [
 W("string-matching-kmp", "String matching and KMP", 10,
   "## Finding a pattern in text\n\nThe naive method compares at every start position: O(n·m). **KMP** precomputes, for the pattern, the longest proper prefix that is also a suffix at each position, so after a mismatch it never re-reads text characters: O(n + m). **Rabin-Karp** compares rolling hashes instead.",
   [Q("KMP runs in:", ["O(n + m)", "O(n·m)", "O(n²)", "O(log n)"], 0, "Text is scanned once.")]),
 W("segment-trees-and-fenwick", "Segment trees and Fenwick trees", 10,
   "## Range queries with updates\n\nPrefix sums answer range sums but break when values change. A **Fenwick tree** (binary indexed tree) and a **segment tree** both support point updates and range queries in O(log n). Segment trees generalise to min, max and (with lazy propagation) range updates.",
   [Q("Updating one value and querying a range sum in O(log n) each is the job of:", ["A Fenwick tree", "A plain array", "A stack", "A queue"], 0, "Both operations walk O(log n) nodes.")]),
]

import courses_dsa_welcome as WL
import courses_dsa_warmup as WARM
import lang_java2 as lang_java, lang_python2 as lang_python, lang_cpp2 as lang_cpp, lang_csharp2 as lang_csharp, lang_go2 as lang_go, lang_js2 as lang_js, lang_ts2 as lang_ts
class LANG:
    JAVA, PYTHON, CPP = lang_java.JAVA2, lang_python.PYTHON2, lang_cpp.CPP2
    CSHARP, GO, JS, TS = lang_csharp.CSHARP2, lang_go.GO2, lang_js.JS2, lang_ts.TS2

layout = [
 ("Welcome", keep("course-introduction", "course-roadmap")),
 ("Coding Interview Strategy", keep("how-coding-interviews-work", "communicating-while-you-solve", "recognising-the-pattern")),
 ("Programming Fundamentals", [LANG.JAVA, LANG.PYTHON, LANG.CPP, LANG.CSHARP, LANG.GO, LANG.JS, LANG.TS]),
 ("DSA Warmup", WARM.WARMUP),
 ("Big O Notation", keep("big-o-in-plain-words", "best-worst-and-space")),
 ("Math Fundamentals", new["math"]),
 ("Sorting Algorithms", new["sort"]),
 ("Arrays", keep("arrays-and-hash-maps", "finding-and-counting")),
 ("Strings", keep("string-basics", "anagrams-and-frequency")),
 ("Bit Manipulation", new["bits"]),
 ("Hash Tables", keep("hash-maps-in-practice")),
 ("Two Pointers", keep("two-pointers-and-windows", "two-pointers-on-sorted-input")),
 ("Sliding Window", new["slide"]),
 ("Prefix Sum", keep("prefix-sums")),
 ("Kadane's Algorithm", new["kadane"]),
 ("Binary Search", keep("binary-search-mastery")),
 ("Matrix", new["matrix"]),
 ("Linked List", keep("linked-list-basics", "fast-and-slow-pointers")),
 ("Stacks", keep("stacks-and-brackets", "monotonic-stack")),
 ("Queues", keep("queues-and-bfs-queue")),
 ("Recursion and Backtracking", new["rec"]),
 ("Binary Tree", new["tree"]),
 ("BST", new["bst"]),
 ("Heaps", new["heap"]),
 ("Intervals", new["interval"]),
 ("Tries", new["trie"]),
 ("Data Structure Design", new["design"]),
 ("Greedy", keep("greedy-and-intervals")),
 ("Graphs", new["graph"]),
 ("Dynamic Programming", new["dp"]),
 ("Advanced Topics", new["adv"]),
]
used = {l["slug"] for _, ls in layout for l in ls}
assert set(old) <= used, set(old) - used
_override = {l["slug"]: l for l in (WL.INTRO, WL.ROADMAP, WL.HOW, WL.COMM, WL.PATTERN)}
layout = [(t, [_override.get(l["slug"], l) for l in ls]) for t, ls in layout]
DSA_FULL = {**DSA, "sections": [{"title": t, "lessons": ls} for t, ls in layout]}
