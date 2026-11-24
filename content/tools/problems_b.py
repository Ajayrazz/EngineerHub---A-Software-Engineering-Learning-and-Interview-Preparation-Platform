"""Original EngineerHub practice problems, part B (see problems_a.py for the verification rule)."""
import heapq
from collections import Counter
from itertools import combinations
from problems_a import ints

# ---- 14 course order (topological sort, smallest label first) ------------------------------------------------
def ref_topo(inp):
    l = inp.split("\n"); n, m = ints(l[0]); adj = [[] for _ in range(n + 1)]; indeg = [0] * (n + 1)
    for row in l[1:m+1]:
        a, b = ints(row); adj[a].append(b); indeg[b] += 1
    h = [i for i in range(1, n + 1) if indeg[i] == 0]; heapq.heapify(h); out = []
    while h:
        x = heapq.heappop(h); out.append(x)
        for y in adj[x]:
            indeg[y] -= 1
            if indeg[y] == 0: heapq.heappush(h, y)
    return " ".join(map(str, out)) if len(out) == n else "IMPOSSIBLE"
def brute_topo(inp):  # repeatedly take the smallest label whose prerequisites are all taken
    l = inp.split("\n"); n, m = ints(l[0]); pre = {i: set() for i in range(1, n + 1)}
    for row in l[1:m+1]:
        a, b = ints(row); pre[b].add(a)
    done, out = set(), []
    while len(out) < n:
        ready = [i for i in range(1, n + 1) if i not in done and pre[i] <= done]
        if not ready: return "IMPOSSIBLE"
        x = min(ready); done.add(x); out.append(x)
    return " ".join(map(str, out))
def gen_topo(r):
    n = r.randint(1, 6); edges = set()
    for _ in range(r.randint(0, 7)):
        a, b = r.randint(1, n), r.randint(1, n)
        if a != b: edges.add((a, b))
    return f"{n} {len(edges)}\n" + "\n".join(f"{a} {b}" for a, b in sorted(edges))

# ---- 15 cheapest route (Dijkstra) ---------------------------------------------------------------------------
def ref_dij(inp):
    l = inp.split("\n"); n, m = ints(l[0]); adj = [[] for _ in range(n + 1)]
    for row in l[1:m+1]:
        a, b, w = ints(row); adj[a].append((b, w)); adj[b].append((a, w))
    d = [10**18] * (n + 1); d[1] = 0; h = [(0, 1)]
    while h:
        c, x = heapq.heappop(h)
        if c > d[x]: continue
        for y, w in adj[x]:
            if c + w < d[y]: d[y] = c + w; heapq.heappush(h, (d[y], y))
    return str(d[n] if d[n] < 10**18 else -1)
def brute_dij(inp):  # Bellman-Ford
    l = inp.split("\n"); n, m = ints(l[0]); E = [ints(x) for x in l[1:m+1]]; d = [10**18] * (n + 1); d[1] = 0
    for _ in range(n):
        for a, b, w in E:
            if d[a] + w < d[b]: d[b] = d[a] + w
            if d[b] + w < d[a]: d[a] = d[b] + w
    return str(d[n] if d[n] < 10**18 else -1)
def gen_dij(r):
    n = r.randint(2, 7); m = r.randint(0, 10)
    return f"{n} {m}\n" + "\n".join(f"{r.randint(1,n)} {r.randint(1,n)} {r.randint(1,9)}" for _ in range(m))

# ---- 16 most meetings (greedy by earliest finish) ------------------------------------------------------------
def ref_meet(inp):
    l = inp.split("\n"); n = int(l[0]); iv = sorted((ints(x) for x in l[1:n+1]), key=lambda p: (p[1], p[0])); end = -1; c = 0
    for s, e in iv:
        if s >= end: c += 1; end = e
    return str(c)
def brute_meet(inp):
    l = inp.split("\n"); n = int(l[0]); iv = [ints(x) for x in l[1:n+1]]; best = 0
    for mask in range(1 << n):
        ch = sorted(iv[i] for i in range(n) if mask >> i & 1)
        if all(ch[i][1] <= ch[i+1][0] for i in range(len(ch) - 1)): best = max(best, len(ch))
    return str(best)
def gen_meet(r):
    n = r.randint(1, 8); rows = []
    for _ in range(n): s = r.randint(0, 14); rows.append(f"{s} {s + r.randint(1,5)}")
    return f"{n}\n" + "\n".join(rows)

# ---- 17 fewest hops (greedy jump game) -------------------------------------------------------------------------
def ref_jump(inp):
    l = inp.split("\n"); n = int(l[0]); a = ints(l[1]); hops = cur = far = 0
    for i in range(n - 1):
        far = max(far, i + a[i])
        if i == cur:
            if far <= i: return "-1"
            hops += 1; cur = far
            if cur >= n - 1: break
    return str(hops)
def brute_jump(inp):
    l = inp.split("\n"); n = int(l[0]); a = ints(l[1]); INF = 10**9; dp = [INF] * n; dp[0] = 0
    for i in range(n):
        if dp[i] == INF: continue
        for k in range(1, a[i] + 1):
            if i + k < n: dp[i + k] = min(dp[i + k], dp[i] + 1)
    return str(dp[-1] if dp[-1] < INF else -1)
def gen_jump(r):
    n = r.randint(1, 9); return f"{n}\n" + " ".join(str(r.randint(0, 4)) for _ in range(n))

# ---- 18 kth largest (heap) ------------------------------------------------------------------------------------------
def ref_kth(inp):
    l = inp.split("\n"); n, k = ints(l[0]); a = ints(l[1]); h = []
    for x in a:
        heapq.heappush(h, x)
        if len(h) > k: heapq.heappop(h)
    return str(h[0])
def brute_kth(inp):
    l = inp.split("\n"); n, k = ints(l[0]); a = ints(l[1]); return str(sorted(a, reverse=True)[k - 1])
def gen_kth(r):
    n = r.randint(1, 10); return f"{n} {r.randint(1,n)}\n" + " ".join(str(r.randint(-9, 9)) for _ in range(n))

# ---- 19 anagram groups (hashing) --------------------------------------------------------------------------------------
def ref_ana(inp):
    l = inp.split("\n"); n = int(l[0]); return str(len({"".join(sorted(w)) for w in l[1].split()}))
def brute_ana(inp):
    l = inp.split("\n"); w = l[1].split(); groups = []
    for x in w:
        for g in groups:
            if Counter(g[0]) == Counter(x): g.append(x); break
        else: groups.append([x])
    return str(len(groups))
def gen_ana(r):
    n = r.randint(1, 8); return f"{n}\n" + " ".join("".join(r.choice("abc") for _ in range(r.randint(1, 4))) for _ in range(n))

# ---- 20 longest unique stretch (sliding window) ----------------------------------------------------------------------
def ref_uniq(inp):
    s = inp.strip(); last = {}; lo = best = 0
    for i, c in enumerate(s):
        if c in last and last[c] >= lo: lo = last[c] + 1
        last[c] = i; best = max(best, i - lo + 1)
    return str(best)
def brute_uniq(inp):
    s = inp.strip(); return str(max(j - i for i in range(len(s)) for j in range(i + 1, len(s) + 1) if len(set(s[i:j])) == j - i))
def gen_uniq(r): return "".join(r.choice("abcd") for _ in range(r.randint(1, 12)))

# ---- 21 subsets hitting a target (backtracking) -----------------------------------------------------------------------
def ref_subs(inp):
    l = inp.split("\n"); n, t = ints(l[0]); a = ints(l[1])
    def go(i, rem): return (rem == 0) if i == n else go(i + 1, rem) + go(i + 1, rem - a[i])
    return str(int(go(0, t)))
def brute_subs(inp):
    l = inp.split("\n"); n, t = ints(l[0]); a = ints(l[1])
    return str(sum(1 for mask in range(1 << n) if sum(a[i] for i in range(n) if mask >> i & 1) == t))
def gen_subs(r):
    n = r.randint(1, 9); return f"{n} {r.randint(0,12)}\n" + " ".join(str(r.randint(-3, 6)) for _ in range(n))

# ---- 22 trapped rainwater (two pointers) ---------------------------------------------------------------------------------
def ref_rain(inp):
    l = inp.split("\n"); n = int(l[0]); h = ints(l[1]); lo, hi = 0, n - 1; lm = rm = tot = 0
    while lo < hi:
        if h[lo] < h[hi]:
            lm = max(lm, h[lo]); tot += lm - h[lo]; lo += 1
        else:
            rm = max(rm, h[hi]); tot += rm - h[hi]; hi -= 1
    return str(tot)
def brute_rain(inp):
    l = inp.split("\n"); n = int(l[0]); h = ints(l[1])
    return str(sum(max(0, min(max(h[:i+1]), max(h[i:])) - h[i]) for i in range(n)))
def gen_rain(r):
    n = r.randint(1, 10); return f"{n}\n" + " ".join(str(r.randint(0, 7)) for _ in range(n))

# ---- 23 edit distance (dp) ------------------------------------------------------------------------------------------------
def ref_edit(inp):
    a, b = inp.split("\n")[:2]; dp = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        prev, dp[0] = dp[0], i
        for j in range(1, len(b) + 1):
            cur = dp[j]; dp[j] = min(dp[j] + 1, dp[j-1] + 1, prev + (a[i-1] != b[j-1])); prev = cur
    return str(dp[-1])
def brute_edit(inp):
    a, b = inp.split("\n")[:2]
    from functools import lru_cache
    @lru_cache(None)
    def go(i, j):
        if i == len(a): return len(b) - j
        if j == len(b): return len(a) - i
        if a[i] == b[j]: return go(i + 1, j + 1)
        return 1 + min(go(i + 1, j), go(i, j + 1), go(i + 1, j + 1))
    return str(go(0, 0))
def gen_edit(r): return "\n".join("".join(r.choice("abc") for _ in range(r.randint(0, 6))) or "a" for _ in range(2))

# ---- 24 friend circles (union-find) -----------------------------------------------------------------------------------------
def ref_uf(inp):
    l = inp.split("\n"); n, m = ints(l[0]); p = list(range(n + 1)); c = n
    def f(x):
        while p[x] != x: p[x] = p[p[x]]; x = p[x]
        return x
    for row in l[1:m+1]:
        a, b = ints(row); ra, rb = f(a), f(b)
        if ra != rb: p[ra] = rb; c -= 1
    return str(c)
def brute_uf(inp):
    l = inp.split("\n"); n, m = ints(l[0]); comp = {i: {i} for i in range(1, n + 1)}
    for row in l[1:m+1]:
        a, b = ints(row)
        if comp[a] is not comp[b]:
            merged = comp[a] | comp[b]
            for x in merged: comp[x] = merged
    return str(len({id(s) for s in comp.values()}))
def gen_uf(r):
    n = r.randint(1, 8); m = r.randint(0, 8)
    return f"{n} {m}\n" + "\n".join(f"{r.randint(1,n)} {r.randint(1,n)}" for _ in range(m))

# ---- 25 largest rectangle in a skyline (monotonic stack) -------------------------------------------------------------------
def ref_hist(inp):
    l = inp.split("\n"); n = int(l[0]); h = ints(l[1]) + [0]; st = []; best = 0
    for i, x in enumerate(h):
        while st and h[st[-1]] >= x:
            top = h[st.pop()]; left = st[-1] if st else -1; best = max(best, top * (i - left - 1))
        st.append(i)
    return str(best)
def brute_hist(inp):
    l = inp.split("\n"); n = int(l[0]); h = ints(l[1])
    return str(max(min(h[i:j+1]) * (j - i + 1) for i in range(n) for j in range(i, n)))
def gen_hist(r):
    n = r.randint(1, 9); return f"{n}\n" + " ".join(str(r.randint(0, 8)) for _ in range(n))
