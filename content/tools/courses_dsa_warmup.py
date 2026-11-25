"""The DSA Warmup chapter: eight lessons mirroring the groups of the 65 warm-up exercises (original text)."""
from courses_a import Q, L

M = lambda t: ("md", t)
C = lambda code, cap: ("code", "python", code, cap)

def practice(*items):
    return "\n\n### Practice problems\nSolve these in order. Try each one without hints first, then check the hints.\n\n" + "\n".join(f"{n}. [{t}](/problems/{s})" for n, (s, t) in enumerate(items, 1))

V = [("swap-two-numbers", "Swap Two Numbers"), ("temperature-converter", "Temperature Converter"), ("simple-and-compound-interest", "Simple Interest and Compound Interest"), ("seconds-to-hms", "Convert Seconds to Hours, Minutes, Seconds"), ("absolute-value", "Absolute Value Without Built-in"), ("quotient-and-remainder", "Quotient and Remainder of a Division"), ("area-and-perimeter", "Area and Perimeter of Shapes")]
CO = [("even-or-odd", "Even or Odd"), ("positive-negative-or-zero", "Positive, Negative, or Zero"), ("leap-year-check", "Leap Year Check"), ("grade-from-marks", "Grade from Marks"), ("valid-triangle", "Valid Triangle from Three Sides"), ("vowel-or-consonant", "Vowel or Consonant"), ("collinear-points", "Check if Three Points are Collinear")]
LO = [("print-1-to-n", "Print Numbers from 1 to N"), ("multiplication-table", "Multiplication Table of a Number"), ("sum-of-even-or-odd", "Sum of Even or Odd Numbers"), ("count-digits", "Count Digits in an Integer"), ("sum-of-divisors", "Sum of All Divisors of a Number")]
PA = [("right-angled-triangle", "Right-Angled Triangle of Stars"), ("inverted-right-angled-triangle", "Inverted Right-Angled Triangle"), ("pyramid-of-stars", "Pyramid of Stars"), ("inverted-pyramid", "Inverted Pyramid"), ("hollow-square", "Hollow Square"), ("hollow-triangle", "Hollow Triangle"), ("diamond-pattern", "Diamond Pattern"), ("floyds-triangle", "Floyd's Triangle"), ("mirror-number-pattern", "Mirror Number Pattern"), ("butterfly-pattern", "Butterfly Pattern"), ("hourglass-pattern", "Hourglass Pattern"), ("spiral-number-pattern", "Spiral Number Pattern")]
NT = [("prime-number-check", "Prime Number Check"), ("print-primes-up-to-n", "Print All Primes Up to N"), ("factorial", "Factorial of a Number"), ("print-fibonacci-terms", "Print Fibonacci Up to N Terms"), ("gcd-of-two-numbers", "GCD of Two Numbers"), ("lcm-of-two-numbers", "LCM of Two Numbers"), ("perfect-number-check", "Perfect Number Check"), ("decimal-to-binary", "Decimal to Binary")]
AR = [("sum-of-array", "Sum of All Elements"), ("average-of-array", "Average of Array Elements"), ("largest-element", "Largest Element in an Array"), ("second-largest-element", "Second Largest Element"), ("count-even-and-odd", "Count Even and Odd Numbers"), ("linear-search", "Linear Search for a Target"), ("reverse-an-array", "Reverse an Array In Place"), ("is-array-sorted", "Check if an Array is Sorted"), ("count-frequency", "Count Frequency of Each Element")]
ST = [("length-of-string", "Length of a String"), ("count-vowels-and-consonants", "Count Vowels and Consonants"), ("count-words", "Count Words in a Sentence"), ("palindrome-string-check", "Palindrome String Check"), ("count-occurrence-of-character", "Count Occurrence of a Character"), ("replace-spaces", "Replace All Spaces with a Character"), ("toggle-case", "Toggle Case of Every Character"), ("longest-word", "Find the Longest Word in a Sentence")]
RE = [("sum-of-first-n-recursive", "Sum of First N Natural Numbers (Recursive)"), ("factorial-recursive", "Factorial (Recursive)"), ("fibonacci-recursive", "Nth Fibonacci Number (Recursive)"), ("power-recursive", "Power of a Number (Recursive)"), ("reverse-string-recursive", "Reverse a String (Recursive)"), ("sum-of-digits-recursive", "Sum of Digits (Recursive)"), ("print-1-to-n-and-back", "Print Numbers 1 to N and N to 1 (Recursive)"), ("count-digits-recursive", "Count Digits (Recursive)"), ("gcd-recursive", "GCD Using Recursion")]

L_VARS = L("variables-io-operators", "Variables, I/O & Operators", "free", 18, "", blocks=[
M("""## Warm up your hands before the algorithms

Every algorithm in this course is built from a few tiny skills: store a value, read input, compute something, print the answer. This chapter makes those skills automatic. The exercises are small on purpose; the goal is **speed and zero hesitation**, so that later your attention can go to the idea instead of the syntax.

## Variables and types

A **variable** is a name for a stored value. A value has a **type**: a whole number (integer), a number with a fraction (float), text (string), or true/false (boolean). In statically typed languages you declare the type (`int x = 5;`); in Python you just assign (`x = 5`). Before you write code, ask: *can this number get big?* If it can exceed about two billion, use a 64-bit integer (`long` in Java, `long long` in C++, `int64` in Go); Python integers never overflow.

## Reading input and printing output

Practice problems read **standard input** and print to **standard output**, and the output must match **exactly** (spaces, newlines, decimals). Read tokens with `input().split()` in Python; convert text to numbers with `int(...)`. Print several values separated by spaces with `print(a, b)`.

## Operators you need

| Operator | Meaning | Example |
|---|---|---|
| `+ - *` | add, subtract, multiply | `3 * 4 == 12` |
| `/` | division (a float in Python) | `7 / 2 == 3.5` |
| `//` | integer (floor) division | `7 // 2 == 3` |
| `%` | remainder | `7 % 2 == 1` |
| `**` | power | `2 ** 10 == 1024` |
| `abs(x)` | absolute value | `abs(-4) == 4` |

**Two ideas appear everywhere.** First, `a = b * q + r` where `q = a // b` and `r = a % b`: this connects division and remainder. Second, **swapping** two variables needs a temporary (or Python's tuple assignment `a, b = b, a`).
"""),
C("""a, b = map(int, "3 8".split())          # in a real solution: input().split()
a, b = b, a                              # swap without a temporary
print(a, b)                              # 8 3

secs = 3725
h, rest = divmod(secs, 3600)             # quotient and remainder in one call
m, s = divmod(rest, 60)
print(h, m, s)                           # 1 2 5

c = 100
tenths = c * 18 + 320                    # F * 10 using only integers: no floating-point error
print(f"{tenths // 10}.{tenths % 10}")   # 212.0

n = -17
print(n if n >= 0 else -n)               # absolute value without abs(): 17""", "Swap, divmod, integer-only temperature conversion and absolute value"),
M("""## Avoid floating-point surprises

Decimals such as `0.1` cannot be stored exactly, so `0.1 + 0.2 != 0.3`. When the answer has a fixed number of decimals (two for money, one for a temperature), do the arithmetic with **integers in the smallest unit** (cents, tenths) and format at the end. The interest problem uses exactly this idea: compute with fractions or integers, then round half up.

## A quick method for every exercise

1. Write the input and the expected output for the sample by hand.
2. Name the values (`a`, `b`, `secs`).
3. Write one line per step of the calculation.
4. Test the edge cases: zero, one, the biggest allowed value, negative numbers.
"""),
M(practice(*V)),
], quiz=[
 Q("In Python, `-7 // 2` equals:", ["-4", "-3", "3", "4"], 0, "Floor division rounds toward minus infinity."),
 Q("Why compute a temperature in tenths with integers?", ["To avoid floating-point rounding errors", "It is shorter", "Python requires it", "It is faster to read"], 0, "Integers are exact."),
 Q("If `a = 17` and `b = 5`, what is `a % b`?", ["2", "3", "5", "12"], 0, "17 = 5 × 3 + 2."),
])

L_COND = L("conditionals-and-decisions", "Conditionals & Decision Making", "free", 18, "", blocks=[
M("""## Choosing between paths

A **conditional** runs different code depending on a condition. In Python: `if`, `elif` (else-if) and `else`. The condition must evaluate to true or false. Combine conditions with `and`, `or`, `not`; Python also lets you chain comparisons: `0 <= x < n`.

## Order matters

Conditions are tested top to bottom and the **first true one wins**. For a grade table (90+ is A, 80+ is B, ...) test the highest band first; if you test `marks >= 60` first, a score of 95 would be caught too early. Always ask: *can two branches both be true, and which should win?*

## Common building blocks

| Task | Test |
|---|---|
| Even or odd | `n % 2 == 0` (also correct for negatives in Python) |
| Sign | `n > 0`, `n < 0`, else zero |
| Leap year | divisible by 4, except century years, which need 400: `(y % 4 == 0 and y % 100 != 0) or y % 400 == 0` |
| Triangle with sides a, b, c | each side < the sum of the other two |
| Vowel | `ch.lower() in "aeiou"` |
| Three points collinear | `x1(y2-y3) + x2(y3-y1) + x3(y1-y2) == 0` |

### Why the collinearity formula
Three points are on one line exactly when the triangle they form has **zero area**. Twice that area is the expression above. It uses only multiplication and subtraction, so there is no division by zero (a slope formula fails for vertical lines) and no rounding error.
"""),
C("""def grade(m):
    if m >= 90: return "A"
    if m >= 80: return "B"
    if m >= 70: return "C"
    if m >= 60: return "D"
    return "F"

def is_leap(y):
    return (y % 4 == 0 and y % 100 != 0) or y % 400 == 0

def triangle(a, b, c):
    return a + b > c and a + c > b and b + c > a

def collinear(x1, y1, x2, y2, x3, y3):
    return x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2) == 0

print(grade(85), is_leap(1900), is_leap(2000), triangle(1, 2, 3), collinear(0, 0, 1, 1, 2, 2))
# B False True False True""", "Grades, leap years, triangles and collinear points as small functions"),
M("""## Edge cases to test every time

Equal boundaries (marks exactly 90 or 60), zero, negative numbers, the smallest and largest allowed values, and degenerate shapes (a "triangle" with sides 1, 2, 3 is a flat line).
"""),
M(practice(*CO)),
], quiz=[
 Q("For grades, why test `>= 90` before `>= 80`?", ["The first true branch wins, so higher bands must come first", "It is faster", "Python requires it", "No reason"], 0, "Otherwise 95 would match the lower band first."),
 Q("Is 1900 a leap year?", ["No", "Yes", "Only in Python", "It depends"], 0, "It is a century year not divisible by 400."),
 Q("Why use the cross-product test for collinear points?", ["It avoids division and works for vertical lines", "It is longer", "It uses floats", "It needs sorting"], 0, "A zero area means the points are on one line."),
])

L_LOOP = L("loops-and-iteration", "Loops & Iteration", "free", 18, "", blocks=[
M("""## Doing something many times

A **loop** repeats a block. `for i in range(1, n + 1)` runs with `i = 1, 2, ..., n`; `while cond:` repeats until the condition becomes false. Use `for` when you know how many repetitions there are, `while` when you repeat until something changes.

## The loop checklist

1. **Start** (where does the counter begin?), 2. **Stop** (which value ends it? Is the end included?), 3. **Step** (how does it change?), 4. **Body** (what happens each time?).

The most common bug is the **off-by-one error**: `range(1, n)` stops at `n - 1`; to include `n` write `range(1, n + 1)`.

## Patterns you will reuse
- **Accumulate:** start a total at 0 and add to it (`total += x`).
- **Digit loop:** `while n > 0: digit = n % 10; n //= 10` visits every digit of a number from the right.
- **Divisor loop:** divisors come in pairs `(d, n/d)`, so test `d` only up to `√n`. For `n = 10^12` that is a million steps instead of a trillion.

## How fast is your loop?
A loop that runs `n` times costs O(n). Looping to √n costs O(√n). Counting the number of times the inner statement runs is how you estimate whether a solution is fast enough.
"""),
C("""n = 10
print(*range(1, n + 1))                              # 1 2 3 ... 10

total = sum(x for x in range(2, n + 1, 2))           # sum of evens up to n: 30
print(total, (n // 2) * (n // 2 + 1))                # the closed form k(k+1) gives the same: 30 30

x, digits = 4096, 0
while x > 0:                                         # count digits by peeling the last one off
    digits += 1
    x //= 10
print(digits)                                        # 4

def sigma(n):                                        # sum of divisors in O(sqrt n)
    t, d = 0, 1
    while d * d <= n:
        if n % d == 0:
            t += d + (n // d if d * d != n else 0)   # do not add a square root twice
        d += 1
    return t
print(sigma(28), sigma(36))                          # 56 91""", "Counting, summing, digit loops and an O(sqrt n) divisor sum"),
M(practice(*LO)),
], quiz=[
 Q("To include `n` in a Python range starting at 1 you write:", ["range(1, n + 1)", "range(1, n)", "range(n)", "range(0, n)"], 0, "The stop value is excluded."),
 Q("Why does a divisor loop stop at √n?", ["Divisors come in pairs (d, n/d)", "Larger numbers are not divisors", "To avoid zero", "It is a Python rule"], 0, "One of each pair is at most √n."),
 Q("A loop that peels digits with `n //= 10` runs about:", ["log₁₀(n) times", "n times", "√n times", "once"], 0, "A number has about log₁₀(n) digits."),
])

L_PAT = L("pattern-printing", "Pattern Printing", "free", 22, "", blocks=[
M("""## Why print patterns?

Pattern problems look silly but train something real: **thinking in rows and columns** and controlling nested loops precisely. You will use the same skill for matrices, grids, dynamic-programming tables and image-like data.

## The three questions

For every pattern ask:
1. **How many rows?** (The outer loop.)
2. **In row `i`, how many spaces come first, and how many symbols follow?** (Write them as formulas in `i` and `n`.)
3. **What changes from one row to the next?**

Then each row is `" " * spaces + "*" * stars`.

## Worked formulas (n rows)

| Pattern | Row `i` (1-based) |
|---|---|
| Right-angled triangle | `i` stars |
| Inverted triangle | `n - i + 1` stars |
| Pyramid | `n - i` spaces, `2i - 1` stars |
| Inverted pyramid | `i - 1` spaces, `2(n - i) + 1` stars |
| Diamond | pyramid rows `1..n`, then pyramid rows `n-1..1` |
| Hourglass | inverted pyramid, then pyramid rows `2..n` |
| Butterfly | `i` stars, `2(n - i)` spaces, `i` stars; then the rows in reverse order |
| Floyd's triangle | `i` consecutive numbers; a counter keeps growing across rows |

**Hollow shapes:** print the border and leave the interior as spaces. For a hollow square, a cell is a star when its row is the first or last, or its column is the first or last.

**The spiral** is a different skill: fill an `n × n` grid by walking right, down, left, up, shrinking four boundaries (`top`, `bottom`, `left`, `right`) after each side.

## Output rules
Print **no trailing spaces**; the last character of each row is a symbol. Test `n = 1` and `n = 2`, where formulas like `n - 2` can go negative.
"""),
C("""n = 4
for i in range(1, n + 1):                      # pyramid
    print(" " * (n - i) + "*" * (2 * i - 1))
print()
for i in range(1, n + 1):                      # hollow square
    print("*" * n if i in (1, n) else "*" + " " * (n - 2) + "*")
print()
k = 1
for i in range(1, 4):                          # Floyd's triangle with 3 rows
    print(*range(k, k + i)); k += i

def spiral(n):
    g = [[0] * n for _ in range(n)]
    top, bot, left, right, k = 0, n - 1, 0, n - 1, 1
    while top <= bot and left <= right:
        for c in range(left, right + 1): g[top][c] = k; k += 1
        top += 1
        for r in range(top, bot + 1): g[r][right] = k; k += 1
        right -= 1
        if top <= bot:
            for c in range(right, left - 1, -1): g[bot][c] = k; k += 1
            bot -= 1
        if left <= right:
            for r in range(bot, top - 1, -1): g[r][left] = k; k += 1
            left += 1
    return g
for row in spiral(3): print(*row)              # 1 2 3 / 8 9 4 / 7 6 5""", "A pyramid, a hollow square, Floyd's triangle and the spiral walk"),
M(practice(*PA)),
], quiz=[
 Q("Row i of a centred pyramid with n rows has how many stars?", ["2i - 1", "i", "n - i", "2n"], 0, "1, 3, 5, ..."),
 Q("What is the first thing to define for any pattern?", ["Rows, then spaces and symbols per row as formulas", "The language", "A recursive function", "A sorted list"], 0, "Formulas in i and n drive the loops."),
 Q("Why test n = 1 and n = 2?", ["Formulas such as n - 2 can go negative", "They are slow", "Larger n is invalid", "To avoid recursion"], 0, "Small cases expose boundary mistakes."),
])

L_NT = L("number-theory-warmups", "Number Theory Warm-ups", "free", 22, "", blocks=[
M("""## Numbers you will meet again and again

**Primes, factorials, Fibonacci numbers, GCD, LCM, perfect numbers and binary representation** appear inside harder problems (hashing, modular arithmetic, combinatorics, bit tricks). Learn them once, properly.

## Prime numbers
A **prime** is an integer greater than 1 whose only divisors are 1 and itself. To test one number, try divisors from 2 up to `√n` (if `n` had a divisor larger than `√n`, its partner would be smaller): O(√n). To list **all primes up to n**, use the **Sieve of Eratosthenes**: mark every multiple of each prime as composite. It runs in about O(n log log n), far faster than testing each number alone.

## Factorial and Fibonacci
`n! = 1 × 2 × ... × n` grows extremely fast (20! already needs a 64-bit integer), so problems often ask for the answer **modulo 1 000 000 007**: take the remainder after every multiplication. The Fibonacci sequence `0, 1, 1, 2, 3, 5, ...` is generated by keeping the last two terms and updating them: O(n) time, O(1) space.

## GCD and LCM
The **greatest common divisor** comes from **Euclid's algorithm**: `gcd(a, b) = gcd(b, a % b)` until `b` is 0. It takes O(log min(a, b)) steps. The **least common multiple** is `lcm(a, b) = a // gcd(a, b) * b` (divide first to avoid overflow).

## Perfect numbers and binary
A number is **perfect** if it equals the sum of its proper divisors (6 = 1 + 2 + 3, 28 = 1 + 2 + 4 + 7 + 14). Use the divisor-pair loop up to √n. To convert a number to **binary**, repeatedly divide by 2 and collect the remainders in reverse; in Python `bin(n)` shows the result and `n & (n - 1)` clears the lowest set bit.
"""),
C("""def is_prime(n):
    if n < 2: return False
    d = 2
    while d * d <= n:
        if n % d == 0: return False
        d += 1
    return True

def sieve(n):
    s = [True] * (n + 1); s[0:2] = [False, False]
    for i in range(2, int(n ** 0.5) + 1):
        if s[i]:
            for j in range(i * i, n + 1, i): s[j] = False
    return [i for i in range(n + 1) if s[i]]

def gcd(a, b):
    while b: a, b = b, a % b
    return a

def to_binary(n):
    if n == 0: return "0"
    out = ""
    while n: out = str(n % 2) + out; n //= 2
    return out

print(is_prime(17), sieve(20))                 # True [2, 3, 5, 7, 11, 13, 17, 19]
print(gcd(12, 18), 12 // gcd(12, 18) * 18)     # 6 36
print(to_binary(10), 28 == sum(d for d in range(1, 28) if 28 % d == 0))   # 1010 True

a, b, fib = 0, 1, []
for _ in range(8): fib.append(a); a, b = b, a + b
print(fib)                                     # [0, 1, 1, 2, 3, 5, 8, 13]""", "Primality, the sieve, Euclid, binary conversion and Fibonacci"),
M(practice(*NT)),
], quiz=[
 Q("Euclid's algorithm computes:", ["The greatest common divisor", "The next prime", "A factorial", "A binary string"], 0, "gcd(a, b) = gcd(b, a mod b)."),
 Q("To test whether n is prime you only need divisors up to:", ["√n", "n / 2", "n", "log n"], 0, "A larger divisor pairs with a smaller one."),
 Q("Why divide before multiplying in lcm(a, b) = a // gcd * b?", ["To avoid overflow in fixed-size integers", "Python needs it", "It is faster", "No reason"], 0, "a × b can be huge."),
])

L_ARR = L("array-warmups", "Array Warm-ups", "free", 20, "", blocks=[
M("""## One pass, a little state

Most array warm-ups are a **single scan** that carries a little **state** from element to element. The skill is deciding what to remember.

| Task | State to carry | Cost |
|---|---|---|
| Sum / average | running total | O(n) time, O(1) space |
| Largest | best so far (start with the first element) | O(n), O(1) |
| Second largest (distinct) | best and second best | O(n), O(1) |
| Count evens and odds | two counters | O(n), O(1) |
| Linear search | nothing: stop at the first match | O(n), worst case n |
| Is sorted? | compare each element with the next | O(n), O(1) |
| Frequency of each value | a hash map value → count | O(n) time, O(distinct values) space |
| Reverse in place | two indices swapping inward | O(n) time, O(1) space |

## Details that cause wrong answers
- **Empty and one-element arrays:** initialise "best so far" from the first element, not from 0 (all values may be negative).
- **Second largest** must ignore equal copies of the maximum: `5 5 3` has second largest `3`.
- **Average:** divide only at the end; for two decimals round **half up** using integers: `(sum * 200 + n) // (2 * n)` gives hundredths.
- **Frequency order:** a hash map has no order; sort the keys before printing if the problem asks for one.

## Complexity words to use
Say "one pass, O(n) time, O(1) extra space" for scans, and "O(n) time and O(k) space for k distinct values" for the frequency map.
"""),
C("""a = [4, 9, 9, 2, 7]

best = a[0]
for x in a[1:]:
    if x > best: best = x
print(best)                                           # 9

hi = lo = None                                        # second largest distinct
for x in a:
    if hi is None or x > hi: lo, hi = hi, x
    elif x != hi and (lo is None or x > lo): lo = x
print(lo)                                             # 7

freq = {}
for x in a: freq[x] = freq.get(x, 0) + 1
print(sorted(freq.items()))                           # [(2, 1), (4, 1), (7, 1), (9, 2)]

i, j = 0, len(a) - 1
while i < j: a[i], a[j] = a[j], a[i]; i += 1; j -= 1
print(a)                                              # [7, 2, 9, 9, 4]

print(all(x <= y for x, y in zip(a, a[1:])))          # False: is it sorted?
total, n = sum(a), len(a)
c = (total * 200 + n) // (2 * n); print(f"{c // 100}.{c % 100:02d}")   # 6.20""", "Largest, second largest, frequencies, in-place reverse, sortedness and a rounded average"),
M(practice(*AR)),
], quiz=[
 Q("Why start 'largest so far' from the first element instead of 0?", ["All values may be negative", "Zero is slow", "It is shorter", "No reason"], 0, "Starting from 0 would give a wrong answer for all-negative input."),
 Q("Reversing an array in place uses how much extra space?", ["O(1)", "O(n)", "O(log n)", "O(n²)"], 0, "Two indices and swaps."),
 Q("What is the second largest distinct value in `5 5 3`?", ["3", "5", "None", "8"], 0, "Equal copies of the maximum do not count."),
])

L_STR = L("string-warmups", "String Warm-ups", "free", 20, "", blocks=[
M("""## Strings are sequences of characters

A **string** is an ordered sequence of characters, and most string warm-ups are scans just like arrays, with extra care for **spaces, case and non-letters**.

## Core facts
- Strings are **immutable** in Python, Java, Go and JavaScript: operations return a new string. To build a big string, collect pieces in a list and `"".join(...)` once; repeated `+=` can be O(n²).
- `len(s)` is O(1). `s[i]` is O(1). Slicing `s[a:b]` copies, O(b - a).
- Compare characters as numbers: `ord("a") == 97`, `ord("b") - ord("a") == 1`. This gives a letter index and a fast frequency table of 26 counters.
- Case: `c.lower()`, `c.upper()`, `c.isalpha()`, `c.isdigit()`.

## Typical tasks
| Task | Idea |
|---|---|
| Length | count characters (spaces count) |
| Vowels and consonants | count **letters** only; test `c.lower() in "aeiou"` |
| Words in a sentence | split on whitespace; consecutive spaces create no empty word |
| Palindrome | compare characters from both ends moving inward |
| Occurrences of a character | scan and compare (case sensitive) |
| Replace spaces | build a new string, swapping each space |
| Toggle case | swap upper and lower; leave other characters |
| Longest word | keep the best so far; a strict `>` keeps the **first** among ties |

## Pitfalls
Leading, trailing or double spaces; an empty string; upper-case vowels; characters that are not letters (digits, punctuation). Read the statement for exactly which characters count.
"""),
C("""s = "the quick  brown fox"
print(len(s))                                          # 20 (spaces count)
print(len(s.split()))                                  # 4 words (double space ignored)

letters = [c for c in s.lower() if c.isalpha()]
vowels = sum(1 for c in letters if c in "aeiou")
print(vowels, len(letters) - vowels)                   # 5 11

print("banana".count("a"))                             # 3
print("a b c".replace(" ", "-"))                       # a-b-c
print("Hello, World 1".swapcase())                     # hELLO, wORLD 1

best = ""
for w in "the quick brown fox".split():
    if len(w) > len(best): best = w                    # strict > keeps the first of equal length
print(best)                                            # quick

def is_pal(t):
    i, j = 0, len(t) - 1
    while i < j:
        if t[i] != t[j]: return False
        i, j = i + 1, j - 1
    return True
print(is_pal("level"), is_pal("hello"))                # True False""", "Length, words, vowels, replace, toggle, longest word and a palindrome test"),
M(practice(*ST)),
], quiz=[
 Q("`\"a  b\".split()` returns how many words?", ["2", "3", "1", "0"], 0, "split() without an argument ignores repeated whitespace."),
 Q("Why does a strict `>` keep the first longest word?", ["Equal-length later words do not replace it", "It is faster", "It sorts words", "It removes spaces"], 0, "Only a longer word can replace the best so far."),
 Q("Building a long string with `+=` in a loop can be:", ["O(n²)", "O(1)", "O(log n)", "Impossible"], 0, "Each step may copy the whole string; join once instead."),
])

L_REC = L("recursion-warmups", "Recursion Warm-ups", "free", 22, "", blocks=[
M("""## A function that calls itself

**Recursion** solves a problem by solving a smaller copy of it. Every recursive function has two parts:

1. **Base case**: the smallest input, answered directly (it stops the recursion).
2. **Recursive case**: reduce the input and call yourself, then combine.

Example: `sum(n) = n + sum(n - 1)` with `sum(0) = 0`. For `n = 3` the calls expand as `3 + (2 + (1 + 0))`.

## How the computer runs it
Each call gets a **stack frame**. The deepest call returns first, then each caller finishes its work on the way back. A recursion with no base case, or one that never reaches it, ends in a stack overflow (Python allows about 1000 nested calls by default).

## Order of work: before or after the call
If you **print before** the recursive call the output goes `1 2 3 ... n`; **after** the call it prints on the way back `n ... 3 2 1`. This is why "print 1 to N and N to 1" needs only one function.

## Cost
- Linear recursions (`sum`, `factorial`, `count digits`) make n calls: O(n) time and O(n) stack space.
- **Halving the exponent** makes power O(log n): `pow(a, b) = pow(a, b // 2)²` (times `a` if `b` is odd).
- The naive `fib(n) = fib(n-1) + fib(n-2)` recalculates the same values and takes exponential time; **memoising** (storing results) makes it O(n). This idea becomes dynamic programming later.

## Recursion on digits and strings
`n % 10` is the last digit and `n // 10` is the rest, so "sum of digits" and "count digits" recurse on `n // 10`. A string splits as `first character + rest`, so reversal is `reverse(rest) + first`. GCD recurses with `gcd(b, a % b)` until `b == 0`.
"""),
C("""import sys
from functools import lru_cache

def total(n):            return 0 if n == 0 else n + total(n - 1)
def fact(n):             return 1 if n == 0 else n * fact(n - 1)
def power(a, b):
    if b == 0: return 1
    half = power(a, b // 2)                      # halve the exponent: O(log b) calls
    return half * half * (a if b % 2 else 1)
def digit_sum(n):        return 0 if n == 0 else n % 10 + digit_sum(n // 10)
def count_digits(n):     return 1 if n < 10 else 1 + count_digits(n // 10)
def reverse(s):          return s if len(s) <= 1 else reverse(s[1:]) + s[0]
def gcd(a, b):           return a if b == 0 else gcd(b, a % b)

@lru_cache(None)                                 # memoisation: each F(k) computed once
def fib(n):              return n if n < 2 else fib(n - 1) + fib(n - 2)

def up_down(n):
    if n == 0: return
    print(n, end=" ")                            # printing BEFORE the call: n, n-1, ..., 1
    up_down(n - 1)
    print(n, end=" ")                            # printing AFTER the call: 1, 2, ..., n

print(total(5), fact(5), power(2, 10), digit_sum(4096), count_digits(12345), reverse("stack"), gcd(48, 18), fib(30))
# 15 120 1024 19 5 kcats 6 832040
up_down(3); print()                              # 3 2 1 1 2 3""", "Seven recursive functions, memoised Fibonacci and the before/after printing trick"),
M(practice(*RE)),
], quiz=[
 Q("Every recursive function needs:", ["A base case that stops it", "A loop", "A global variable", "A second function"], 0, "Without a base case it never terminates."),
 Q("Printing before the recursive call (counting down from n) gives:", ["n, n-1, ..., 1", "1, 2, ..., n", "Nothing", "A random order"], 0, "Work done before the call happens on the way in."),
 Q("Why is naive recursive Fibonacci slow?", ["It recalculates the same subproblems", "It uses loops", "Python limits it", "It sorts"], 0, "Memoisation stores each result once."),
 Q("Computing power by halving the exponent takes:", ["O(log b) calls", "O(b) calls", "O(b²) calls", "O(1)"], 0, "The exponent halves each call."),
])

WARMUP = [L_VARS, L_COND, L_LOOP, L_PAT, L_NT, L_ARR, L_STR, L_REC]
