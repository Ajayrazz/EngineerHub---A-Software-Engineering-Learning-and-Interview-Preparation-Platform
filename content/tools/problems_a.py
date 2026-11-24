"""Original EngineerHub practice problems, part A. Every problem has a reference solution (ref) and an
independent brute-force (brute); build_problems.py refuses to emit a problem unless they agree on every test."""
import random
from collections import deque, Counter
import heapq

def ints(s): return list(map(int, s.split()))

# ---- 1 pair-with-target-sum -------------------------------------------------------------------------
def ref_pair(inp):
    l = inp.split("\n"); n, t = ints(l[0]); a = ints(l[1]); first = {}
    for j, x in enumerate(a):
        if t - x in first: return f"{first[t-x]+1} {j+1}"
        first.setdefault(x, j)
    return "-1"
def brute_pair(inp):
    l = inp.split("\n"); n, t = ints(l[0]); a = ints(l[1])
    for j in range(n):
        for i in range(j):
            if a[i] + a[j] == t: return f"{i+1} {j+1}"
    return "-1"
def gen_pair(r):
    n = r.randint(2, 9); return f"{n} {r.randint(-6,12)}\n" + " ".join(str(r.randint(-5,9)) for _ in range(n))

# ---- 2 calm stretch (sliding window with monotonic deques) ---------------------------------------------
def ref_calm(inp):
    l = inp.split("\n"); n, k = ints(l[0]); a = ints(l[1]); mx, mn = deque(), deque(); lo = best = 0
    for hi, x in enumerate(a):
        while mx and a[mx[-1]] <= x: mx.pop()
        while mn and a[mn[-1]] >= x: mn.pop()
        mx.append(hi); mn.append(hi)
        while a[mx[0]] - a[mn[0]] > k:
            lo += 1
            if mx[0] < lo: mx.popleft()
            if mn[0] < lo: mn.popleft()
        best = max(best, hi - lo + 1)
    return str(best)
def brute_calm(inp):
    l = inp.split("\n"); n, k = ints(l[0]); a = ints(l[1]); best = 0
    for i in range(n):
        for j in range(i, n):
            if max(a[i:j+1]) - min(a[i:j+1]) <= k: best = max(best, j - i + 1)
    return str(best)
def gen_calm(r):
    n = r.randint(1, 10); return f"{n} {r.randint(0,6)}\n" + " ".join(str(r.randint(0,12)) for _ in range(n))

# ---- 3 rotated search -----------------------------------------------------------------------------------
def ref_rot(inp):
    l = inp.split("\n"); n, t = ints(l[0]); a = ints(l[1]); lo, hi = 0, n - 1
    while lo <= hi:
        m = (lo + hi) // 2
        if a[m] == t: return str(m)
        if a[lo] <= a[m]:
            if a[lo] <= t < a[m]: hi = m - 1
            else: lo = m + 1
        else:
            if a[m] < t <= a[hi]: lo = m + 1
            else: hi = m - 1
    return "-1"
def brute_rot(inp):
    l = inp.split("\n"); n, t = ints(l[0]); a = ints(l[1])
    return str(a.index(t)) if t in a else "-1"
def gen_rot(r):
    n = r.randint(1, 10); base = sorted(r.sample(range(-20, 40), n)); k = r.randint(0, n - 1)
    a = base[k:] + base[:k]; t = r.choice(a) if r.random() < .6 else r.randint(-25, 45)
    return f"{n} {t}\n" + " ".join(map(str, a))

# ---- 4 ship within days (binary search on the answer) ----------------------------------------------------
def days_needed(w, cap):
    d, cur = 1, 0
    for x in w:
        if cur + x > cap: d += 1; cur = 0
        cur += x
    return d
def ref_ship(inp):
    l = inp.split("\n"); n, d = ints(l[0]); w = ints(l[1]); lo, hi = max(w), sum(w)
    while lo < hi:
        m = (lo + hi) // 2
        if days_needed(w, m) <= d: hi = m
        else: lo = m + 1
    return str(lo)
def brute_ship(inp):
    l = inp.split("\n"); n, d = ints(l[0]); w = ints(l[1]); c = max(w)
    while days_needed(w, c) > d: c += 1
    return str(c)
def gen_ship(r):
    n = r.randint(1, 9); return f"{n} {r.randint(1,n)}\n" + " ".join(str(r.randint(1,15)) for _ in range(n))

# ---- 5 balanced brackets (stack) ----------------------------------------------------------------------------
def ref_brk(inp):
    st, pair = [], {")": "(", "]": "[", "}": "{"}
    for c in inp.strip():
        if c in "([{": st.append(c)
        elif not st or st.pop() != pair[c]: return "NO"
    return "NO" if st else "YES"
def brute_brk(inp):
    s = inp.strip()
    while True:
        t = s.replace("()", "").replace("[]", "").replace("{}", "")
        if t == s: break
        s = t
    return "YES" if s == "" else "NO"
def gen_brk(r): return "".join(r.choice("()[]{}") for _ in range(r.randint(1, 10)))
def gen_brk_valid(r):
    def build(d):
        if d == 0 or r.random() < .3: return ""
        o, c = r.choice(["()", "[]", "{}"]); return o + build(d - 1) + c + build(d - 1)
    return build(4) or "()"

# ---- 6 next warmer day (monotonic stack) --------------------------------------------------------------------
def ref_warm(inp):
    l = inp.split("\n"); n = int(l[0]); a = ints(l[1]); res = [0] * n; st = []
    for i, x in enumerate(a):
        while st and a[st[-1]] < x: j = st.pop(); res[j] = i - j
        st.append(i)
    return " ".join(map(str, res))
def brute_warm(inp):
    l = inp.split("\n"); n = int(l[0]); a = ints(l[1])
    return " ".join(str(next((j - i for j in range(i + 1, n) if a[j] > a[i]), 0)) for i in range(n))
def gen_warm(r):
    n = r.randint(1, 10); return f"{n}\n" + " ".join(str(r.randint(10, 40)) for _ in range(n))

# ---- 7 merge meeting slots (intervals) ------------------------------------------------------------------------
def ref_merge(inp):
    l = inp.split("\n"); n = int(l[0]); iv = sorted(tuple(ints(x)) for x in l[1:n+1]); out = []
    for s, e in iv:
        if out and s <= out[-1][1]: out[-1][1] = max(out[-1][1], e)
        else: out.append([s, e])
    return "\n".join([str(len(out))] + [f"{s} {e}" for s, e in out])
def brute_merge(inp):
    l = inp.split("\n"); n = int(l[0]); iv = [tuple(ints(x)) for x in l[1:n+1]]
    pts = sorted({p for s, e in iv for p in (s, e)}); covered = set()
    for s, e in iv:
        for p in range(s, e): covered.add(p)          # unit cells [p, p+1)
    out, cur = [], None
    for p in range(pts[0], pts[-1]):
        if p in covered:
            if cur is None: cur = [p, p + 1]
            else: cur[1] = p + 1
        elif cur: out.append(cur); cur = None
    if cur: out.append(cur)
    # touching slots (end == next start) count as one meeting block, which unit cells already give
    return "\n".join([str(len(out))] + [f"{s} {e}" for s, e in out])
def gen_merge(r):
    n = r.randint(1, 7); rows = []
    for _ in range(n):
        s = r.randint(0, 20); rows.append(f"{s} {s + r.randint(1, 6)}")
    return f"{n}\n" + "\n".join(rows)

# ---- 8 subarrays with sum k (prefix sums + hashing) ---------------------------------------------------------
def ref_sumk(inp):
    l = inp.split("\n"); n, k = ints(l[0]); a = ints(l[1]); seen = Counter({0: 1}); s = c = 0
    for x in a:
        s += x; c += seen[s - k]; seen[s] += 1
    return str(c)
def brute_sumk(inp):
    l = inp.split("\n"); n, k = ints(l[0]); a = ints(l[1])
    return str(sum(1 for i in range(n) for j in range(i, n) if sum(a[i:j+1]) == k))
def gen_sumk(r):
    n = r.randint(1, 10); return f"{n} {r.randint(-3,5)}\n" + " ".join(str(r.randint(-3,4)) for _ in range(n))

# ---- 9 fewest coins (dp) ----------------------------------------------------------------------------------------
def ref_coins(inp):
    l = inp.split("\n"); n, t = ints(l[0]); c = ints(l[1]); INF = 10**9; dp = [0] + [INF] * t
    for v in range(1, t + 1):
        for x in c:
            if x <= v and dp[v - x] + 1 < dp[v]: dp[v] = dp[v - x] + 1
    return str(dp[t] if dp[t] < INF else -1)
def brute_coins(inp):
    l = inp.split("\n"); n, t = ints(l[0]); c = ints(l[1]); best = [10**9]
    def go(rem, used, idx):
        if used >= best[0]: return
        if rem == 0: best[0] = used; return
        if idx == len(c): return
        for k in range(rem // c[idx], -1, -1): go(rem - k * c[idx], used + k, idx + 1)
    go(t, 0, 0); return str(best[0] if best[0] < 10**9 else -1)
def gen_coins(r):
    n = r.randint(1, 4); return f"{n} {r.randint(0,25)}\n" + " ".join(map(str, r.sample(range(1, 10), n)))

# ---- 10 staircase with skips (dp, modular) ---------------------------------------------------------------------
MOD = 10**9 + 7
def ref_stairs(inp):
    n = int(inp); dp = [1] + [0] * n
    for i in range(1, n + 1): dp[i] = sum(dp[i - s] for s in (1, 2, 3) if i >= s) % MOD
    return str(dp[n])
def brute_stairs(inp):
    n = int(inp)
    def go(r): return 1 if r == 0 else sum(go(r - s) for s in (1, 2, 3) if r >= s)
    return str(go(n) % MOD)

# ---- 11 longest common run (LCS) ----------------------------------------------------------------------------------
def ref_lcs(inp):
    a, b = inp.split("\n")[:2]; dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            dp[i][j] = dp[i-1][j-1] + 1 if a[i-1] == b[j-1] else max(dp[i-1][j], dp[i][j-1])
    return str(dp[-1][-1])
def brute_lcs(inp):
    a, b = inp.split("\n")[:2]; best = 0
    for mask in range(1 << len(a)):
        sub = "".join(a[i] for i in range(len(a)) if mask >> i & 1)
        it = iter(b)
        if all(ch in it for ch in sub): best = max(best, len(sub))
    return str(best)
def gen_lcs(r): return "\n".join("".join(r.choice("abc") for _ in range(r.randint(1, 8))) for _ in range(2))

# ---- 12 island counter (dfs on grids) -------------------------------------------------------------------------------
def ref_island(inp):
    l = inp.split("\n"); h, w = ints(l[0]); g = [list(x) for x in l[1:h+1]]; c = 0
    for i in range(h):
        for j in range(w):
            if g[i][j] == "#":
                c += 1; st = [(i, j)]; g[i][j] = "."
                while st:
                    x, y = st.pop()
                    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                        p, q = x + dx, y + dy
                        if 0 <= p < h and 0 <= q < w and g[p][q] == "#": g[p][q] = "."; st.append((p, q))
    return str(c)
def brute_island(inp):
    l = inp.split("\n"); h, w = ints(l[0]); g = l[1:h+1]
    cells = [(i, j) for i in range(h) for j in range(w) if g[i][j] == "#"]
    parent = {c: c for c in cells}
    def find(x):
        while parent[x] != x: x = parent[x]
        return x
    changed = True
    while changed:
        changed = False
        for (i, j) in cells:
            for (p, q) in ((i+1, j), (i, j+1)):
                if (p, q) in parent and find((i, j)) != find((p, q)): parent[find((p, q))] = find((i, j)); changed = True
    return str(len({find(c) for c in cells}))
def gen_island(r):
    h, w = r.randint(1, 6), r.randint(1, 6)
    return f"{h} {w}\n" + "\n".join("".join(r.choice("#.#.") for _ in range(w)) for _ in range(h))

# ---- 13 shortest maze path (bfs) -----------------------------------------------------------------------------------
def ref_maze(inp):
    l = inp.split("\n"); h, w = ints(l[0]); g = l[1:h+1]; s = e = None
    for i in range(h):
        for j in range(w):
            if g[i][j] == "S": s = (i, j)
            if g[i][j] == "E": e = (i, j)
    dist = {s: 0}; dq = deque([s])
    while dq:
        x, y = dq.popleft()
        if (x, y) == e: return str(dist[e])
        for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
            p, q = x + dx, y + dy
            if 0 <= p < h and 0 <= q < w and g[p][q] != "#" and (p, q) not in dist: dist[(p, q)] = dist[(x, y)] + 1; dq.append((p, q))
    return "-1"
def brute_maze(inp):  # Bellman-Ford style relaxation to a fixed point
    l = inp.split("\n"); h, w = ints(l[0]); g = l[1:h+1]; INF = 10**9
    d = [[INF] * w for _ in range(h)]; e = None
    for i in range(h):
        for j in range(w):
            if g[i][j] == "S": d[i][j] = 0
            if g[i][j] == "E": e = (i, j)
    for _ in range(h * w + 1):
        for i in range(h):
            for j in range(w):
                if g[i][j] == "#": continue
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    p, q = i + dx, j + dy
                    if 0 <= p < h and 0 <= q < w and g[p][q] != "#" and d[p][q] + 1 < d[i][j]: d[i][j] = d[p][q] + 1
    return str(d[e[0]][e[1]] if d[e[0]][e[1]] < INF else -1)
def gen_maze(r):
    h, w = r.randint(2, 6), r.randint(2, 6); g = [[r.choice("..#") for _ in range(w)] for _ in range(h)]
    cells = r.sample([(i, j) for i in range(h) for j in range(w)], 2)
    g[cells[0][0]][cells[0][1]] = "S"; g[cells[1][0]][cells[1][1]] = "E"
    return f"{h} {w}\n" + "\n".join("".join(x) for x in g)
