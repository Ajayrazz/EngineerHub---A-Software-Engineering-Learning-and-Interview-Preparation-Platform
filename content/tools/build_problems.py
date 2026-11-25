"""Builds content/problems.json. A problem is emitted only if its reference solution and its independent
brute-force agree on EVERY test (docs/20 §8 rule 2): expected outputs are produced by running code, never typed."""
import json, random, sys, pathlib
import problems_a as A, problems_b as B

def P(slug, title, diff, tier, topics, patterns, statement, inp, out, cons, hints, ref, brute, gen, samples, edges=()):
    return dict(slug=slug, title=title, difficulty=diff, tier=tier, topics=topics, patterns=patterns, statement=statement,
                inputFormat=inp, outputFormat=out, constraints=cons, hints=hints, ref=ref, brute=brute, gen=gen,
                samples=samples, edges=list(edges))

PROBLEMS = [
 P("pair-with-target-sum", "Pair With Target Sum", "easy", "free", ["arrays", "hashing"], ["hashing"],
   "A shop keeps a list of item prices. A customer wants exactly **two different items** whose prices add up to a given budget.\n\nFind the pair that is *completed earliest*: among all valid pairs `(i, j)` with `i < j`, choose the one with the smallest `j`, and for that `j` the smallest `i`. Print the 1-based positions `i j`, or `-1` if no pair exists.",
   "Line 1: `n t` (item count and budget). Line 2: `n` integers, the prices.", "`i j` or `-1`.",
   "2 ≤ n ≤ 100 000; prices and t fit in 32-bit signed integers.",
   ["Checking every pair works but is too slow for large n.", "While scanning left to right, what do you need to remember about the prices you have already seen?", "A hash map from price to the first position it appeared turns the inner loop into one lookup."],
   A.ref_pair, A.brute_pair, A.gen_pair,
   [("4 9\n2 7 11 15", "Prices 2 and 7 (positions 1 and 2) sum to 9."), ("3 100\n1 2 3", "No two prices reach 100.")],
   ["2 4\n2 2", "5 0\n-3 5 3 0 0"]),
 P("calm-stretch", "Calm Stretch", "medium", "free", ["arrays", "sliding-window"], ["sliding-window"],
   "A sensor reports one reading per minute. A stretch of consecutive minutes is **calm** if the highest and lowest readings in it differ by at most `k`.\n\nPrint the length of the longest calm stretch.",
   "Line 1: `n k`. Line 2: `n` integers, the readings.", "One integer: the longest calm stretch.", "1 ≤ n ≤ 200 000; 0 ≤ k ≤ 10^9; |reading| ≤ 10^9.",
   ["Try a window that grows on the right and shrinks on the left.", "To know the window's max and min quickly after the left side moves, what structure keeps them in order?", "Two monotonic deques (one for the max, one for the min) give O(n) overall."],
   A.ref_calm, A.brute_calm, A.gen_calm,
   [("6 2\n4 6 5 9 8 7", "Minutes 1-3 (4, 6, 5) differ by 2; no longer stretch qualifies."), ("1 0\n7", "A single reading is always calm.")], ["3 0\n5 5 5"]),
 P("rotated-lookup", "Rotated Lookup", "medium", "free", ["arrays", "binary-search"], ["binary-search"],
   "A sorted list of **distinct** integers was cut at some position and the two parts swapped, so it may look like `[40, 50, 10, 20, 30]`.\n\nGiven the rotated list and a value, print the 0-based position of the value, or `-1` if it is absent. Aim for logarithmic time.",
   "Line 1: `n t`. Line 2: `n` distinct integers.", "The index or `-1`.", "1 ≤ n ≤ 100 000.",
   ["At any midpoint, at least one half of the range is still sorted.", "Decide which half is sorted, then check whether the target can lie inside it.", "Keep narrowing the range exactly like binary search."],
   A.ref_rot, A.brute_rot, A.gen_rot,
   [("5 20\n40 50 10 20 30", "20 sits at index 3."), ("4 8\n3 4 1 2", "8 is not in the list.")], ["1 5\n5"]),
 P("minimum-ship-capacity", "Minimum Ship Capacity", "medium", "premium", ["arrays", "binary-search"], ["binary-search"],
   "Packages with given weights must be shipped **in the given order**. Each day the truck takes consecutive packages as long as their total weight fits its capacity. You have `d` days.\n\nPrint the smallest truck capacity that ships every package within `d` days.",
   "Line 1: `n d`. Line 2: `n` positive integers, the weights.", "One integer: the minimum capacity.", "1 ≤ d ≤ n ≤ 100 000; 1 ≤ weight ≤ 10 000.",
   ["The capacity can never be below the heaviest package nor above the total weight.", "If a capacity works, any larger one works too. What does that suggest?", "Binary search the capacity; for each guess, greedily count the days needed."],
   A.ref_ship, A.brute_ship, A.gen_ship,
   [("6 3\n4 2 3 7 1 5", "Capacity 9 gives days {4,2,3}, {7,1}, {5}; 8 would need four days."), ("3 3\n2 9 4", "One package per day, so the heaviest decides.")], ["1 1\n10"]),
 P("balanced-brackets", "Balanced Brackets", "easy", "free", ["strings", "stacks"], ["stack"],
   "A string is made only of the characters `(`, `)`, `[`, `]`, `{` and `}`. It is **balanced** when every opener is closed by the same kind of bracket in the correct order.\n\nPrint `YES` if the string is balanced, otherwise `NO`.",
   "One line: the bracket string.", "`YES` or `NO`.", "1 ≤ length ≤ 100 000.",
   ["The most recent unclosed opener must be closed first.", "Which data structure gives you the most recent item?", "Push openers; on a closer, the top of the stack must be its partner. Also check the stack is empty at the end."],
   A.ref_brk, A.brute_brk, A.gen_brk, [("([]{})", "Every pair nests properly."), ("(]", "A `]` arrives while `(` is open.")],
   ["()", "(((", ")"]),
 P("next-warmer-day", "Next Warmer Day", "medium", "free", ["arrays", "monotonic-stacks"], ["monotonic-stack"],
   "For each day you are given the temperature. For every day, print how many days you must wait until a **strictly warmer** day arrives, or `0` if none ever does.",
   "Line 1: `n`. Line 2: `n` integers, the temperatures.", "`n` integers separated by spaces.", "1 ≤ n ≤ 200 000.",
   ["For one day, scanning forward is O(n); doing it for all days is O(n²).", "Keep days whose answer is still unknown in a stack.", "When a warmer temperature arrives, pop every colder day and record the distance."],
   A.ref_warm, A.brute_warm, A.gen_warm, [("6\n30 20 25 40 35 22", "Day 1 waits 3 days for 40; day 4 never sees warmer.")], ["1\n10", "4\n9 8 7 6"]),
 P("merge-meeting-slots", "Merge Meeting Slots", "medium", "free", ["arrays", "intervals"], ["intervals"],
   "A room booking system stores slots as `[start, end)` pairs. Slots that overlap **or touch** (one ends exactly when the next starts) should be shown as a single block.\n\nPrint the number of blocks, then each block as `start end`, ordered by start.",
   "Line 1: `n`. Then `n` lines, each `start end`.", "First line: the number of blocks. Then one line per block.", "1 ≤ n ≤ 100 000; 0 ≤ start < end ≤ 10^9.",
   ["Order matters: sort first.", "After sorting by start, compare each slot only with the last merged block.", "If it starts at or before the block's end, extend the block; otherwise start a new one."],
   A.ref_merge, A.brute_merge, A.gen_merge,
   [("4\n1 3\n2 6\n8 10\n6 7", "[1,3] and [2,6] merge to [1,6]; [6,7] touches it, giving [1,7]; [8,10] stays apart.")], ["1\n3 4"]),
 P("subarrays-with-sum", "Subarrays With Sum", "medium", "premium", ["arrays", "prefix-sums", "hashing"], ["prefix-sums", "hashing"],
   "Count the contiguous, non-empty subarrays whose elements add up to exactly `k`. Numbers may be negative, so a sliding window will not work.",
   "Line 1: `n k`. Line 2: `n` integers.", "One integer: the count.", "1 ≤ n ≤ 200 000; |a_i| ≤ 1000.",
   ["Let P[i] be the sum of the first i numbers. A subarray (i, j] has sum P[j] - P[i].", "For each j you need how many earlier prefixes equal P[j] - k.", "Store prefix-sum counts in a hash map as you scan."],
   A.ref_sumk, A.brute_sumk, A.gen_sumk, [("5 3\n1 2 1 2 1", "The subarrays at positions 1-2, 2-3, 3-4 and 4-5 each sum to 3."), ("3 0\n0 0 0", "Six zero-sum subarrays.")], ["1 5\n5"]),
 P("fewest-coins", "Fewest Coins", "medium", "free", ["dynamic-programming"], ["dynamic-programming"],
   "You have unlimited coins of several denominations. Print the **fewest coins** that add up to exactly `t`, or `-1` if it is impossible. A total of 0 needs 0 coins.",
   "Line 1: `n t`. Line 2: `n` distinct coin values.", "One integer.", "1 ≤ n ≤ 12; 1 ≤ coin ≤ 10 000; 0 ≤ t ≤ 10 000.",
   ["Greedy (always the biggest coin) can fail. Think about smaller totals.", "Let best[v] be the fewest coins for total v.", "best[v] = 1 + min(best[v - c]) over coins c ≤ v."],
   A.ref_coins, A.brute_coins, A.gen_coins, [("3 11\n1 2 5", "5 + 5 + 1."), ("2 3\n2 4", "Odd totals cannot be formed from even coins.")], ["1 0\n7"]),
 P("staircase-ways", "Staircase Ways", "easy", "free", ["dynamic-programming"], ["dynamic-programming"],
   "A staircase has `n` steps. You may climb 1, 2 or 3 steps at a time. Print the number of distinct ways to reach the top, modulo 1 000 000 007.",
   "One integer `n`.", "One integer.", "0 ≤ n ≤ 100 000.",
   ["The last move was 1, 2 or 3 steps.", "ways[i] = ways[i-1] + ways[i-2] + ways[i-3].", "Remember to take the modulo at every addition."],
   A.ref_stairs, A.brute_stairs, lambda r: str(r.randint(0, 14)), [("4", "1+1+1+1, 1+1+2, 1+2+1, 2+1+1, 2+2, 1+3, 3+1 = 7 ways.")], ["0", "1"]),
 P("longest-common-run", "Longest Common Run", "medium", "premium", ["strings", "dynamic-programming"], ["dynamic-programming"],
   "Two strings are given. Print the length of the longest sequence of characters that appears in **both**, in the same order (characters need not be adjacent).",
   "Two lines, one string each.", "One integer.", "1 ≤ length ≤ 2000; lowercase letters.",
   ["Compare prefixes: what happens when the last characters match?", "dp[i][j] = dp[i-1][j-1] + 1 if they match, else max(dp[i-1][j], dp[i][j-1]).", "You only need the previous row to compute the next."],
   A.ref_lcs, A.brute_lcs, A.gen_lcs, [("abcde\nace", "The common sequence `ace` has length 3.")], ["a\nb"]),
 P("island-counter", "Island Counter", "medium", "free", ["graphs", "depth-first-search"], ["dfs"],
   "A map is a grid of `#` (land) and `.` (water). Land cells touching horizontally or vertically belong to the same island. Print the number of islands.",
   "Line 1: `h w`. Then `h` lines of `w` characters.", "One integer.", "1 ≤ h, w ≤ 500.",
   ["When you find land, everything connected to it is one island.", "Walk the island with DFS or BFS and mark its cells as visited.", "Count how many times you start a new walk."],
   A.ref_island, A.brute_island, A.gen_island, [("3 4\n##..\n#..#\n...#", "Two islands: the top-left group and the right-hand pair.")], ["1 1\n."]),
 P("shortest-maze-path", "Shortest Maze Path", "medium", "free", ["graphs", "breadth-first-search"], ["bfs"],
   "A maze has open cells, walls `#`, a start `S` and an exit `E`. You move one step up, down, left or right through open cells. Print the minimum number of steps from `S` to `E`, or `-1`.",
   "Line 1: `h w`. Then `h` lines of `w` characters.", "One integer.", "2 ≤ h, w ≤ 500; exactly one S and one E.",
   ["All moves cost the same, so the first time you reach a cell is the shortest way.", "Which traversal explores in order of distance?", "Breadth-first search with a queue and a distance per cell."],
   A.ref_maze, A.brute_maze, A.gen_maze, [("3 4\nS..#\n.#..\n...E", "Go down twice, then right three times."), ("2 3\nS#E\n.#.", "The wall column blocks every route.")], ["2 2\nSE\n..", "1 3\nS#E"]),
 P("course-order", "Course Order", "medium", "free", ["graphs", "topological-sort"], ["topological-sort"],
   "A degree has `n` courses numbered 1..n and `m` rules `a b`, meaning course `a` must be taken before `b`. Print an order that respects every rule. If several orders work, print the one that is smallest when compared number by number. Print `IMPOSSIBLE` if the rules contain a cycle.",
   "Line 1: `n m`. Then `m` lines `a b`.", "`n` integers in order, or `IMPOSSIBLE`.", "1 ≤ n ≤ 100 000; 0 ≤ m ≤ 200 000.",
   ["A course with no unmet prerequisite can be taken now.", "Track in-degrees; courses at 0 are ready.", "Use a min-heap for the ready set so the smallest label is always chosen. If fewer than n courses get taken, there is a cycle."],
   B.ref_topo, B.brute_topo, B.gen_topo, [("4 3\n1 2\n1 3\n3 4", "Take 1, then 2, 3, 4."), ("2 2\n1 2\n2 1", "Each course needs the other first.")], ["1 0\n"]),
 P("cheapest-route", "Cheapest Route", "medium", "premium", ["graphs", "shortest-paths"], ["shortest-path"],
   "A country has `n` cities and `m` two-way roads with positive tolls. Print the cheapest total toll from city 1 to city `n`, or `-1` if there is no route.",
   "Line 1: `n m`. Then `m` lines `a b w`.", "One integer.", "2 ≤ n ≤ 100 000; 0 ≤ m ≤ 200 000; 1 ≤ w ≤ 10^6.",
   ["Tolls are positive, so the cheapest known city can be finalised.", "Use a priority queue keyed by distance (Dijkstra).", "Skip a popped entry whose distance is already outdated."],
   B.ref_dij, B.brute_dij, B.gen_dij, [("4 4\n1 2 4\n1 3 1\n3 2 2\n2 4 5", "1 → 3 → 2 → 4 costs 1+2+5 = 8."), ("3 1\n1 2 5", "City 3 is unreachable.")], ["2 0\n"]),
 P("most-meetings", "Most Meetings", "easy", "free", ["arrays", "greedy"], ["greedy", "intervals"],
   "One room, many requests `[start, end)`. Two meetings may share an endpoint (one ends when the next begins). Print the **largest number** of meetings that can be held without overlap.",
   "Line 1: `n`. Then `n` lines `start end`.", "One integer.", "1 ≤ n ≤ 100 000.",
   ["Which meeting should you choose first?", "The one that ends earliest leaves the most room.", "Sort by end time and take every meeting that starts at or after the last chosen end."],
   B.ref_meet, B.brute_meet, B.gen_meet, [("4\n1 3\n2 4\n3 5\n4 6", "Take [1,3) and [3,5), or [2,4) and [4,6): at most 2.")], ["1\n0 1"]),
 P("fewest-hops", "Fewest Hops", "medium", "premium", ["arrays", "greedy"], ["greedy"],
   "You stand on the first of `n` stepping stones. Stone `i` lets you jump **up to** `a[i]` stones forward. Print the fewest jumps needed to reach the last stone, or `-1` if it cannot be reached.",
   "Line 1: `n`. Line 2: `n` integers.", "One integer.", "1 ≤ n ≤ 100 000; 0 ≤ a[i] ≤ 1000.",
   ["Think in layers: all stones reachable with 1 jump, then 2 jumps, and so on.", "Track the farthest stone reachable from the current layer.", "When you pass the end of the current layer, take one more jump."],
   B.ref_jump, B.brute_jump, B.gen_jump, [("5\n2 3 1 1 4", "Jump to stone 2, then to the end: 2 hops."), ("3\n1 0 5", "Stuck on the second stone.")], ["1\n0"]),
 P("kth-largest", "Kth Largest", "easy", "free", ["arrays", "heaps"], ["heap"],
   "Print the `k`-th largest value in a list of numbers (duplicates count separately: in `[5, 5, 3]` the 2nd largest is 5).",
   "Line 1: `n k`. Line 2: `n` integers.", "One integer.", "1 ≤ k ≤ n ≤ 200 000.",
   ["Sorting works in O(n log n).", "You only need to remember the k biggest values seen so far.", "Keep a min-heap of size k; its top is the answer."],
   B.ref_kth, B.brute_kth, B.gen_kth, [("6 2\n3 9 4 9 1 7", "Sorted descending: 9, 9, 7 ... the 2nd is 9.")], ["1 1\n-4"]),
 P("anagram-groups", "Anagram Groups", "easy", "free", ["strings", "hashing"], ["hashing"],
   "Words are anagrams if they use exactly the same letters the same number of times. Print how many **groups of mutually anagram words** the list splits into.",
   "Line 1: `n`. Line 2: `n` words separated by spaces.", "One integer.", "1 ≤ n ≤ 100 000; lowercase letters.",
   ["Two anagrams look identical once their letters are sorted.", "Use the sorted letters (or letter counts) as a hash key.", "The answer is the number of distinct keys."],
   B.ref_ana, B.brute_ana, B.gen_ana, [("5\nlisten silent enlist google gogole", "Two groups: {listen, silent, enlist} and {google, gogole}.")], ["1\na"]),
 P("longest-unique-stretch", "Longest Unique Stretch", "medium", "free", ["strings", "sliding-window"], ["sliding-window", "hashing"],
   "Print the length of the longest substring in which **no character repeats**.",
   "One line: the string.", "One integer.", "1 ≤ length ≤ 200 000.",
   ["Maintain a window with no repeats.", "When a character repeats, move the left edge past its previous position.", "Store the last index of each character."],
   B.ref_uniq, B.brute_uniq, B.gen_uniq, [("abcabcbb", "`abc` has length 3 and nothing longer is repeat-free.")], ["aaaa", "a"]),
 P("subset-target-count", "Subset Target Count", "medium", "premium", ["recursion", "backtracking"], ["backtracking"],
   "Count the subsets of a list (chosen by position, so equal values in different places count separately; the empty subset counts) whose sum equals `t`.",
   "Line 1: `n t`. Line 2: `n` integers.", "One integer.", "1 ≤ n ≤ 20; |a_i| ≤ 100.",
   ["For each element you choose: take it or leave it.", "Recurse on the rest with the remaining target.", "At the end of the list, a match is exactly when the remaining target is 0."],
   B.ref_subs, B.brute_subs, B.gen_subs, [("3 4\n1 3 1", "{1,3} twice (either 1) — 2 subsets.")], ["1 0\n0"]),
 P("trapped-rainwater", "Trapped Rainwater", "hard", "premium", ["arrays", "two-pointers"], ["two-pointers"],
   "Bars of width 1 and given heights stand side by side. After rain, water collects between them. Print the total units of trapped water.",
   "Line 1: `n`. Line 2: `n` non-negative integers.", "One integer.", "1 ≤ n ≤ 200 000; 0 ≤ height ≤ 100 000.",
   ["Water above a bar is limited by the lower of the tallest bar on its left and on its right.", "Precomputing both maxima works in O(n) extra space.", "Two pointers moving inward let you do it in O(1) space: always advance the side with the smaller wall."],
   B.ref_rain, B.brute_rain, B.gen_rain, [("6\n3 0 2 0 4 1", "Positions 2, 3 and 4 hold 3, 1 and 3 units of water: 7 in total.")], ["1\n5", "3\n4 0 4"]),
 P("edit-distance", "Edit Distance", "hard", "premium", ["strings", "dynamic-programming"], ["dynamic-programming"],
   "Turn string `a` into string `b` using the fewest operations, where one operation inserts, deletes or replaces a single character. Print that minimum.",
   "Two lines: `a` then `b`.", "One integer.", "1 ≤ length ≤ 2000.",
   ["Look at the last character of each string.", "If equal, no cost; otherwise consider insert, delete and replace.", "dp[i][j] = 1 + min of the three neighbours; one row is enough."],
   B.ref_edit, B.brute_edit, B.gen_edit, [("kitten\nsitting", "Replace k, replace e, insert g: 3.")], ["a\na", "ab\nb"]),
 P("friend-circles", "Friend Circles", "medium", "free", ["graphs", "union-find"], ["union-find"],
   "`n` people start with no friends. Then `m` friendships `a b` are announced; friendship is transitive for the purposes of a **circle** (a group connected through any chain of friends). Print the number of circles after all announcements.",
   "Line 1: `n m`. Then `m` lines `a b`.", "One integer.", "1 ≤ n ≤ 200 000; 0 ≤ m ≤ 200 000.",
   ["Every merge of two different circles reduces the count by one.", "How can you quickly tell whether two people are already in the same circle?", "Disjoint-set union with path compression."],
   B.ref_uf, B.brute_uf, B.gen_uf, [("5 3\n1 2\n2 3\n4 5", "Two circles remain: {1, 2, 3} and {4, 5}.")], ["1 0\n"]),
 P("largest-skyline-rectangle", "Largest Skyline Rectangle", "hard", "premium", ["arrays", "monotonic-stacks"], ["monotonic-stack"],
   "A skyline is a row of adjacent bars of width 1. Find the area of the largest axis-aligned rectangle that fits entirely inside the skyline.",
   "Line 1: `n`. Line 2: `n` non-negative integers, the bar heights.", "One integer.", "1 ≤ n ≤ 200 000; 0 ≤ height ≤ 10^9.",
   ["For each bar, how far can a rectangle of exactly its height extend left and right?", "The extension stops at the nearest shorter bar on each side.", "A monotonic stack finds both boundaries in one pass."],
   B.ref_hist, B.brute_hist, B.gen_hist, [("6\n2 1 5 6 2 3", "Bars of height 5 and 6 form a 5×2 = 10 rectangle.")], ["1\n0", "3\n2 2 2"]),
]

import problems_warmup
import problems_warmup2
PROBLEMS += problems_warmup.make(P)
PROBLEMS += problems_warmup2.make(P)

def build():
    out, bad = [], False
    for p in PROBLEMS:
        rng = random.Random(p["slug"])
        tests = [(i, True, e) for i, e in p["samples"]] + [(e, False, None) for e in p["edges"]]
        tests = [(i, s, e) for (i, s, e) in tests]
        seen = {t[0] for t in tests}
        tries = 0
        while len(tests) < len(p["samples"]) + len(p["edges"]) + 10 and tries < 400:
            tries += 1; x = p["gen"](rng)
            if x not in seen: seen.add(x); tests.append((x, False, None))
        final = []
        for inp, sample, expl in tests:
            r, b = p["ref"](inp), p["brute"](inp)
            if r != b:
                print(f"MISMATCH {p['slug']}: ref={r!r} brute={b!r} input={inp!r}"); bad = True
            final.append({"input": inp.rstrip("\n"), "expected": r, "sample": sample, **({"explanation": expl} if expl else {})})
        out.append({k: v for k, v in p.items() if k not in ("ref", "brute", "gen", "samples", "edges")} | {"tests": final})
    if bad: sys.exit(1)
    path = pathlib.Path(__file__).resolve().parent.parent / "problems.json"
    path.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n")
    print(f"{len(out)} problems, {sum(len(p['tests']) for p in out)} tests verified (reference == brute force) -> {path}")

if __name__ == "__main__": build()
