"""Original EngineerHub course content, part A. Written for this project; no third-party text."""

def Q(prompt, options, correct, explanation, kind="single"):
    """options: list of strings; correct: index or list of indexes."""
    c = correct if isinstance(correct, list) else [correct]
    return dict(prompt=prompt, options=options, correct=c, explanation=explanation, type=kind if len(c) == 1 or kind == "multi" else "multi")

def L(slug, title, tier, minutes, md, code=None, tip=None, quiz=None, viz=None, quiz_title="Check your understanding"):
    return dict(slug=slug, title=title, tier=tier, minutes=minutes, md=md, code=code, tip=tip, quiz=quiz, viz=viz, quiz_title=quiz_title)

def C(slug, title, description, topic, difficulty, tier, minutes, sections):
    return dict(slug=slug, title=title, description=description, topic=topic, difficulty=difficulty, tier=tier, minutes=minutes, sections=sections)

COURSES_A = [
C("dsa-foundations", "Data Structures & Algorithms Foundations",
  "Build the core toolkit for coding interviews: complexity, arrays and hashing, two pointers, sliding windows and binary search, with visual walkthroughs.",
  "arrays", "beginner", "free", 300, [
  ("Getting started", [
    L("big-o-in-plain-words", "Big-O in plain words", "free", 12,
      "## Why we measure growth\n\nTwo programs can both be correct and still behave very differently when the input grows. **Big-O notation** describes how the work an algorithm does *grows* with the input size `n`, ignoring constant factors.\n\n| Growth | Name | Doubling `n` makes the work... |\n|---|---|---|\n| O(1) | constant | the same |\n| O(log n) | logarithmic | one step longer |\n| O(n) | linear | twice as long |\n| O(n log n) | linearithmic | slightly more than twice |\n| O(n²) | quadratic | four times as long |\n\nWe usually talk about the **worst case** and drop everything but the fastest-growing term: `3n² + 10n + 7` is just O(n²).",
      code=("python", "def has_duplicate(items):\n    # O(n^2): compares every pair\n    for i in range(len(items)):\n        for j in range(i + 1, len(items)):\n            if items[i] == items[j]:\n                return True\n    return False\n\ndef has_duplicate_fast(items):\n    # O(n): one pass, remembering what we have seen\n    seen = set()\n    for x in items:\n        if x in seen:\n            return True\n        seen.add(x)\n    return False", "Same answer, very different growth"),
      tip="Memory counts too. The fast version above trades O(n) extra space for speed.",
      viz=("bubble-sort", "5, 2, 9, 1, 7, 3", ""),
      quiz=[Q("A loop runs once over `n` items and does constant work each time. Its complexity is:", ["O(1)", "O(n)", "O(n²)", "O(log n)"], 1, "One pass over n items is linear."),
            Q("Doubling the input makes an O(n²) algorithm take roughly how much longer?", ["The same time", "Twice as long", "Four times as long", "Eight times as long"], 2, "(2n)² = 4n²."),
            Q("Big-O ignores constant factors, so 5n and n are both O(n).", ["True", "False"], 0, "Constants are dropped; only the growth rate matters.", "true_false")]),
    L("arrays-and-hashing", "Arrays and hash maps", "free", 15,
      "## Two workhorse structures\n\nAn **array** stores items in consecutive memory, so reading `a[i]` is O(1). Inserting in the middle is O(n) because later items must shift.\n\nA **hash map** stores key → value pairs. A hash function turns a key into a bucket number, giving **average O(1)** insert, lookup and delete. Collisions (two keys in one bucket) are handled by chaining or probing; the worst case degrades, which is why good hash functions matter.\n\n### The classic trade\nWhenever a nested loop asks *\"have I seen a matching value?\"*, a hash map can usually turn it into a single pass.",
      code=("python", "def first_pair_with_sum(nums, target):\n    seen = {}                      # value -> index\n    for j, x in enumerate(nums):\n        if target - x in seen:\n            return seen[target - x], j\n        seen.setdefault(x, j)\n    return None", "Pair with a target sum in O(n)"),
      tip="Try the practice problem **Pair With Target Sum** after this lesson.",
      quiz=[Q("What is the average cost of looking up a key in a hash map?", ["O(1)", "O(log n)", "O(n)", "O(n log n)"], 0, "A good hash spreads keys across buckets, so a lookup touches a constant number of entries on average."),
            Q("Which operation is O(n) on an array?", ["Reading a[i]", "Inserting at the front", "Reading the length", "Overwriting a[i]"], 1, "Every existing item has to shift one place.")]),
  ]),
  ("Core techniques", [
    L("two-pointers-and-windows", "Two pointers and sliding windows", "free", 16,
      "## Moving two indexes instead of looping twice\n\nMany array and string problems become linear when you keep **two indexes** and move them intelligently.\n\n- **Opposite ends:** start at both ends of a sorted array and walk inward (pair sums, palindromes, container problems).\n- **Sliding window:** keep a window `[left, right]`; grow `right`, and shrink `left` whenever the window breaks a rule (longest substring without repeats, subarray sums with positive numbers).\n\nThe reason it is O(n): each index only ever moves forward, so together they make at most `2n` moves.",
      code=("python", "def longest_unique(s):\n    last, left, best = {}, 0, 0\n    for right, ch in enumerate(s):\n        if ch in last and last[ch] >= left:\n            left = last[ch] + 1          # jump past the repeat\n        last[ch] = right\n        best = max(best, right - left + 1)\n    return best", "Sliding window with a last-seen map"),
      viz=("sliding-window", "2, 1, 5, 1, 3, 2", "3"),
      quiz=[Q("Why is a sliding window O(n) even though it has a loop inside a loop?", ["It uses recursion", "Each pointer only moves forward, so total moves are at most 2n", "It sorts the array first", "It skips half of the items"], 1, "Neither pointer ever moves backwards."),
            Q("Two pointers from opposite ends of an array normally require the array to be:", ["Sorted (or have a monotone property)", "Of even length", "Free of duplicates", "Made of positive numbers only"], 0, "The inward moves rely on a known ordering to decide which pointer to move.")]),
    L("binary-search-mastery", "Binary search, and binary search on the answer", "free", 18,
      "## Halving the search space\n\nBinary search finds a target in a **sorted** array in O(log n) by repeatedly checking the middle and discarding the half that cannot contain it.\n\nThe same idea works whenever you can answer a yes/no question that is **monotone**: *no, no, no, yes, yes, yes*. Then you can search for the first `yes` — even when there is no array at all (for example the smallest truck capacity that finishes in `d` days).\n\n### Off-by-one safety\nPick one convention and stick to it. Below, the answer lives in `[lo, hi)`; the loop ends when `lo == hi`.",
      code=("python", "def first_true(lo, hi, ok):\n    # smallest x in [lo, hi) with ok(x) True, assuming ok is monotone; returns hi if none\n    while lo < hi:\n        mid = (lo + hi) // 2\n        if ok(mid):\n            hi = mid\n        else:\n            lo = mid + 1\n    return lo", "A reusable boundary search"),
      viz=("binary-search", "2, 5, 8, 12, 16, 23, 38, 56", "23"),
      tip="Practice: **Rotated Lookup** and **Minimum Ship Capacity**.",
      quiz=[Q("How many comparisons does binary search need for 1,000,000 sorted items, roughly?", ["About 20", "About 1,000", "About 500,000", "1,000,000"], 0, "log2(1,000,000) ≈ 20."),
            Q("Binary search on the answer needs the yes/no check to be monotone.", ["True", "False"], 0, "Without monotonicity, discarding half the range is not justified.", "true_false")]),
  ]),
]),

C("system-design-basics", "System Design Basics",
  "Learn how real systems are shaped: requirements, scaling, load balancing, caching and data storage, with the trade-offs that interviewers expect you to discuss.",
  "system-design", "intermediate", "premium", 280, [
  ("Thinking in systems", [
    L("start-with-requirements", "Start with requirements and numbers", "free", 12,
      "## Don't draw boxes yet\n\nEvery good design starts with questions.\n\n1. **Functional requirements** — what must the system do? (post a message, read a feed)\n2. **Non-functional requirements** — latency, availability, consistency, durability, cost.\n3. **Scale** — users, requests per second, data size, read/write ratio.\n\n### Back-of-the-envelope maths\nOne million daily active users each making 10 requests per day is about 10 million requests per day ≈ **115 requests per second** on average (10,000,000 / 86,400). Peaks are often 3–10× the average. Numbers like these decide whether you need one server or a fleet.",
      tip="State your assumptions out loud. A reasonable assumption you can defend beats a perfect one you never mention.",
      quiz=[Q("1 million requests spread evenly over one day is about how many requests per second?", ["12", "120", "1,200", "12,000"], 0, "1,000,000 / 86,400 ≈ 11.6."),
            Q("Which of these is a non-functional requirement?", ["Users can upload a photo", "The feed loads within 300 ms for most users", "Users can follow each other", "Users can delete a post"], 1, "Latency targets describe *how well* the system works, not *what* it does.")]),
    L("scaling-and-load-balancing", "Scaling out and load balancing", "premium", 16,
      "## Vertical vs horizontal\n\n**Vertical scaling** means a bigger machine; it is simple but has a ceiling and is a single point of failure. **Horizontal scaling** means more machines behind a **load balancer**, which spreads requests and removes unhealthy servers.\n\nCommon balancing strategies: round robin, least connections, and consistent hashing (when requests for the same key should reach the same server, e.g. a cache shard).\n\n### Keep servers stateless\nIf a server remembers a user's session in memory, that user must always return to it. Store sessions in a shared store (a database or cache) so **any** server can serve **any** request.",
      quiz=[Q("Why do we prefer stateless application servers?", ["They use less CPU", "Any server can handle any request, so we can add or remove servers freely", "They never need a database", "They are always faster"], 1, "Statelessness is what makes horizontal scaling and failover easy."),
            Q("A load balancer also helps availability by sending traffic only to healthy servers.", ["True", "False"], 0, "Health checks remove failed servers from rotation.", "true_false")]),
    L("caching-strategies", "Caching: where, what and when to evict", "premium", 18,
      "## Make the common case cheap\n\nA **cache** keeps recent or popular results in fast memory so you avoid slow work (database queries, remote calls).\n\n### Where to cache\nBrowser → CDN (static files near the user) → application memory → a shared cache such as Redis → the database's own buffers.\n\n### Patterns\n- **Cache-aside:** read the cache; on a miss, read the database and fill the cache.\n- **Write-through:** write to cache and database together.\n- **Write-back:** write to cache first and flush later (fast, but data can be lost).\n\n### Hard parts\nA cache can be **stale**; set a time-to-live (TTL) or invalidate on writes. Evict with LRU (least recently used) when full. Beware a **cache stampede**: many requests missing at once and hammering the database.",
      code=("python", "def get_user(user_id, cache, db):\n    user = cache.get(user_id)\n    if user is None:                  # miss\n        user = db.load_user(user_id)\n        cache.set(user_id, user, ttl_seconds=300)\n    return user", "Cache-aside"),
      quiz=[Q("In cache-aside, what happens on a cache miss?", ["The request fails", "The app loads from the database and stores the result in the cache", "The cache asks the user", "The cache is cleared"], 1, "The application fills the cache itself."),
            Q("Which policy evicts the item that has gone unused the longest?", ["FIFO", "LRU", "Random", "LIFO"], 1, "LRU = least recently used.")]),
  ]),
]),

C("python-for-engineers", "Python for Engineers",
  "A practical introduction to Python: core types, functions, comprehensions, errors and files, written for people who want to build and automate.",
  "python", "beginner", "free", 240, [
  ("Core Python", [
    L("types-and-control-flow", "Types, variables and control flow", "free", 14,
      "## The building blocks\n\nPython is dynamically typed: a variable is a name bound to a value, and values carry their type.\n\n| Type | Example | Mutable? |\n|---|---|---|\n| int, float, bool | `42`, `3.14`, `True` | no |\n| str | `\"hello\"` | no |\n| list | `[1, 2, 3]` | yes |\n| tuple | `(1, 2)` | no |\n| dict | `{\"a\": 1}` | yes |\n| set | `{1, 2}` | yes |\n\nIndentation defines blocks. `if/elif/else`, `for` and `while` work as you would expect; `for` iterates over any iterable.",
      code=("python", "scores = {\"asha\": 82, \"ben\": 67, \"chen\": 91}\nfor name, score in scores.items():\n    if score >= 80:\n        grade = \"A\"\n    elif score >= 70:\n        grade = \"B\"\n    else:\n        grade = \"C\"\n    print(f\"{name}: {grade}\")", "A dict, a loop and a conditional"),
      quiz=[Q("Which of these types is immutable?", ["list", "dict", "tuple", "set"], 2, "Tuples cannot be changed after creation."),
            Q("What defines a code block in Python?", ["Curly braces", "Indentation", "begin/end keywords", "Semicolons"], 1, "Consistent indentation is part of the syntax.")]),
    L("functions-and-comprehensions", "Functions and comprehensions", "free", 15,
      "## Reusable logic\n\nA function groups steps behind a name. Give parameters **defaults**, call with **keyword arguments** for clarity, and return values rather than printing them.\n\nA **comprehension** builds a collection in one readable expression:\n\n`[x * x for x in nums if x % 2 == 0]` — the squares of the even numbers.\n\n### A classic trap\nNever use a mutable default such as `def add(item, bucket=[])`: the same list is shared between calls. Use `None` and create the list inside.",
      code=("python", "def add_item(item, bucket=None):\n    if bucket is None:\n        bucket = []\n    bucket.append(item)\n    return bucket\n\nsquares = {n: n * n for n in range(5)}   # dict comprehension\nprint(squares)                           # {0: 0, 1: 1, 2: 4, 3: 9, 4: 16}", "Safe defaults and a dict comprehension"),
      quiz=[Q("Why is `def f(x, items=[])` risky?", ["It is a syntax error", "The default list is created once and shared by every call", "Lists cannot be defaults", "It is slower"], 1, "Defaults are evaluated once, when the function is defined."),
            Q("`[x for x in range(6) if x % 3 == 0]` evaluates to:", ["[0, 3]", "[3, 6]", "[0, 3, 6]", "[1, 2]"], 0, "range(6) is 0..5; multiples of 3 are 0 and 3.")]),
    L("errors-and-files", "Errors, files and context managers", "free", 14,
      "## Failing gracefully\n\nUse `try/except` for failures you can handle, and let others bubble up. Catch **specific** exceptions, never a bare `except:`.\n\nThe `with` statement is a **context manager**: it guarantees cleanup (closing a file) even when an error happens.",
      code=("python", "from pathlib import Path\n\ndef count_lines(path):\n    try:\n        with Path(path).open(encoding=\"utf-8\") as f:\n            return sum(1 for _ in f)\n    except FileNotFoundError:\n        return 0", "Reading a file safely"),
      quiz=[Q("What does `with open(...) as f:` guarantee?", ["The file is read faster", "The file is closed when the block ends, even after an error", "The file is created if missing", "The file is locked forever"], 1, "Context managers run cleanup in all cases.")]),
  ]),
]),

C("sql-and-databases", "SQL and Databases",
  "Query data confidently: selecting, joining and aggregating, and understand indexes and transactions the way production systems use them.",
  "databases", "beginner", "free", 240, [
  ("Querying", [
    L("select-filter-join", "SELECT, WHERE and JOIN", "free", 16,
      "## Asking questions of tables\n\nRelational databases store data in **tables** of rows and columns. `SELECT` chooses columns, `WHERE` filters rows, `ORDER BY` sorts.\n\nA **JOIN** combines rows from two tables using a matching column. `INNER JOIN` keeps only matches; `LEFT JOIN` keeps every left-hand row even without a match (the right-hand columns are `NULL`).\n\n`NULL` means *unknown*, so `x = NULL` is never true. Use `IS NULL`.",
      code=("sql", "SELECT c.name, COUNT(o.id) AS orders\nFROM customers c\nLEFT JOIN orders o ON o.customer_id = c.id\nWHERE c.country = 'IN'\nGROUP BY c.name\nORDER BY orders DESC;", "Customers and how many orders each placed (including zero)"),
      quiz=[Q("Which join keeps customers that have no orders?", ["INNER JOIN", "LEFT JOIN from customers to orders", "CROSS JOIN only", "None"], 1, "A LEFT JOIN keeps every row of the left table."),
            Q("How do you test for a missing value?", ["= NULL", "== NULL", "IS NULL", "EQUALS NULL"], 2, "Comparisons with NULL are never true; IS NULL is the test.")]),
    L("aggregation-and-grouping", "Aggregation, GROUP BY and HAVING", "free", 14,
      "## Summaries\n\nAggregate functions — `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` — collapse many rows into one value. `GROUP BY` makes one result row per group.\n\n`WHERE` filters rows **before** grouping; `HAVING` filters groups **after**. Every selected column must either be in `GROUP BY` or be wrapped in an aggregate.",
      code=("sql", "SELECT country, COUNT(*) AS customers\nFROM customers\nGROUP BY country\nHAVING COUNT(*) >= 100\nORDER BY customers DESC;", "Countries with at least 100 customers"),
      quiz=[Q("Which clause filters groups after aggregation?", ["WHERE", "HAVING", "ORDER BY", "LIMIT"], 1, "WHERE runs before grouping; HAVING runs after.")]),
  ]),
  ("Under the hood", [
    L("indexes-and-transactions", "Indexes and transactions", "free", 18,
      "## Speed and safety\n\nAn **index** is a sorted structure (usually a B-tree) that lets the database find rows without scanning the whole table. Indexes speed up reads on the indexed columns but cost extra space and slow down writes, so index what you filter and join on, not everything.\n\nA **transaction** groups statements so they succeed or fail together. The ACID properties: **A**tomicity (all or nothing), **C**onsistency (rules always hold), **I**solation (concurrent work does not interfere in surprising ways) and **D**urability (committed data survives a crash).",
      code=("sql", "CREATE INDEX idx_orders_customer ON orders (customer_id);\n\nBEGIN;\nUPDATE accounts SET balance = balance - 500 WHERE id = 1;\nUPDATE accounts SET balance = balance + 500 WHERE id = 2;\nCOMMIT;", "An index, and a transfer that must not half-happen"),
      quiz=[Q("What is the main cost of adding an index?", ["Reads become slower", "Extra storage and slower writes", "Data is lost", "Queries stop working"], 1, "The index must be kept in sync on every insert, update and delete."),
            Q("In ACID, what does Atomicity mean?", ["Data is encrypted", "A transaction's statements all happen or none do", "Queries are fast", "Tables are normalised"], 1, "All or nothing.")]),
  ]),
]),

C("git-and-collaboration", "Git and Collaboration",
  "Version control from first commit to pull request: commits, branches, merging, rebasing and the habits that keep a team's history clean.",
  "git", "beginner", "free", 150, [
  ("Everyday Git", [
    L("commits-and-history", "Commits, staging and history", "free", 12,
      "## Snapshots, not diffs\n\nGit records **commits**: snapshots of your project with an author, a message and a pointer to the previous commit. You choose what goes in the next commit by **staging** files.\n\nWrite messages that explain *why*: `fix: stop double-charging on retry` beats `changes`.",
      code=("bash", "git init\ngit add README.md src/\ngit commit -m \"feat: add project skeleton\"\ngit log --oneline --graph", "The core loop"),
      quiz=[Q("What does `git add` do?", ["Creates a commit", "Stages changes for the next commit", "Pushes to the server", "Deletes files"], 1, "Staging selects what the next commit will contain.")]),
    L("branches-and-pull-requests", "Branches, merging and pull requests", "free", 14,
      "## Work in parallel\n\nA **branch** is a lightweight movable pointer to a commit. Create one per change so `main` stays releasable.\n\n- **Merge** joins histories with a merge commit (or fast-forwards).\n- **Rebase** replays your commits on top of another branch for a linear history. Never rebase commits others already pulled.\n\nA **pull request** asks teammates to review a branch before it merges. Keep them small and describe the intent.",
      code=("bash", "git switch -c feature/search\n# ...edit and commit...\ngit fetch origin\ngit rebase origin/main\ngit push -u origin feature/search", "A feature branch kept up to date"),
      tip="Resolve conflicts by reading both sides, not by always taking yours.",
      quiz=[Q("Which is safe to rebase?", ["Commits already shared and pulled by teammates", "Your own local commits not yet pushed", "Anything on main", "A published tag"], 1, "Rebasing rewrites history; only do it on commits nobody else has."),
            Q("Why keep pull requests small?", ["Git requires it", "They are easier to review and less risky to merge", "They run faster", "Branches are limited in size"], 1, "Small changes get better reviews.")]),
  ]),
]),

C("web-apis-with-rest", "Web APIs with REST",
  "Understand HTTP, design clean REST endpoints and handle authentication and errors the way modern backends do.",
  "web-apis", "intermediate", "free", 200, [
  ("HTTP and REST", [
    L("http-in-practice", "HTTP in practice", "free", 14,
      "## The language of the web\n\nHTTP is request/response. A request has a **method**, a **URL**, **headers** and an optional **body**; the response has a **status code**, headers and a body.\n\n| Method | Meaning | Safe? | Idempotent? |\n|---|---|---|---|\n| GET | read | yes | yes |\n| POST | create / action | no | no |\n| PUT | replace | no | yes |\n| PATCH | partial update | no | no* |\n| DELETE | remove | no | yes |\n\nStatus classes: **2xx** success, **3xx** redirect, **4xx** client mistake (400 bad input, 401 unauthenticated, 403 forbidden, 404 not found), **5xx** server fault.",
      quiz=[Q("Which status means the caller is logged in but not allowed to do this?", ["401", "403", "404", "500"], 1, "403 Forbidden: authenticated, not authorised."),
            Q("An idempotent request can be repeated safely with the same effect as sending it once.", ["True", "False"], 0, "That is the definition, and why PUT and DELETE are safe to retry.", "true_false")]),
    L("designing-rest-endpoints", "Designing REST endpoints", "free", 16,
      "## Nouns in URLs, verbs in methods\n\nModel **resources**: `/courses`, `/courses/42`, `/courses/42/lessons`. Use plural nouns, let the method express the action, return JSON, and use consistent errors.\n\n### Good habits\n- **Pagination** for lists (`?page=2&size=20`), never unbounded results.\n- **Validation** on every input; reject unknown fields.\n- **Versioning** (`/api/v1`) so you can evolve without breaking clients.\n- **Idempotency keys** for payment-like POSTs so a retry cannot double-charge.\n- Never expose internal errors; return a stable, documented error shape.",
      code=("http", "POST /api/v1/courses/42/enrollments\nContent-Type: application/json\n\n{}\n\n-> 201 Created\nLocation: /api/v1/courses/42/enrollments/me", "Creating a sub-resource"),
      quiz=[Q("Which URL follows REST conventions for listing lessons of course 42?", ["GET /getLessons?course=42", "GET /courses/42/lessons", "POST /courses/lessons/42", "GET /lesson_list/42"], 1, "A plural noun path with the GET method."),
            Q("Why add an idempotency key to a payment POST?", ["To make it faster", "So a retried request cannot charge twice", "To encrypt it", "To cache it"], 1, "The server recognises the repeat and returns the original result.")]),
    L("api-authentication", "Authentication and authorisation for APIs", "free", 16,
      "## Who are you, and what may you do?\n\n**Authentication** proves identity; **authorisation** decides permissions. Never trust the client for either.\n\nModern apps delegate login to an identity provider using **OAuth 2.0 / OpenID Connect** and receive a signed **token** (often a JWT). The API verifies the signature, expiry, issuer and audience, then checks permissions **on the server** for every request.\n\nStore secrets in a server-side vault or environment, never in front-end code, and send tokens only over HTTPS.",
      tip="A token proves who someone is. It does not replace checking that *this* user may touch *this* record.",
      quiz=[Q("Where must permission checks happen?", ["Only in the browser", "On the server, on every request", "Only at login", "In the database schema only"], 1, "Client code can be bypassed; the server is the authority."),
            Q("Which of these should an API verify on a JWT?", ["Signature, expiry, issuer and audience", "Only that it is non-empty", "Only its length", "Nothing; it is signed by the client"], 0, "All of these checks prevent forged or misdirected tokens.")]),
  ]),
]),
]
