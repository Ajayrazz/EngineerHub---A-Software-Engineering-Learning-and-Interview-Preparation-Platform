"""Original EngineerHub course: Data Structures and Algorithms, organised as chapters (sections).
Every lesson is written for this project; practice links point to our own problem bank (/problems/<slug>)."""
from courses_a import Q, L, C

def P(*items):  # "Practice" line: (slug, title) pairs -> markdown links into our own bank
    return "\n\n### Practice\n" + "\n".join(f"- [{t}](/problems/{s})" for s, t in items)

DSA = C("data-structures-and-algorithms", "Data Structures and Algorithms",
  "Build the toolkit for coding interviews, chapter by chapter: strategy, complexity, arrays, strings, hashing, pointers and windows, searching, stacks, linked lists, trees, graphs and dynamic programming, with visual walkthroughs and practice problems.",
  "arrays", "beginner", "free", 900, [
 ("Welcome", [
  L("course-introduction", "Course introduction", "free", 5,
    "## How this course works\n\nThis course teaches the data structures and algorithms that coding interviews keep returning to. It is organised in **chapters** (the list on the left). Each chapter has short lessons with examples, a quiz, and links to practice problems in our problem bank.\n\n### What you will find\n- **Concept lessons** written in plain language, with code.\n- **Step-by-step visualizations** you can play, scrub and run on your own input.\n- **Chapter quizzes** with explanations after you answer.\n- **Practice problems** with hints, graded in stages.\n\nMark lessons complete as you go; your progress bar at the top of the sidebar updates.",
    tip="New to DSA? Follow the chapters in order. Short on time? Use the roadmap lesson to pick a path.",
    quiz=[Q("Where do you find the practice problems for a chapter?", ["In the problem bank, linked from the lesson", "Only by email", "They do not exist", "Only on a phone app"], 0, "Each lesson links to related problems in the problem bank.")]),
  L("course-roadmap", "Course roadmap", "free", 6,
    "## Pick a path\n\nChoose a plan by how much time you have.\n\n| Time available | Suggested path |\n|---|---|\n| 2 weeks | Strategy, Big O, Arrays, Hash tables, Two pointers, Binary search |\n| 1 month | Add Sliding window, Prefix sums, Stacks, Linked lists |\n| 2-3 months | Add Trees, Graphs, Dynamic programming, Greedy |\n\nWhatever the plan, solve problems **without peeking first**, then compare with the explanation. Revisit the ones you missed after a few days.",
    quiz=[Q("What is the best way to use a practice problem?", ["Try it first, then compare with the explanation", "Read the answer first", "Skip hard ones forever", "Copy code without reading"], 0, "Struggling first is what builds recall.")]),
 ]),
 ("Coding Interview Strategy", [
  L("how-coding-interviews-work", "How coding interviews work", "free", 7,
    "## The shape of a round\n\nA typical coding round lasts 45-60 minutes: a few minutes of introductions, **one or two problems**, and time for your questions. The interviewer evaluates how you **understand** the problem, **plan** a solution, **code** it cleanly, **test** it, and **communicate** throughout.\n\nA reliable routine:\n1. Restate the problem and ask clarifying questions.\n2. Work through a small example by hand.\n3. Propose a simple solution, then improve it.\n4. State time and space complexity.\n5. Code it, then test with normal and edge cases.",
    quiz=[Q("What should you do right after hearing the problem?", ["Clarify and restate it, then try an example", "Start typing code immediately", "Ask for the solution", "Stay silent"], 0, "Understanding first prevents solving the wrong problem.")]),
  L("communicating-while-you-solve", "Communicating while you solve", "free", 6,
    "## Think out loud\n\nInterviewers cannot read your mind. Narrate your reasoning: what you are considering, why you reject an option, what you will try next. If you are stuck, say what you have tried and what you are weighing.\n\nUse **small examples** on the whiteboard or editor, name variables clearly, and ask for confirmation before a big decision (\"I will use a hash map for lookups; is extra memory fine?\").",
    quiz=[Q("If you are stuck you should:", ["Explain what you tried and what you are weighing", "Say nothing", "Guess randomly and type fast", "Give up"], 0, "Visible reasoning lets the interviewer help and still credit you.")]),
  L("recognising-the-pattern", "Recognising the pattern", "free", 8,
    "## From problem to technique\n\nMost interview problems are variations of a small set of **patterns**. Clues help you pick one:\n\n| Clue in the problem | Likely pattern |\n|---|---|\n| sorted array, find a pair | two pointers |\n| longest/shortest contiguous part with a rule | sliding window |\n| many range sums | prefix sums |\n| \"minimum value that works\" | binary search on the answer |\n| matching brackets, next greater item | stack |\n| shortest path, fewest steps | BFS |\n| count ways, best choice among overlapping subproblems | dynamic programming |\n\nThe chapters that follow teach each pattern with problems to practise on.",
    quiz=[Q("\"Fewest moves in an unweighted maze\" most suggests:", ["BFS", "Binary search", "Sorting", "Prefix sums"], 0, "BFS explores in order of distance.")]),
 ]),
 ("Big O Notation", [
  L("big-o-in-plain-words", "Big-O in plain words", "free", 12,
    "## Why we measure growth\n\nTwo programs can both be correct and still behave very differently when the input grows. **Big-O notation** describes how the work an algorithm does *grows* with the input size `n`, ignoring constant factors.\n\n| Growth | Name | Doubling `n` makes the work... |\n|---|---|---|\n| O(1) | constant | the same |\n| O(log n) | logarithmic | one step longer |\n| O(n) | linear | twice as long |\n| O(n log n) | linearithmic | slightly more than twice |\n| O(n²) | quadratic | four times as long |\n\nWe usually talk about the **worst case** and drop everything but the fastest-growing term: `3n² + 10n + 7` is just O(n²).",
    code=("python", "def has_duplicate(items):\n    # O(n^2): compares every pair\n    for i in range(len(items)):\n        for j in range(i + 1, len(items)):\n            if items[i] == items[j]:\n                return True\n    return False\n\ndef has_duplicate_fast(items):\n    # O(n): one pass, remembering what we have seen\n    seen = set()\n    for x in items:\n        if x in seen:\n            return True\n        seen.add(x)\n    return False", "Same answer, very different growth"),
    tip="Memory counts too. The fast version trades O(n) extra space for speed.",
    viz=("bubble-sort", "5, 2, 9, 1, 7, 3", ""),
    quiz=[Q("A loop runs once over `n` items doing constant work each time. Its complexity is:", ["O(1)", "O(n)", "O(n²)", "O(log n)"], 1, "One pass over n items is linear."),
          Q("Doubling the input makes an O(n²) algorithm take roughly how much longer?", ["The same time", "Twice as long", "Four times as long", "Eight times as long"], 2, "(2n)² = 4n²."),
          Q("Big-O ignores constant factors, so 5n and n are both O(n).", ["True", "False"], 0, "Only the growth rate matters.", "true_false")]),
  L("best-worst-and-space", "Best, worst and average case; space", "free", 8,
    "## Cases and memory\n\nThe **best case** is the luckiest input, the **worst case** the unluckiest, and the **average case** a typical one. Linear search finds its target immediately in the best case (O(1)) and scans everything in the worst case (O(n)). Interviews usually mean the worst case unless stated.\n\n**Space complexity** counts the extra memory you use besides the input. A hash set of `n` items is O(n) space; swapping in place is O(1). Recursion uses stack space proportional to its depth.",
    quiz=[Q("Linear search's worst case is when the target is:", ["Last or missing", "First", "In the middle always", "Never"], 0, "Every element must be checked."),
          Q("Extra memory used by an algorithm, apart from the input, is its:", ["Space complexity", "Time complexity", "Cache", "Hash"], 0, "It is measured with the same Big-O notation.")]),
 ]),
 ("Arrays", [
  L("arrays-and-hash-maps", "Arrays and in-place updates", "free", 12,
    "## The workhorse structure\n\nAn **array** stores items in consecutive memory, so reading `a[i]` is O(1). Appending at the end is amortised O(1); inserting or deleting in the middle is O(n) because later items shift.\n\nA frequent technique is the **write index**: scan with one index and keep another where the next kept value goes. It filters or compacts an array in place with O(1) extra space.",
    code=("python", "def move_zeroes_to_end(a):\n    w = 0                      # where the next non-zero goes\n    for x in a:\n        if x != 0:\n            a[w] = x\n            w += 1\n    for i in range(w, len(a)):\n        a[i] = 0\n    return a", "Compact an array in place"),
    quiz=[Q("Which operation is O(n) on an array?", ["Inserting at the front", "Reading a[i]", "Reading the length", "Overwriting a[i]"], 0, "Every existing item has to shift one place."),
          Q("A write index lets you filter an array:", ["In place with O(1) extra space", "Only with a second array", "Only by sorting", "Never"], 0, "You overwrite as you scan.")]),
  L("finding-and-counting", "Finding and counting in arrays", "free", 9,
    "## Common array questions\n\n- **Max / min / second max:** one pass keeping the best values so far.\n- **Majority element:** the voting idea keeps a candidate and a counter in O(1) space.\n- **Missing number in 0..n:** the sum formula `n(n+1)/2` minus the actual sum.\n- **Reverse in place:** swap from both ends moving inward.\n\nMost of these are a single scan; think about what **state** you need to carry from one element to the next."
    + P(("kth-largest", "Kth Largest")),
    code=("python", "def missing_number(nums):          # values are 0..n with one missing\n    n = len(nums)\n    return n * (n + 1) // 2 - sum(nums)", "Arithmetic instead of a set"),
    quiz=[Q("Missing number from 0..n via the sum formula runs in:", ["O(n) time, O(1) space", "O(n²)", "O(log n)", "O(n) space"], 0, "One pass to sum, no extra structure.")]),
 ]),
 ("Strings", [
  L("string-basics", "String basics", "free", 9,
    "## Strings are sequences\n\nA string is a sequence of characters. In most languages strings are **immutable**: building one by repeated `+=` in a loop copies each time and can be O(n²); collect pieces in a list and join once.\n\nUseful moves: iterate by index, compare characters from both ends (palindromes), count characters with an array of 26 or a map, and slice carefully (check off-by-one at the boundaries).",
    code=("python", "def is_palindrome(s):\n    i, j = 0, len(s) - 1\n    while i < j:\n        if s[i] != s[j]:\n            return False\n        i, j = i + 1, j - 1\n    return True", "Two indexes from the ends"),
    quiz=[Q("Why is `s += ch` in a long loop often slow?", ["Strings are immutable, so each step copies", "Python forbids it", "It deletes characters", "It sorts"], 0, "Collect in a list and join once.")]),
  L("anagrams-and-frequency", "Anagrams and frequency counts", "free", 9,
    "## Same letters, different order\n\nTwo words are anagrams if they use the same letters the same number of times. Compare **letter counts** (an array of 26 integers, or a hash map) or compare the sorted letters.\n\nThe counting version is O(n); sorting is O(n log n). Frequency counting is one of the most reusable ideas in the course."
    + P(("anagram-groups", "Anagram Groups"), ("longest-unique-stretch", "Longest Unique Stretch")),
    code=("python", "from collections import Counter\n\ndef is_anagram(a, b):\n    return Counter(a) == Counter(b)", "Compare letter counts"),
    quiz=[Q("Counting letters to test anagrams takes:", ["O(n)", "O(n²)", "O(log n)", "O(1) always"], 0, "One pass over each string.")]),
 ]),
 ("Hash Tables", [
  L("hash-maps-in-practice", "Hash maps in practice", "free", 12,
    "## Constant-time lookups\n\nA **hash map** stores key to value pairs. A hash function turns a key into a bucket number, so insert, lookup and delete are **average O(1)**. Collisions (two keys in one bucket) are handled by chaining or probing.\n\n### The classic trade\nWhenever a nested loop asks *\"have I seen a matching value?\"*, a hash map usually turns it into a single pass at the cost of O(n) memory."
    + P(("pair-with-target-sum", "Pair With Target Sum"), ("subarrays-with-sum", "Subarrays With Sum")),
    code=("python", "def first_pair_with_sum(nums, target):\n    seen = {}                      # value -> index\n    for j, x in enumerate(nums):\n        if target - x in seen:\n            return seen[target - x], j\n        seen.setdefault(x, j)\n    return None", "Pair with a target sum in O(n)"),
    quiz=[Q("What is the average cost of looking up a key in a hash map?", ["O(1)", "O(log n)", "O(n)", "O(n log n)"], 0, "A good hash spreads keys across buckets."),
          Q("A hash map trades:", ["Memory for speed", "Speed for memory only", "Correctness for speed", "Nothing"], 0, "O(n) extra space buys O(1) lookups.")]),
 ]),
 ("Two Pointers", [
  L("two-pointers-and-windows", "Two pointers and sliding windows", "free", 16,
    "## Moving two indexes instead of looping twice\n\nMany array and string problems become linear when you keep **two indexes** and move them intelligently.\n\n- **Opposite ends:** start at both ends of a sorted array and walk inward (pair sums, palindromes, container problems).\n- **Sliding window:** keep a window `[left, right]`; grow `right`, and shrink `left` whenever the window breaks a rule.\n\nIt is O(n) because each index only moves forward, so together they make at most `2n` moves."
    + P(("trapped-rainwater", "Trapped Rainwater"), ("calm-stretch", "Calm Stretch"), ("longest-unique-stretch", "Longest Unique Stretch")),
    code=("python", "def longest_unique(s):\n    last, left, best = {}, 0, 0\n    for right, ch in enumerate(s):\n        if ch in last and last[ch] >= left:\n            left = last[ch] + 1          # jump past the repeat\n        last[ch] = right\n        best = max(best, right - left + 1)\n    return best", "Sliding window with a last-seen map"),
    viz=("sliding-window", "2, 1, 5, 1, 3, 2, 8, 1", "3"),
    quiz=[Q("Why is a sliding window O(n) despite nested-looking code?", ["Each pointer only moves forward", "It uses recursion", "It sorts first", "It skips items"], 0, "At most 2n moves in total."),
          Q("Two pointers from opposite ends need the array to be:", ["Sorted or monotone", "Of even length", "Free of duplicates", "Positive only"], 0, "The ordering decides which pointer to move.")]),
  L("two-pointers-on-sorted-input", "Two pointers on sorted input", "free", 8,
    "## Pair sums in a sorted array\n\nWith the array sorted, put `lo` at the start and `hi` at the end. If `a[lo] + a[hi]` is too small, move `lo` right (the sum can only grow); if too big, move `hi` left. Either you find the pair or the pointers meet. O(n) time, O(1) space.",
    viz=("two-pointers", "1, 3, 4, 6, 8, 11, 15", "14"),
    quiz=[Q("If the current sum is too small, you move:", ["The left pointer right", "The right pointer left", "Both inward", "Neither"], 0, "A larger left value raises the sum.")]),
 ]),
 ("Prefix Sums", [
  L("prefix-sums", "Prefix sums", "free", 10,
    "## Answer range sums instantly\n\nLet `P[i]` be the sum of the first `i` numbers. Then the sum of the subarray `a[l..r]` is `P[r+1] - P[l]`: constant time after an O(n) build.\n\nCombine with a **hash map** of earlier prefix values to count subarrays with a given sum, even with negative numbers: for each position, how many earlier prefixes equal `P - k`?"
    + P(("subarrays-with-sum", "Subarrays With Sum")),
    code=("python", "from itertools import accumulate\n\na = [3, 1, 4, 1, 5]\nP = [0] + list(accumulate(a))     # P[i] = sum of first i items\nrange_sum = lambda l, r: P[r + 1] - P[l]\nprint(range_sum(1, 3))            # 1 + 4 + 1 = 6", "Build once, query in O(1)"),
    quiz=[Q("With prefix sums, a range sum query costs:", ["O(1)", "O(n)", "O(log n)", "O(n²)"], 0, "Two lookups and a subtraction.")]),
 ]),
 ("Binary Search", [
  L("binary-search-mastery", "Binary search, and binary search on the answer", "free", 18,
    "## Halving the search space\n\nBinary search finds a target in a **sorted** array in O(log n) by repeatedly checking the middle and discarding the half that cannot contain it.\n\nThe same idea works whenever you can answer a yes/no question that is **monotone**: *no, no, no, yes, yes, yes*. Then you can search for the first `yes` even when there is no array (for example the smallest truck capacity that finishes in `d` days).\n\n### Off-by-one safety\nPick one convention and stick to it. Below, the answer lives in `[lo, hi)`."
    + P(("rotated-lookup", "Rotated Lookup"), ("minimum-ship-capacity", "Minimum Ship Capacity")),
    code=("python", "def first_true(lo, hi, ok):\n    # smallest x in [lo, hi) with ok(x) True, assuming ok is monotone; returns hi if none\n    while lo < hi:\n        mid = (lo + hi) // 2\n        if ok(mid):\n            hi = mid\n        else:\n            lo = mid + 1\n    return lo", "A reusable boundary search"),
    viz=("binary-search", "2, 5, 8, 12, 16, 23, 38, 56, 72, 91", "23"),
    quiz=[Q("About how many comparisons for 1,000,000 sorted items?", ["About 20", "About 1,000", "About 500,000", "1,000,000"], 0, "log2(1,000,000) ≈ 20."),
          Q("Binary search on the answer needs the yes/no check to be monotone.", ["True", "False"], 0, "Without monotonicity, discarding half is unjustified.", "true_false")]),
 ]),
 ("Sorting", [
  L("sorting-algorithms", "Sorting algorithms", "free", 14,
    "## Putting things in order\n\n| Algorithm | Idea | Time (typical) |\n|---|---|---|\n| Bubble / selection / insertion | simple comparisons and swaps | O(n²) |\n| Merge sort | split, sort halves, merge | O(n log n) |\n| Quick sort | partition around a pivot | O(n log n) average |\n| Counting sort | count occurrences of small integers | O(n + k) |\n\n**Insertion sort** is fast on nearly sorted data. **Merge sort** is stable and predictable. In practice use your language's built-in sort (O(n log n)) and focus on *what to sort by*.",
    viz=("merge-sort", "38, 27, 43, 3, 9, 82, 10", ""),
    quiz=[Q("Merge sort runs in:", ["O(n log n)", "O(n²)", "O(n)", "O(log n)"], 0, "log n levels of linear merging."),
          Q("A stable sort keeps equal items in their original order.", ["True", "False"], 0, "That is the definition of stability.", "true_false")]),
 ]),
 ("Stacks and Queues", [
  L("stacks-and-brackets", "Stacks and bracket matching", "free", 10,
    "## Last in, first out\n\nA **stack** supports push and pop at one end in O(1). The most recent unmatched opener must be closed first, which is exactly what bracket matching needs: push openers, and on a closer check that the top is its partner. At the end the stack must be empty."
    + P(("balanced-brackets", "Balanced Brackets")),
    viz=("valid-parentheses", "{[()]}(", ""),
    quiz=[Q("Which structure gives you the most recent item first?", ["Stack", "Queue", "Array only", "Set"], 0, "LIFO.")]),
  L("monotonic-stack", "Monotonic stack", "free", 10,
    "## Next greater item in O(n)\n\nKeep a stack of items whose answer is not known yet, in decreasing order. When a bigger value arrives, pop everything smaller; each popped item has just found its answer. Every item is pushed and popped once, so the whole scan is O(n)."
    + P(("next-warmer-day", "Next Warmer Day"), ("largest-skyline-rectangle", "Largest Skyline Rectangle")),
    code=("python", "def next_greater(a):\n    res, st = [-1] * len(a), []\n    for i, x in enumerate(a):\n        while st and a[st[-1]] < x:\n            res[st.pop()] = x\n        st.append(i)\n    return res", "Next greater element"),
    quiz=[Q("Why is a monotonic stack scan O(n)?", ["Each item is pushed and popped at most once", "It sorts the data", "It uses hashing", "It skips half"], 0, "Amortised constant work per element.")]),
  L("queues-and-bfs-queue", "Queues", "free", 7,
    "## First in, first out\n\nA **queue** removes the oldest item first. It models waiting lines and is the engine of breadth-first search. A **deque** supports both ends in O(1) and powers sliding window maximum with a monotonic queue.",
    quiz=[Q("A queue is:", ["First in, first out", "Last in, first out", "Sorted", "Hashed"], 0, "The oldest item leaves first.")]),
 ]),
 ("Linked Lists", [
  L("linked-list-basics", "Linked list basics", "free", 10,
    "## Nodes and pointers\n\nA **linked list** stores items in nodes that point to the next node. Insert or delete after a known node is O(1); reaching the i-th node is O(i). Use a **dummy head** node to avoid special cases when the head changes.\n\nReversing a list in place: walk through, pointing each node back at the previous one, keeping `prev`, `cur` and `next` pointers.",
    code=("python", "def reverse(head):\n    prev = None\n    while head:\n        nxt = head.next\n        head.next = prev\n        prev, head = head, nxt\n    return prev", "Reverse in place"),
    quiz=[Q("Why use a dummy head node?", ["To avoid special cases when the head changes", "To make the list faster", "To store the length", "To sort it"], 0, "Operations on the head then look like any other node.")]),
  L("fast-and-slow-pointers", "Fast and slow pointers", "free", 8,
    "## Two speeds\n\nMove `slow` one step and `fast` two steps. When `fast` reaches the end, `slow` is at the **middle**. If the list has a **cycle**, `fast` eventually catches `slow` inside it. O(n) time, O(1) space.",
    quiz=[Q("When fast (2 steps) reaches the end, slow is at:", ["The middle", "The start", "The end", "Nowhere"], 0, "It travelled half the distance.")]),
 ]),
 ("Trees", [
  L("binary-tree-traversals", "Binary tree traversals", "free", 12,
    "## Visiting every node\n\n- **Pre-order:** node, left, right (copy a tree).\n- **In-order:** left, node, right (a binary *search* tree comes out sorted).\n- **Post-order:** left, right, node (compute from children).\n- **Level-order:** row by row using a queue (BFS).\n\nMost tree problems are recursion: solve for the left subtree, solve for the right, combine. The height of a tree with `n` nodes is between log n and n.",
    code=("python", "def height(node):\n    if node is None:\n        return 0\n    return 1 + max(height(node.left), height(node.right))", "Post-order recursion"),
    quiz=[Q("In-order traversal of a binary search tree yields:", ["Sorted values", "Reverse order", "Random order", "Level by level"], 0, "Left, node, right respects the ordering."),
          Q("Level-order traversal uses a:", ["Queue", "Stack", "Heap only", "Hash map"], 0, "BFS processes by distance.")]),
  L("binary-search-trees", "Binary search trees", "free", 8,
    "## Ordered structure\n\nIn a **BST** every value in the left subtree is smaller than the node and every value in the right subtree is larger. Search, insert and delete are O(h), where h is the height: O(log n) when balanced, O(n) in the worst case. Self-balancing variants (AVL, red-black) keep h near log n.",
    quiz=[Q("BST search cost is:", ["O(height)", "Always O(1)", "Always O(n)", "O(n²)"], 0, "You follow one root-to-leaf path.")]),
 ]),
 ("Graphs", [
  L("bfs-and-dfs", "Graphs: BFS and DFS", "free", 16,
    "## Nodes and edges\n\nA **graph** is a set of nodes connected by edges, usually stored as an **adjacency list** (node to neighbours). Two traversals cover most problems:\n\n- **BFS** (queue): visits nodes in order of distance, so it finds the **shortest path in an unweighted graph**.\n- **DFS** (stack or recursion): goes deep first; good for connected components, cycle detection, flood fill.\n\nAlways mark nodes **visited** to avoid loops. Both run in O(V + E)."
    + P(("shortest-maze-path", "Shortest Maze Path"), ("island-counter", "Island Counter"), ("friend-circles", "Friend Circles")),
    viz=("bfs", "0-1, 0-2, 1-3, 2-3, 3-4, 2-5", "0"),
    quiz=[Q("BFS finds the shortest path in a graph that is:", ["Unweighted", "Weighted with negatives", "Always a tree only", "Directed only"], 0, "Equal edge costs mean fewest edges is shortest."),
          Q("Graph traversal needs a visited set to:", ["Avoid infinite loops", "Sort nodes", "Save disk", "Encrypt edges"], 0, "Cycles would otherwise repeat forever.")]),
  L("topological-sort-and-shortest-paths", "Topological sort and shortest paths", "free", 10,
    "## Ordering and weights\n\n**Topological sort** orders a directed acyclic graph so every edge goes forward (course prerequisites). Repeatedly take nodes with no unmet prerequisite (in-degree 0); if some remain, there is a cycle.\n\n**Dijkstra's algorithm** finds shortest paths with **non-negative** weights using a priority queue keyed by distance."
    + P(("course-order", "Course Order"), ("cheapest-route", "Cheapest Route")),
    quiz=[Q("Dijkstra requires edge weights to be:", ["Non-negative", "Negative", "Equal only", "Integers only"], 0, "A negative edge can invalidate finalised distances.")]),
 ]),
 ("Dynamic Programming", [
  L("dp-first-steps", "Dynamic programming: first steps", "free", 16,
    "## Reuse subproblem answers\n\nDynamic programming solves a problem by combining answers to **overlapping subproblems**, each solved once. The recipe: define the **state** (what you need to know), write the **recurrence** (how a state depends on smaller ones), set **base cases**, and choose the **order**.\n\nFibonacci shows the idea: naive recursion repeats work exponentially; storing each result makes it O(n). Climbing stairs, coin change and edit distance are classic next steps."
    + P(("staircase-ways", "Staircase Ways"), ("fewest-coins", "Fewest Coins"), ("longest-common-run", "Longest Common Run"), ("edit-distance", "Edit Distance")),
    viz=("fibonacci-dp", "10", ""),
    quiz=[Q("DP is most useful when a problem has:", ["Overlapping subproblems", "No structure", "Only sorted input", "A single step"], 0, "Reusing answers avoids repeated work."),
          Q("A DP recurrence expresses a state in terms of:", ["Smaller states", "Random values", "The future only", "The input size"], 0, "That dependency order defines the fill order.")]),
 ]),
 ("Greedy and Intervals", [
  L("greedy-and-intervals", "Greedy choices and intervals", "free", 10,
    "## Make the locally best choice\n\nA **greedy** algorithm commits to the best option at each step and never reconsiders. It works when a local choice can be proven safe. Classic example: to fit the most non-overlapping meetings, always take the one that **ends earliest**.\n\nFor **intervals**: sort by start, then merge by comparing each interval with the last merged one."
    + P(("most-meetings", "Most Meetings"), ("merge-meeting-slots", "Merge Meeting Slots"), ("fewest-hops", "Fewest Hops")),
    quiz=[Q("To maximise non-overlapping meetings, pick the one that:", ["Ends earliest", "Starts latest", "Is longest", "Is shortest always"], 0, "It leaves the most room for the rest.")]),
 ]),
])
