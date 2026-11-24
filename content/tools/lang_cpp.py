from courses_a import Q, L
M = lambda t: ("md", t)
C = lambda code, cap: ("code", "cpp", code, cap)

CPP = L("cpp-for-dsa", "C++ for DSA", "free", 30, "", blocks=[
M("""## Why C++ for interviews?

C++ is a **compiled, statically typed** language with direct control over memory and very fast execution. Competitive programmers favour it because the **Standard Template Library (STL)** gives containers and algorithms (`vector`, `map`, `unordered_map`, `priority_queue`, `sort`, `lower_bound`) with predictable performance.

| Strengths | Weaknesses |
|---|---|
| Fastest of the common interview languages | Easy to write undefined behaviour (out-of-range, uninitialised values) |
| Powerful STL algorithms | Verbose error messages; more syntax to remember |
| Value semantics, no hidden boxing | Manual care with pointers and lifetimes |
| Great for system-level and performance questions | Integer overflow is undefined for signed types |

The programs below use **C++17** and explicit headers; every one was compiled and run before this lesson was published.

## 1. Program skeleton and fast input

`main` returns `int`. Include the headers you use. With plain `cin/cout`, turn off synchronisation with C stdio for speed.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    int n, k;
    cin >> n >> k;
    vector<long long> a(n);
    long long sum = 0;
    for (auto &x : a) { cin >> x; sum += x; }

    cout << "n=" << n << " k=" << k << " sum=" << sum << "\\n";   // '\\n' is faster than endl
    return 0;
}""", "Fast input with cin and a vector"),
M("""`#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>` pulls in the whole STL on GCC and Clang; it is fine for practice and interviews that allow it. In production code include only what you need.

## 2. Types and overflow

| Type | Typical size | Notes |
|---|---|---|
| `int` | 32 bits | about ±2.1 × 10⁹; **signed overflow is undefined behaviour** |
| `long long` | 64 bits | use for sums, products, large counts |
| `unsigned` types | same width | wrap around predictably; mixing signed and unsigned is a bug source |
| `double` | 64 bits | not exact |
| `char` | 8 bits | a small integer |
| `bool` | 1 byte | `true` / `false` |
| `size_t` | 64 bits on most systems | what `.size()` returns (unsigned!) |

`v.size() - 1` on an **empty** vector wraps to a huge number because `size()` is unsigned. Cast: `(int)v.size() - 1`.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

int main() {
    vector<int> v;
    cout << (int)v.size() - 1 << "\\n";          // -1 (safe)
    cout << v.size() - 1 << "\\n";               // huge number (wraps)

    long long big = 1LL * 1000000 * 1000000;     // multiply in 64-bit: 10^12
    cout << big << "\\n";

    cout << 7 / 2 << " " << -7 / 2 << " " << -7 % 3 << "\\n";   // 3 -3 -1 (truncates toward zero)
    int m = ((-7 % 3) + 3) % 3;                  // non-negative modulo
    cout << m << "\\n";                          // 2

    const long long MOD = 1000000007;
    long long x = 123456789LL * 987654321LL % MOD;
    cout << x << "\\n";
    cout << INT_MAX << " " << LLONG_MAX << "\\n";
    return 0;
}""", "size_t wrap-around, 64-bit math and modulo"),
M("""## 3. Control flow, functions and references

`if`, `switch`, `for`, range-based `for (auto x : v)`, `while`, `break`, `continue`. Functions take arguments **by value** (a copy) unless you write `&`: `void f(vector<int>& v)` modifies the caller's vector; `const vector<int>& v` avoids copying while forbidding changes. Passing a large vector by value is a classic silent slowdown. `auto` deduces the type.

## 4. Strings

`std::string` is **mutable** (unlike Java and Python), and `s[i]` is a character you can assign. Concatenate with `+=` (amortised O(1) per character). Substring is `s.substr(pos, len)` (copies).

| Task | Code |
|---|---|
| Length | `s.size()` |
| Substring | `s.substr(a, len)` |
| Search | `s.find("ab")` returns `string::npos` if absent |
| Reverse | `reverse(s.begin(), s.end())` |
| Number to string / back | `to_string(12)`, `stoi("12")`, `stoll` |
| Letter index | `c - 'a'` |
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

bool isPalindrome(const string& s) {
    int i = 0, j = (int)s.size() - 1;
    while (i < j) if (s[i++] != s[j--]) return false;
    return true;
}

int main() {
    cout << boolalpha << isPalindrome("level") << "\\n";   // true

    string t = "hello";
    t[0] = 'J';                                            // strings are mutable
    reverse(t.begin(), t.end());
    cout << t << "\\n";                                    // olleJ

    int cnt[26] = {0};                                     // letter frequencies
    for (char c : string("banana")) cnt[c - 'a']++;
    cout << cnt[0] << " " << cnt['n' - 'a'] << "\\n";      // 3 2

    cout << string("abcabc").find("ca") << "\\n";                   // 2
    return 0;
}""", "Strings and a counting array"),
M("""## 5. Arrays and vectors

Fixed C arrays (`int a[100];`) are uninitialised unless you write `= {0}`. Prefer `std::vector<T>`, a dynamic array with `push_back` (amortised O(1)), `pop_back`, `back()`, `size()`, `resize`, `clear`, `insert` (O(n)). 2-D: `vector<vector<int>> g(r, vector<int>(c, 0));`. Iterators let algorithms work on any range: `v.begin()`, `v.end()`.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

int main() {
    vector<int> a = {5, 2, 9, 1};
    sort(a.begin(), a.end());
    for (int x : a) cout << x << " ";                       // 1 2 5 9
    cout << "\\n";

    vector<vector<int>> grid(3, vector<int>(4, -1));
    grid[1][2] = 7;
    cout << grid[1][2] << " " << grid[0][0] << "\\n";        // 7 -1

    a.erase(a.begin() + 1);                                  // remove index 1 (O(n))
    a.insert(a.begin(), 100);                                // insert at front (O(n))
    cout << a.size() << " " << a.front() << " " << a.back() << "\\n";

    vector<pair<int, string>> ps = {{3, "c"}, {1, "a"}, {2, "b"}};
    sort(ps.begin(), ps.end());                              // pairs sort lexicographically
    cout << ps[0].first << ps[0].second << "\\n";

    cout << accumulate(a.begin(), a.end(), 0LL) << " " << *max_element(a.begin(), a.end()) << "\\n";
    return 0;
}""", "vector, 2-D vector, erase/insert, pairs and numeric helpers"),
M("""## 6. Maps and sets

| Structure | Header | Cost | Notes |
|---|---|---|---|
| `unordered_map<K,V>` / `unordered_set<T>` | `<unordered_map>` | O(1) average | hash-based, no order |
| `map<K,V>` / `set<T>` | `<map>`, `<set>` | O(log n) | sorted; `lower_bound`, `upper_bound`; `begin()` is the smallest |
| `multiset<T>` / `multimap` | | O(log n) | duplicates allowed |

`m[key]` **inserts** a default value if the key is missing, which is handy for counting (`cnt[x]++`) but a bug when you only wanted to check; use `m.count(key)` or `m.find(key) != m.end()`.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

int main() {
    unordered_map<char, int> freq;
    for (char c : string("mississippi")) freq[c]++;
    cout << freq['s'] << " " << freq['i'] << "\\n";           // 4 4

    vector<int> nums = {2, 7, 11, 15};
    int target = 9;
    unordered_map<int, int> seen;                            // value -> index
    for (int i = 0; i < (int)nums.size(); i++) {
        auto it = seen.find(target - nums[i]);
        if (it != seen.end()) { cout << it->second << " " << i << "\\n"; break; }
        seen[nums[i]] = i;
    }

    map<int, vector<string>> byLen;
    for (string w : {"to", "be", "or", "not"}) byLen[(int)w.size()].push_back(w);
    for (auto& [len, words] : byLen) cout << len << ":" << words.size() << " ";   // 2:3 3:1
    cout << "\\n";

    set<int> s = {10, 20, 30};
    auto it = s.lower_bound(25);                             // first element >= 25
    cout << *it << " " << *prev(it) << "\\n";                 // 30 20
    return 0;
}""", "Counting, two-sum, ordered map and set boundaries"),
M("""## 7. Stack, queue, deque and priority queue

| Need | Use | Key methods |
|---|---|---|
| Stack | `stack<T>` (or a `vector` with `push_back`/`pop_back`) | `push`, `top`, `pop`, `empty` |
| Queue | `queue<T>` | `push`, `front`, `pop` |
| Deque | `deque<T>` | `push_front`, `push_back`, `pop_front`, `pop_back`, random access |
| Max-heap | `priority_queue<T>` | `push`, `top`, `pop` (default **max** at top) |
| Min-heap | `priority_queue<T, vector<T>, greater<T>>` | same |

`pop()` returns nothing: read `top()` or `front()` first.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

int main() {
    stack<int> st; st.push(1); st.push(2);
    cout << st.top() << "\\n"; st.pop();                      // 2

    queue<int> q; q.push(1); q.push(2);
    cout << q.front() << "\\n"; q.pop();                      // 1

    priority_queue<int> maxHeap;                             // max at top by default
    for (int x : {3, 9, 4}) maxHeap.push(x);
    cout << maxHeap.top() << "\\n";                           // 9

    priority_queue<int, vector<int>, greater<int>> minHeap;
    for (int x : {3, 9, 4}) minHeap.push(x);
    cout << minHeap.top() << "\\n";                           // 3

    priority_queue<pair<int, int>, vector<pair<int, int>>, greater<>> pq;   // (distance, node)
    pq.push({5, 1}); pq.push({2, 7});
    cout << pq.top().second << "\\n";                         // 7

    deque<int> dq = {1, 2, 3};
    dq.push_front(0); dq.pop_back();
    cout << dq.front() << " " << dq.back() << "\\n";          // 0 2
    return 0;
}""", "Stack, queue, heaps, Dijkstra-style pair heap and deque"),
M("""## 8. Sorting, comparators and binary search

`sort(begin, end)` is O(n log n) (introsort, not stable; use `stable_sort` when order of equals matters). Give a comparator as a **lambda**: `sort(v.begin(), v.end(), [](const auto& a, const auto& b){ return a[0] < b[0]; })`. A comparator must be a **strict weak ordering**: use `<`, never `<=`, or you get undefined behaviour.

On a **sorted** range: `lower_bound(first, last, x)` is the first element `>= x`, `upper_bound` the first `> x`, `binary_search` returns a bool. Distances: `lower_bound(...) - v.begin()` is an index.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

struct Person { string name; int age; };

int main() {
    vector<Person> people = {{"Asha", 30}, {"Ben", 25}, {"Chen", 30}};
    sort(people.begin(), people.end(), [](const Person& a, const Person& b) {
        if (a.age != b.age) return a.age > b.age;           // older first
        return a.name < b.name;                             // then by name
    });
    for (auto& p : people) cout << p.name << " ";            // Asha Chen Ben
    cout << "\\n";

    vector<int> v = {1, 3, 5, 5, 7, 9};
    cout << (lower_bound(v.begin(), v.end(), 5) - v.begin()) << " "      // 2
         << (upper_bound(v.begin(), v.end(), 5) - v.begin()) << "\\n";   // 4

    vector<vector<int>> intervals = {{5, 8}, {1, 3}, {2, 6}};
    sort(intervals.begin(), intervals.end());                // sorts by first, then second
    cout << intervals[0][0] << intervals[1][0] << intervals[2][0] << "\\n";   // 125
    return 0;
}""", "Lambda comparators, lower_bound and upper_bound"),
M("""## 9. Structs, classes and pointers

Use `struct` for nodes. Allocate with `new` and free with `delete`, or use smart pointers (`unique_ptr`, `make_unique`) in real code. Interview problems usually hand you `ListNode*` and `TreeNode*`. **Always check for `nullptr`** before dereferencing.
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

struct ListNode { int val; ListNode* next; ListNode(int v) : val(v), next(nullptr) {} };
struct TreeNode { int val; TreeNode *left, *right; TreeNode(int v) : val(v), left(nullptr), right(nullptr) {} };

ListNode* reverseList(ListNode* head) {
    ListNode* prev = nullptr;
    while (head) {
        ListNode* nxt = head->next;
        head->next = prev;
        prev = head;
        head = nxt;
    }
    return prev;
}

int height(TreeNode* t) { return t ? 1 + max(height(t->left), height(t->right)) : 0; }

int main() {
    ListNode* h = new ListNode(1); h->next = new ListNode(2); h->next->next = new ListNode(3);
    h = reverseList(h);
    for (ListNode* p = h; p; p = p->next) cout << p->val << " ";   // 3 2 1
    cout << "\\n";

    TreeNode* root = new TreeNode(1); root->left = new TreeNode(2); root->left->left = new TreeNode(3);
    cout << height(root) << "\\n";                                  // 3
    return 0;                                                       // (leaks are fine in a short demo)
}""", "Linked list reversal and tree height with raw pointers"),
M("""## 10. Complexity cheat sheet (STL)

| Operation | vector | deque | list | map/set | unordered_map/set | priority_queue |
|---|---|---|---|---|---|---|
| Access `[i]` | O(1) | O(1) | - | - | - | - |
| Push back | O(1)* | O(1) | O(1) | O(log n) | O(1)* | O(log n) |
| Insert/erase middle | O(n) | O(n) | O(1) at iterator | O(log n) | O(1)* | - |
| Find | O(n) | O(n) | O(n) | O(log n) | O(1)* | - |
| Min/max | O(n) | O(n) | O(n) | O(log n) | O(n) | O(1) top |

\\* amortised / average. Worst-case unordered containers can degrade to O(n) with adversarial keys.

## 11. Pitfalls and idioms checklist

- `vector::size()` is unsigned; cast before subtracting.
- `m[key]` inserts a default; use `find` / `count` to only test.
- Pass big containers by `const&`; pass by value only when you need a copy.
- Out-of-range `v[i]` is **undefined behaviour** (no exception); use `v.at(i)` while debugging.
- Uninitialised local variables hold garbage; initialise everything.
- `int` overflow is undefined; use `long long` early.
- Modulo of negatives is negative: normalise with `((a % m) + m) % m`.
- Comparators must use strict `<`.
- Erasing from a vector while iterating invalidates iterators; use the erase-remove idiom or index carefully.
- Divide integers as `a / (double)b` when you need a fraction.
- Recursion depth is limited by the stack (around 10⁵ frames); convert very deep recursion.

## 12. A reusable solution template
"""),
C("""#include <algorithm>
#include <climits>
#include <deque>
#include <iostream>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
using namespace std;

pair<int, int> twoSum(const vector<int>& a, int target) {
    unordered_map<int, int> seen;
    for (int j = 0; j < (int)a.size(); j++) {
        auto it = seen.find(target - a[j]);
        if (it != seen.end()) return {it->second + 1, j + 1};
        seen.emplace(a[j], j);                       // keeps the first index for duplicates
    }
    return {-1, -1};
}

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int n, t;
    cin >> n >> t;
    vector<int> a(n);
    for (int& x : a) cin >> x;
    auto r = twoSum(a, t);
    if (r.first == -1) cout << -1 << "\\n";
    else cout << r.first << " " << r.second << "\\n";
}""", "Template: parse, call a pure function, print"),
M("""## 13. What to practise next

1. Re-type each program; then deliberately break it (index out of range, forgotten `&`) to see the effect.
2. Solve [Pair With Target Sum](/problems/pair-with-target-sum), [Kth Largest](/problems/kth-largest) and [Cheapest Route](/problems/cheapest-route) in C++.
3. Memorise `sort`, `lower_bound`, `priority_queue`, `unordered_map`, `accumulate` and `reverse`: they cover most interview needs.
"""),
], quiz=[
 Q("`v.size() - 1` on an empty vector gives:", ["A huge number (unsigned wrap)", "-1", "0", "An exception"], 0, "size() is unsigned; cast to int first."),
 Q("What does `m[key]` do on a missing key of an unordered_map?", ["Inserts a default value", "Throws", "Returns null", "Nothing"], 0, "Use find or count to only test."),
 Q("By default `priority_queue<int>` keeps at the top:", ["The maximum", "The minimum", "The first inserted", "The last inserted"], 0, "Use greater<int> for a min-heap."),
 Q("Why must a sort comparator use `<` rather than `<=`?", ["It must be a strict weak ordering", "`<=` is slower", "`<=` does not compile", "No reason"], 0, "Violating it can crash or loop."),
 Q("`lower_bound(v.begin(), v.end(), x)` returns:", ["The first element >= x (sorted input)", "The last element <= x", "Always begin()", "A boolean"], 0, "upper_bound returns the first element > x."),
])
