"""More original warm-up exercises (textbook level): variables, conditionals, loops, patterns, arrays, strings,
recursion. Each has a reference solution and an independent brute force; build_problems.py rejects any mismatch."""
import math
from fractions import Fraction
from decimal import Decimal, getcontext, ROUND_HALF_UP
from problems_a import ints

def one(r, lo, hi): return str(r.randint(lo, hi))
def v(i): return ints(i.replace("\n", " "))
def lines(i): return i.split("\n")

def make(P):
    W = []
    def add(slug, title, diff, topics, statement, inp, out, cons, hints, ref, brute, gen, samples, edges=()):
        W.append(P(slug, title, diff, "free", topics, [], statement, inp, out, cons, hints, ref, brute, gen, samples, edges))

    # ------------------------------------------------------------------ Variables, I/O and operators
    add("swap-two-numbers", "Swap Two Numbers", "easy", ["math"], "Read two integers `a` and `b` and print them swapped: first `b`, then `a`. Try to do it with a temporary variable, then without one.",
        "Two integers `a b`.", "`b a`.", "|a|, |b| ≤ 10^9.", ["A temporary variable holds one value while you overwrite the other.", "Without a temporary: `a = a + b; b = a - b; a = a - b` (watch overflow in fixed-size languages)."],
        lambda i: f"{v(i)[1]} {v(i)[0]}", lambda i: " ".join(map(str, reversed(v(i)))), lambda r: f"{r.randint(-9,9)} {r.randint(-9,9)}",
        [("3 8", "The values trade places.")], ["5 5"])
    add("temperature-converter", "Temperature Converter", "easy", ["math"], "Convert a Celsius temperature (an integer) to Fahrenheit with the formula `F = C × 9 / 5 + 32`. Print the result with **exactly one decimal place**.",
        "One integer `c`.", "`F` with one decimal.", "-1000 ≤ c ≤ 1000.", ["Work in tenths to stay exact: F × 10 = C × 18 + 320.", "Then print the integer part, a dot, and the last digit (watch negative numbers)."],
        lambda i: _tenths(int(i) * 18 + 320), lambda i: str(Fraction(int(i)) * 9 / 5 + 32 + 0) and _tenths(int(Fraction(int(i)) * 9 / 5 * 10 + 320)), lambda r: one(r, -50, 150),
        [("100", "100 × 9 / 5 + 32 = 212."), ("-40", "At -40 both scales agree.")], ["0"])
    add("simple-and-compound-interest", "Simple Interest and Compound Interest", "easy", ["math"], "For principal `p`, yearly rate `r` percent and `t` whole years, print the **simple interest** `p × r × t / 100` on the first line and the **compound interest** `p × (1 + r/100)^t - p` on the second, each rounded **half up** to two decimals.",
        "Three integers `p r t`.", "Two lines: simple, compound.", "1 ≤ p ≤ 10^5; 1 ≤ r ≤ 20; 1 ≤ t ≤ 5.", ["Use exact fractions or integer arithmetic: p × (100 + r)^t / 100^t.", "Round half up: add half a cent before truncating."],
        lambda i: _interest(*v(i), exact=True), lambda i: _interest(*v(i), exact=False), lambda r: f"{r.randint(1,5000)} {r.randint(1,20)} {r.randint(1,5)}",
        [("1000 10 2", "Simple: 1000×10×2/100 = 200; compound: 1000×1.1² - 1000 = 210.")], ["1 1 1"])
    add("seconds-to-hms", "Convert Seconds to Hours, Minutes, Seconds", "easy", ["math"], "Convert a number of seconds into `h m s` where `0 ≤ m, s < 60`.",
        "One integer `secs`.", "`h m s`.", "0 ≤ secs ≤ 10^9.", ["Divide by 3600 for hours, then work with the remainder.", "`divmod` returns both the quotient and the remainder."],
        lambda i: (lambda s: f"{s // 3600} {s % 3600 // 60} {s % 60}")(int(i)), lambda i: _hms_loop(int(i)), lambda r: one(r, 0, 100000),
        [("3725", "3725 s is 1 hour, 2 minutes and 5 seconds.")], ["0", "59"])
    add("absolute-value", "Absolute Value Without Built-in", "easy", ["math"], "Print the absolute value of an integer **without** using a library `abs`.",
        "One integer `n`.", "One integer.", "-10^18 ≤ n ≤ 10^18.", ["If the number is negative, negate it.", "`n * -1` or `-n` flips the sign."],
        lambda i: str(int(i) if int(i) >= 0 else -int(i)), lambda i: str(max(int(i), -int(i))), lambda r: one(r, -50, 50),
        [("-17", "Negating a negative number gives its positive value.")], ["0"])
    add("quotient-and-remainder", "Quotient and Remainder of a Division", "easy", ["math"], "Divide `a` by `b` (a is non-negative, b is positive) and print the integer quotient and the remainder.",
        "Two integers `a b`.", "`quotient remainder`.", "0 ≤ a ≤ 10^18; 1 ≤ b ≤ 10^9.", ["Integer division discards the fraction.", "The remainder satisfies `a = b × q + r` and `0 ≤ r < b`."],
        lambda i: f"{v(i)[0] // v(i)[1]} {v(i)[0] % v(i)[1]}", lambda i: _div_loop(*v(i)), lambda r: f"{r.randint(0,200)} {r.randint(1,17)}",
        [("17 5", "5 × 3 + 2 = 17.")], ["0 4"])
    add("area-and-perimeter", "Area and Perimeter of Shapes", "easy", ["math"], "Read a shape and integer dimensions: `rectangle w h`, `square s` or `parallelogram b h side` (base, height, slanted side). Print its **area** and **perimeter**.",
        "One line: the shape name followed by its dimensions.", "`area perimeter`.", "Dimensions are integers from 1 to 10^6.", ["Rectangle: area w×h, perimeter 2(w+h).", "Parallelogram: area is base × height; perimeter is 2 × (base + side)."],
        lambda i: _shape(i), lambda i: _shape2(i), _gen_shape,
        [("rectangle 3 4", "Area 12, perimeter 14."), ("square 5", "Area 25, perimeter 20.")], ["parallelogram 6 4 5"])

    # ------------------------------------------------------------------ Conditionals
    add("positive-negative-or-zero", "Positive, Negative, or Zero", "easy", ["math"], "Print `POSITIVE`, `NEGATIVE` or `ZERO` for an integer.",
        "One integer.", "One word.", "|n| ≤ 10^18.", ["Compare with zero twice."],
        lambda i: "POSITIVE" if int(i) > 0 else "NEGATIVE" if int(i) < 0 else "ZERO", lambda i: ["NEGATIVE", "ZERO", "POSITIVE"][(int(i) > 0) - (int(i) < 0) + 1], lambda r: one(r, -5, 5),
        [("-3", "Below zero."), ("0", "Exactly zero.")], ["9"])
    add("grade-from-marks", "Grade from Marks", "easy", ["math"], "Print the grade for marks from 0 to 100: `A` for 90 or more, `B` for 80-89, `C` for 70-79, `D` for 60-69 and `F` below 60.",
        "One integer `marks`.", "One letter.", "0 ≤ marks ≤ 100.", ["Test from the highest band downwards."],
        lambda i: _grade(int(i)), lambda i: "ABCDF"[min(4, max(0, (9 - int(i) // 10)))] if int(i) >= 60 else "F", lambda r: one(r, 0, 100),
        [("85", "85 is in the 80-89 band."), ("59", "One mark short of a D.")], ["90", "60", "100", "0"])
    add("valid-triangle", "Valid Triangle from Three Sides", "easy", ["math"], "Three positive integers are the side lengths of a triangle with positive area if **each side is shorter than the sum of the other two**. Print `YES` or `NO`.",
        "Three integers.", "`YES` or `NO`.", "1 ≤ side ≤ 10^9.", ["Check all three pairs (or only the longest side against the other two)."],
        lambda i: "YES" if all(2 * x < sum(v(i)) for x in v(i)) else "NO", lambda i: "YES" if sorted(v(i))[0] + sorted(v(i))[1] > sorted(v(i))[2] else "NO", lambda r: " ".join(str(r.randint(1, 9)) for _ in range(3)),
        [("3 4 5", "3 + 4 > 5 and the others hold."), ("1 2 3", "1 + 2 equals 3: a flat line, not a triangle.")], ["5 5 5"])
    add("vowel-or-consonant", "Vowel or Consonant", "easy", ["strings"], "Read one English letter (either case). Print `VOWEL` if it is `a e i o u`, otherwise `CONSONANT`.",
        "One letter.", "`VOWEL` or `CONSONANT`.", "The input is a single letter.", ["Convert to lower case first."],
        lambda i: "VOWEL" if i.strip().lower() in "aeiou" else "CONSONANT", lambda i: "VOWEL" if i.strip() in list("aeiouAEIOU") else "CONSONANT", lambda r: r.choice("abcdeiouxyzAEIOUBCZ"),
        [("E", "Upper-case E is a vowel."), ("t", "t is a consonant.")], ["a"])
    add("collinear-points", "Check if Three Points are Collinear", "medium", ["math"], "Given three points with integer coordinates, print `YES` if they lie on one straight line, otherwise `NO`.",
        "Three lines, each `x y`.", "`YES` or `NO`.", "|coordinates| ≤ 10^9.", ["Three points are collinear when the area of their triangle is zero.", "Twice the area is `x1(y2-y3) + x2(y3-y1) + x3(y1-y2)`; avoid dividing (slopes break for vertical lines)."],
        lambda i: _col(v(i)), lambda i: _col2(v(i)), lambda r: "\n".join(f"{r.randint(-3,3)} {r.randint(-3,3)}" for _ in range(3)),
        [("0 0\n1 1\n2 2", "All on the line y = x."), ("0 0\n1 2\n2 3", "The slope changes.")], ["1 1\n1 5\n1 9"])

    # ------------------------------------------------------------------ Loops
    add("print-1-to-n", "Print Numbers from 1 to N", "easy", ["math"], "Print the numbers `1, 2, ..., n` on one line separated by single spaces.",
        "One integer `n`.", "The numbers on one line.", "1 ≤ n ≤ 10^5.", ["A `for` loop from 1 to n; build the line and print once."],
        lambda i: " ".join(map(str, range(1, int(i) + 1))), lambda i: _count_up(int(i)), lambda r: one(r, 1, 15),
        [("5", "Counting from 1 up to 5.")], ["1"])
    add("multiplication-table", "Multiplication Table of a Number", "easy", ["math"], "Print the multiplication table of `n` from 1 to 10, one line per row in the form `n x i = result`.",
        "One integer `n`.", "Ten lines.", "-1000 ≤ n ≤ 1000.", ["Loop i from 1 to 10 and print the product."],
        lambda i: "\n".join(f"{int(i)} x {k} = {int(i) * k}" for k in range(1, 11)), lambda i: "\n".join(_row(int(i), k) for k in range(1, 11)), lambda r: one(r, -12, 12),
        [("7", "Rows from 7 x 1 = 7 to 7 x 10 = 70.")], ["0"])
    add("sum-of-even-or-odd", "Sum of Even or Odd Numbers", "easy", ["math"], "Read `n` and a word `even` or `odd`. Print the sum of all even (or all odd) numbers from 1 to `n`.",
        "One line: `n word`.", "One integer.", "1 ≤ n ≤ 10^9.", ["Step by 2 starting at 2 (even) or 1 (odd).", "A formula exists: the sum of the first k evens is k(k+1), of the first k odds is k²."],
        lambda i: _evenodd_formula(i), lambda i: _evenodd_loop(i), lambda r: f"{r.randint(1,40)} {r.choice(['even','odd'])}",
        [("10 even", "2 + 4 + 6 + 8 + 10 = 30."), ("10 odd", "1 + 3 + 5 + 7 + 9 = 25.")], ["1 even"])
    add("sum-of-divisors", "Sum of All Divisors of a Number", "easy", ["math"], "Print the sum of all positive divisors of `n`, including 1 and `n` itself.",
        "One integer `n`.", "One integer.", "1 ≤ n ≤ 10^12.", ["Divisors come in pairs `(d, n/d)`: only test d up to √n.", "Do not add a square root twice."],
        lambda i: str(_sigma_sqrt(int(i))), lambda i: str(sum(d for d in range(1, int(i) + 1) if int(i) % d == 0)), lambda r: one(r, 1, 300),
        [("28", "1 + 2 + 4 + 7 + 14 + 28 = 56."), ("1", "Only the divisor 1.")], ["36", "97"])

    # ------------------------------------------------------------------ Pattern printing
    def pat(slug, title, desc, fn, fn2, hint, maxn=7, samples=((4,),), minn=1):
        add(slug, title, "easy", ["math"], desc + " Print nothing after the last character of each row (no trailing spaces).",
            "One integer `n`.", "The pattern, one row per line.", f"{minn} ≤ n ≤ {maxn}.", hint, lambda i: fn(int(i)), lambda i: fn2(int(i)),
            lambda r: one(r, minn, maxn), [(str(a[0]), "The pattern for this n.") for a in samples], [str(minn)])
    pat("right-angled-triangle", "Right-Angled Triangle of Stars", "Print a right-angled triangle of `*` with `n` rows: row `i` has `i` stars.", lambda n: "\n".join("*" * i for i in range(1, n + 1)), lambda n: _grid([[("*" if c < r else " ") for c in range(n)] for r in range(1, n + 1)]),
        ["Row i prints i stars.", "A nested loop prints the stars of one row; the outer loop picks the row."])
    pat("inverted-right-angled-triangle", "Inverted Right-Angled Triangle", "Print an inverted right-angled triangle of `*`: row `i` has `n - i + 1` stars.", lambda n: "\n".join("*" * (n - i + 1) for i in range(1, n + 1)), lambda n: _grid([[("*" if c < n - r else " ") for c in range(n)] for r in range(n)]),
        ["The first row has n stars and every row has one fewer."])
    pat("pyramid-of-stars", "Pyramid of Stars", "Print a centred pyramid of `*` with `n` rows: row `i` has `n - i` leading spaces and `2i - 1` stars.", lambda n: "\n".join(" " * (n - i) + "*" * (2 * i - 1) for i in range(1, n + 1)), lambda n: _grid([[("*" if abs(c - (n - 1)) <= r else " ") for c in range(2 * n - 1)] for r in range(n)]),
        ["Spaces first, then stars.", "Row i has an odd number of stars: 1, 3, 5, ..."])
    pat("inverted-pyramid", "Inverted Pyramid", "Print an inverted centred pyramid with `n` rows: row `i` has `i - 1` leading spaces and `2(n - i) + 1` stars.", lambda n: "\n".join(" " * (i - 1) + "*" * (2 * (n - i) + 1) for i in range(1, n + 1)), lambda n: _grid([[("*" if abs(c - (n - 1)) <= n - 1 - r else " ") for c in range(2 * n - 1)] for r in range(n)]),
        ["It is the pyramid upside down."])
    pat("hollow-square", "Hollow Square", "Print an `n × n` square outline of `*` with spaces inside.", lambda n: "\n".join("*" * n if i in (0, n - 1) else ("*" + " " * (n - 2) + "*" if n > 1 else "*") for i in range(n)), lambda n: _grid([["*" if (r in (0, n - 1) or c in (0, n - 1)) else " " for c in range(n)] for r in range(n)]),
        ["The first and last rows are full; middle rows have a star at each end.", "n = 1 is a single star."])
    pat("hollow-triangle", "Hollow Triangle", "Print a hollow right-angled triangle: row 1 is `*`, the last row has `n` stars, and rows in between have a star at each end.", lambda n: "\n".join("*" * i if i in (1, 2, n) else "*" + " " * (i - 2) + "*" for i in range(1, n + 1)), lambda n: _grid([["*" if (c == 0 or c == r or r == n - 1) and c <= r else " " for c in range(n)] for r in range(n)]),
        ["Only the left edge, the diagonal and the bottom row get stars."])
    pat("diamond-pattern", "Diamond Pattern", "Print a diamond made of a pyramid with `n` rows followed by an inverted pyramid with `n - 1` rows (`2n - 1` rows in total).", lambda n: "\n".join([" " * (n - i) + "*" * (2 * i - 1) for i in range(1, n + 1)] + [" " * (n - i) + "*" * (2 * i - 1) for i in range(n - 1, 0, -1)]), lambda n: _grid([["*" if abs(c - (n - 1)) <= (n - 1 - abs(r - (n - 1))) else " " for c in range(2 * n - 1)] for r in range(2 * n - 1)]),
        ["Top half, then the same rows in reverse without repeating the middle row."])
    pat("floyds-triangle", "Floyd's Triangle", "Print Floyd's triangle with `n` rows: consecutive integers starting at 1, row `i` holding `i` numbers separated by single spaces.", lambda n: _floyd(n), lambda n: _floyd2(n),
        ["Keep a counter that keeps growing across rows.", "Row i has i numbers."], maxn=12)
    pat("mirror-number-pattern", "Mirror Number Pattern", "For `n ≤ 9` print `n` rows. Row `i` is the digits `1..i`, then `2 × (n - i)` spaces, then the digits `i..1` (a mirror image).", lambda n: "\n".join("".join(map(str, range(1, i + 1))) + " " * (2 * (n - i)) + "".join(map(str, range(i, 0, -1))) for i in range(1, n + 1)), lambda n: _mirror2(n),
        ["Build the left part, the gap and the right part separately."], maxn=9)
    pat("butterfly-pattern", "Butterfly Pattern", "Print a butterfly of `2n` rows: for `i = 1..n` row `i` is `i` stars, `2(n - i)` spaces, `i` stars; then the same rows for `i = n..1`.", lambda n: "\n".join([("*" * i) + " " * (2 * (n - i)) + "*" * i for i in range(1, n + 1)] + [("*" * i) + " " * (2 * (n - i)) + "*" * i for i in range(n, 0, -1)]), lambda n: _butterfly2(n),
        ["Two triangles facing each other, then mirrored vertically."])
    pat("hourglass-pattern", "Hourglass Pattern", "Print an hourglass: an inverted pyramid with `n` rows followed by a pyramid with `n - 1` rows (`2n - 1` rows).", lambda n: "\n".join([" " * (i - 1) + "*" * (2 * (n - i) + 1) for i in range(1, n + 1)] + [" " * (n - i) + "*" * (2 * i - 1) for i in range(2, n + 1)]), lambda n: _grid([["*" if abs(c - (n - 1)) <= abs(r - (n - 1)) else " " for c in range(2 * n - 1)] for r in range(2 * n - 1)]),
        ["The widest rows are at the top and bottom; the waist is a single star."])
    pat("spiral-number-pattern", "Spiral Number Pattern", "Print an `n × n` grid filled with `1, 2, ..., n²` in a clockwise spiral starting at the top-left. Numbers in a row are separated by single spaces.", lambda n: _spiral(n), lambda n: _spiral2(n),
        ["Keep four boundaries (top, bottom, left, right) and shrink them after each side.", "Or walk and turn right whenever the next cell is filled or outside."], maxn=9)

    # ------------------------------------------------------------------ Number theory
    add("print-primes-up-to-n", "Print All Primes Up to N", "medium", ["math"], "Print all prime numbers from 2 to `n` on one line separated by single spaces.",
        "One integer `n`.", "The primes in order.", "2 ≤ n ≤ 10^6.", ["Test each number up to its square root, or use the sieve of Eratosthenes."],
        lambda i: " ".join(map(str, _primes(int(i)))), lambda i: " ".join(str(k) for k in range(2, int(i) + 1) if all(k % d for d in range(2, k))), lambda r: one(r, 2, 60),
        [("20", "2 3 5 7 11 13 17 19.")], ["2"])
    add("print-fibonacci-terms", "Print Fibonacci Up to N Terms", "easy", ["math"], "Print the first `n` Fibonacci numbers (`0 1 1 2 3 5 ...`) on one line separated by spaces.",
        "One integer `n`.", "n numbers.", "1 ≤ n ≤ 60.", ["Keep the previous two terms and update them each step."],
        lambda i: " ".join(map(str, _fibs(int(i)))), lambda i: " ".join(str(_fib_rec_small(k)) for k in range(int(i))), lambda r: one(r, 1, 20),
        [("7", "0 1 1 2 3 5 8.")], ["1", "2"])

    # ------------------------------------------------------------------ Array warmups
    ARR = lambda r: (lambda n: f"{n}\n" + " ".join(str(r.randint(0, 20)) for _ in range(n)))(r.randint(1, 8))
    add("average-of-array", "Average of Array Elements", "easy", ["arrays"], "Print the average of `n` non-negative integers, rounded **half up** to two decimal places.",
        "Line 1: `n`. Line 2: `n` integers.", "One number with two decimals.", "1 ≤ n ≤ 10^5; 0 ≤ a_i ≤ 10^9.", ["Sum first, then divide.", "Use integers to round exactly: `(sum × 200 + n) // (2n)` gives hundredths."],
        lambda i: _avg(i), lambda i: _avg2(i), ARR, [("4\n1 2 3 5", "Sum 11 over 4 elements is 2.75.")], ["1\n7"])
    add("largest-element", "Largest Element in an Array", "easy", ["arrays"], "Print the largest value in the array.",
        "Line 1: `n`. Line 2: `n` integers.", "One integer.", "1 ≤ n ≤ 10^5.", ["Start with the first element as the best so far.", "Compare each next element."],
        lambda i: str(max(v(lines(i)[1]))), lambda i: str(sorted(v(lines(i)[1]))[-1]), lambda r: (lambda n: f"{n}\n" + " ".join(str(r.randint(-9, 9)) for _ in range(n)))(r.randint(1, 8)),
        [("5\n3 9 -2 9 4", "9 is the largest.")], ["1\n-5"])
    add("linear-search", "Linear Search for a Target", "easy", ["arrays"], "Print the **1-based position of the first** occurrence of `t` in the array, or `-1` if it is absent.",
        "Line 1: `n t`. Line 2: `n` integers.", "One integer.", "1 ≤ n ≤ 10^5.", ["Check elements from left to right and stop at the first match."],
        lambda i: _lin(i), lambda i: _lin2(i), lambda r: (lambda n: f"{n} {r.randint(0,9)}\n" + " ".join(str(r.randint(0, 9)) for _ in range(n)))(r.randint(1, 8)),
        [("5 7\n4 7 7 1 0", "First 7 is at position 2."), ("3 5\n1 2 3", "5 is absent.")], ["1 1\n1"])
    add("count-frequency", "Count Frequency of Each Element", "easy", ["arrays", "hashing"], "Print each **distinct** value with how many times it appears, one `value count` pair per line, in **increasing order of value**.",
        "Line 1: `n`. Line 2: `n` integers.", "One line per distinct value.", "1 ≤ n ≤ 10^5.", ["Use a hash map from value to count.", "Sort the keys before printing."],
        lambda i: _freq(i), lambda i: _freq2(i), lambda r: (lambda n: f"{n}\n" + " ".join(str(r.randint(0, 5)) for _ in range(n)))(r.randint(1, 10)),
        [("6\n2 1 2 3 1 2", "1 appears twice, 2 three times, 3 once.")], ["1\n4"])

    # ------------------------------------------------------------------ String warmups
    TXT = lambda r: " ".join("".join(r.choice("abcdeIO") for _ in range(r.randint(1, 5))) for _ in range(r.randint(1, 4)))
    add("length-of-string", "Length of a String", "easy", ["strings"], "Print the number of characters in the line (spaces count; there are no leading or trailing spaces).",
        "One line of text.", "One integer.", "1 ≤ length ≤ 10^5.", ["Count characters one by one, or use the language's length function."],
        lambda i: str(len(i)), lambda i: str(sum(1 for _ in i)), TXT, [("hello world", "11 characters including the space.")], ["a"])
    add("count-vowels-and-consonants", "Count Vowels and Consonants", "easy", ["strings"], "Count the vowels (`a e i o u`, either case) and the consonants (all other **letters**) in the line. Spaces and other characters are ignored. Print `vowels consonants`.",
        "One line of text.", "`vowels consonants`.", "1 ≤ length ≤ 10^5.", ["Skip anything that is not a letter.", "Convert to lower case before testing for vowels."],
        lambda i: _vc(i), lambda i: _vc2(i), lambda r: "".join(r.choice("abcdeAEIOUxyz 12") for _ in range(r.randint(1, 15))).strip() or "a",
        [("Hello World 1", "e, o, o are vowels; the other 7 letters are consonants.")], ["a"])
    add("count-occurrence-of-character", "Count Occurrence of a Character", "easy", ["strings"], "Line 1 is a text, line 2 is one character. Print how many times that character appears in the text (case sensitive).",
        "Two lines: the text, then the character.", "One integer.", "1 ≤ length ≤ 10^5.", ["Compare each character with the target."],
        lambda i: str(lines(i)[0].count(lines(i)[1])), lambda i: str(sum(1 for c in lines(i)[0] if c == lines(i)[1])), lambda r: TXT(r) + "\n" + r.choice("abeI "),
        [("banana\na", "a appears three times.")], ["a\nb"])
    add("replace-spaces", "Replace All Spaces with a Character", "easy", ["strings"], "Line 1 is a text, line 2 is one character. Print the text with every space replaced by that character.",
        "Two lines: the text, then the character.", "The new text.", "1 ≤ length ≤ 10^5.", ["Build a new string character by character."],
        lambda i: lines(i)[0].replace(" ", lines(i)[1]), lambda i: "".join(lines(i)[1] if c == " " else c for c in lines(i)[0]), lambda r: TXT(r) + "\n" + r.choice("-_#"),
        [("a b c\n-", "Both spaces become dashes.")], ["a\n*"])
    add("longest-word", "Find the Longest Word in a Sentence", "easy", ["strings"], "Words are separated by single spaces. Print the **first** word of maximum length.",
        "One line.", "One word.", "1 ≤ length ≤ 10^5.", ["Split into words and keep the best so far.", "Use a strict `>` so ties keep the earlier word."],
        lambda i: _longest(i), lambda i: _longest2(i), TXT, [("the quick brown fox", "quick and brown tie at 5; the first wins.")], ["a"])

    # ------------------------------------------------------------------ Recursion
    add("factorial-recursive", "Factorial (Recursive)", "easy", ["recursion", "math"], "Write a recursive function `fact(n) = n × fact(n - 1)` with `fact(0) = 1` and print `fact(n)`.",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 20.", ["Base case: `fact(0) = 1`."], lambda i: str(math.factorial(int(i))), lambda i: str(_loop_fact(int(i))), lambda r: one(r, 0, 20),
        [("5", "5 × 4 × 3 × 2 × 1.")], ["0", "20"])
    add("fibonacci-recursive", "Nth Fibonacci Number (Recursive)", "easy", ["recursion"], "With `F(0) = 0`, `F(1) = 1`, print `F(n)` using the recursive definition `F(n) = F(n-1) + F(n-2)`.",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 30.", ["Two base cases.", "The plain recursion repeats work; memoise to make it linear."],
        lambda i: str(_fib_rec_small(int(i))), lambda i: str(_fibs(int(i) + 1)[-1]), lambda r: one(r, 0, 20), [("9", "0 1 1 2 3 5 8 13 21 34: F(9) = 34.")], ["0", "1", "30"])
    add("power-recursive", "Power of a Number (Recursive)", "easy", ["recursion", "math"], "Print `a` raised to the power `b` using recursion: `pow(a, b) = a × pow(a, b - 1)` (or faster: halve the exponent).",
        "Two integers `a b`.", "One integer.", "1 ≤ a ≤ 9; 0 ≤ b ≤ 30.", ["Base case: any number to the power 0 is 1.", "Halving: `pow(a, b) = pow(a, b/2)²` (times `a` if b is odd)."],
        lambda i: str(v(i)[0] ** v(i)[1]), lambda i: str(_pow_loop(*v(i))), lambda r: f"{r.randint(1,9)} {r.randint(0,12)}", [("2 10", "2 multiplied ten times is 1024.")], ["5 0"])
    add("reverse-string-recursive", "Reverse a String (Recursive)", "easy", ["recursion", "strings"], "Print the string reversed, computed recursively: `rev(s) = rev(s[1:]) + s[0]`.",
        "One line (no spaces).", "The reversed string.", "1 ≤ length ≤ 1000.", ["Base case: the empty string reverses to itself."],
        lambda i: i[::-1], lambda i: "".join(i[len(i) - 1 - k] for k in range(len(i))), lambda r: "".join(r.choice("abcxyz") for _ in range(r.randint(1, 8))), [("stack", "The last letter comes first.")], ["a"])
    add("sum-of-digits-recursive", "Sum of Digits (Recursive)", "easy", ["recursion", "math"], "Print the sum of the digits of `n` using recursion: `sum(n) = n % 10 + sum(n / 10)`.",
        "One non-negative integer.", "One integer.", "0 ≤ n ≤ 10^18.", ["Base case: `sum(0) = 0`."], lambda i: str(_digsum_rec(int(i))), lambda i: str(sum(map(int, i.strip()))), lambda r: one(r, 0, 10**r.randint(1, 12)),
        [("4096", "4 + 0 + 9 + 6 = 19.")], ["0"])
    add("print-1-to-n-and-back", "Print Numbers 1 to N and N to 1 (Recursive)", "easy", ["recursion"], "Using recursion, print `1 2 ... n` on the first line and `n ... 2 1` on the second.",
        "One integer `n`.", "Two lines.", "1 ≤ n ≤ 1000.", ["Print before the recursive call for one order, after it for the other."],
        lambda i: " ".join(map(str, range(1, int(i) + 1))) + "\n" + " ".join(map(str, range(int(i), 0, -1))), lambda i: _up_down(int(i)), lambda r: one(r, 1, 12), [("4", "Up then down.")], ["1"])
    add("count-digits-recursive", "Count Digits (Recursive)", "easy", ["recursion", "math"], "Print how many digits `n` has, using recursion (`count(n) = 1 + count(n / 10)`, with `count` of a single digit equal to 1).",
        "One non-negative integer.", "One integer.", "0 ≤ n ≤ 10^18.", ["Base case: n < 10 has one digit."], lambda i: str(_cd_rec(int(i))), lambda i: str(len(i.strip())), lambda r: one(r, 0, 10**r.randint(1, 15)),
        [("12345", "Five digits.")], ["0", "9", "10"])
    add("gcd-recursive", "GCD Using Recursion", "easy", ["recursion", "math"], "Print the GCD of two positive integers using the recursive Euclid rule `gcd(a, b) = gcd(b, a mod b)` with `gcd(a, 0) = a`.",
        "Two integers `a b`.", "One integer.", "1 ≤ a, b ≤ 10^9.", ["Base case: when b is 0 the answer is a."], lambda i: str(_gcd_rec(*v(i))), lambda i: str(math.gcd(*v(i))), lambda r: f"{r.randint(1,80)} {r.randint(1,80)}",
        [("48 18", "48 → 18 → 12 → 6 → 0: the answer is 6.")], ["7 7"])
    return W

# --------------------------------------------------------------------------------------------------------------- helpers
def _tenths(t):
    sign = "-" if t < 0 else ""; t = abs(t); return f"{sign}{t // 10}.{t % 10}"
def _interest(p, r, t, exact):
    simple = Fraction(p * r * t, 100)
    comp = Fraction(p * (100 + r) ** t, 100 ** t) - p
    if exact:                                    # reference: exact fractions, add half a cent, truncate
        f = lambda x: int((x * 100 + Fraction(1, 2)) // 1)
    else:                                        # brute force: high-precision Decimal with built-in half-up rounding
        getcontext().prec = 60
        def f(x):
            d = Decimal(x.numerator) / Decimal(x.denominator) * 100
            return int(d.to_integral_value(rounding=ROUND_HALF_UP))
    a, b = f(simple), f(comp)
    return f"{a // 100}.{a % 100:02d}\n{b // 100}.{b % 100:02d}"
def _hms_loop(s):
    h = m = 0
    while s >= 3600: s -= 3600; h += 1
    while s >= 60: s -= 60; m += 1
    return f"{h} {m} {s}"
def _div_loop(a, b):
    q = 0
    while a >= b: a -= b; q += 1
    return f"{q} {a}"
def _shape(i):
    w = i.split(); a = list(map(int, w[1:]))
    if w[0] == "rectangle": return f"{a[0] * a[1]} {2 * (a[0] + a[1])}"
    if w[0] == "square": return f"{a[0] ** 2} {4 * a[0]}"
    return f"{a[0] * a[1]} {2 * (a[0] + a[2])}"
def _shape2(i):
    w = i.split(); n = w[0]; a = list(map(int, w[1:]))
    area = {"rectangle": lambda: a[0] * a[1], "square": lambda: a[0] * a[0], "parallelogram": lambda: a[1] * a[0]}[n]()
    per = {"rectangle": lambda: a[0] + a[1] + a[0] + a[1], "square": lambda: sum([a[0]] * 4), "parallelogram": lambda: a[0] + a[2] + a[0] + a[2]}[n]()
    return f"{area} {per}"
def _gen_shape(r):
    k = r.choice(["rectangle", "square", "parallelogram"])
    return {"rectangle": f"rectangle {r.randint(1,20)} {r.randint(1,20)}", "square": f"square {r.randint(1,20)}", "parallelogram": f"parallelogram {r.randint(1,20)} {r.randint(1,20)} {r.randint(1,20)}"}[k]
def _grade(m): return "A" if m >= 90 else "B" if m >= 80 else "C" if m >= 70 else "D" if m >= 60 else "F"
def _col(p):
    (x1, y1, x2, y2, x3, y3) = p; return "YES" if x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2) == 0 else "NO"
def _col2(p):
    pts = [(p[0], p[1]), (p[2], p[3]), (p[4], p[5])]
    (ax, ay), (bx, by), (cx, cy) = pts
    return "YES" if (bx - ax) * (cy - ay) == (cx - ax) * (by - ay) else "NO"
def _count_up(n):
    out = []; k = 1
    while k <= n: out.append(str(k)); k += 1
    return " ".join(out)
def _row(n, k): return str(n) + " x " + str(k) + " = " + str(n * k)
def _evenodd_formula(i):
    n, w = i.split(); n = int(n); k = n // 2 if w == "even" else (n + 1) // 2
    return str(k * (k + 1) if w == "even" else k * k)
def _evenodd_loop(i):
    n, w = i.split(); n = int(n); return str(sum(x for x in range(1, n + 1) if (x % 2 == 0) == (w == "even")))
def _sigma_sqrt(n):
    t = 0; d = 1
    while d * d <= n:
        if n % d == 0: t += d + (n // d if d * d != n else 0)
        d += 1
    return t
def _grid(g): return "\n".join("".join(row).rstrip(" ") for row in g)
def _floyd(n):
    k = 1; out = []
    for i in range(1, n + 1):
        out.append(" ".join(str(k + j) for j in range(i))); k += i
    return "\n".join(out)
def _floyd2(n):
    seq = iter(range(1, n * (n + 1) // 2 + 1)); return "\n".join(" ".join(str(next(seq)) for _ in range(r)) for r in range(1, n + 1))
def _mirror2(n):
    rows = []
    for i in range(1, n + 1):
        left = "".join(str(d) for d in range(1, i + 1)); right = left[::-1]
        rows.append(left + " " * (2 * (n - i)) + right)
    return "\n".join(rows)
def _butterfly2(n):
    half = [("*" * i + " " * (2 * (n - i)) + "*" * i) for i in range(1, n + 1)]
    return "\n".join(half + half[::-1])
def _spiral(n):
    g = [[0] * n for _ in range(n)]; top, bot, left, right, k = 0, n - 1, 0, n - 1, 1
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
    return "\n".join(" ".join(map(str, row)) for row in g)
def _spiral2(n):
    g = [[0] * n for _ in range(n)]; r = c = 0; dr, dc = 0, 1
    for k in range(1, n * n + 1):
        g[r][c] = k
        nr, nc = r + dr, c + dc
        if not (0 <= nr < n and 0 <= nc < n and g[nr][nc] == 0): dr, dc = dc, -dr; nr, nc = r + dr, c + dc
        r, c = nr, nc
    return "\n".join(" ".join(map(str, row)) for row in g)
def _primes(n):
    s = bytearray([1]) * (n + 1); s[0:2] = b"\x00\x00"
    for i in range(2, int(n ** 0.5) + 1):
        if s[i]: s[i * i::i] = bytearray(len(s[i * i::i]))
    return [i for i in range(n + 1) if s[i]]
def _fibs(n):
    a, b, out = 0, 1, []
    for _ in range(n): out.append(a); a, b = b, a + b
    return out
def _fib_rec_small(n): return n if n < 2 else _fib_rec_small(n - 1) + _fib_rec_small(n - 2)
def _avg(i):
    l = i.split("\n"); a = list(map(int, l[1].split())); n = len(a); c = (sum(a) * 200 + n) // (2 * n); return f"{c // 100}.{c % 100:02d}"
def _avg2(i):
    l = i.split("\n"); a = list(map(Fraction, l[1].split())); m = sum(a) / len(a); c = int((m * 100 + Fraction(1, 2)) // 1); return f"{c // 100}.{c % 100:02d}"
def _lin(i):
    l = i.split("\n"); n, t = map(int, l[0].split()); a = list(map(int, l[1].split()))
    return str(a.index(t) + 1) if t in a else "-1"
def _lin2(i):
    l = i.split("\n"); n, t = map(int, l[0].split()); a = list(map(int, l[1].split()))
    for k in range(n):
        if a[k] == t: return str(k + 1)
    return "-1"
def _freq(i):
    from collections import Counter
    c = Counter(map(int, i.split("\n")[1].split())); return "\n".join(f"{k} {c[k]}" for k in sorted(c))
def _freq2(i):
    a = list(map(int, i.split("\n")[1].split())); return "\n".join(f"{k} {a.count(k)}" for k in sorted(set(a)))
def _vc(i):
    t = i.lower(); return f"{sum(1 for c in t if c in 'aeiou')} {sum(1 for c in t if c.isalpha() and c not in 'aeiou')}"
def _vc2(i):
    letters = [c for c in i if ("a" <= c <= "z") or ("A" <= c <= "Z")]; v = [c for c in letters if c in "aeiouAEIOU"]; return f"{len(v)} {len(letters) - len(v)}"
def _longest(i):
    best = ""
    for w in i.split():
        if len(w) > len(best): best = w
    return best
def _longest2(i):
    w = i.split(); m = max(len(x) for x in w); return next(x for x in w if len(x) == m)
def _loop_fact(n):
    t = 1
    for k in range(2, n + 1): t *= k
    return t
def _pow_loop(a, b):
    t = 1
    for _ in range(b): t *= a
    return t
def _digsum_rec(n): return 0 if n == 0 else n % 10 + _digsum_rec(n // 10)
def _up_down(n):
    return " ".join(str(k) for k in range(1, n + 1)) + "\n" + " ".join(str(n - k) for k in range(n))
def _cd_rec(n): return 1 if n < 10 else 1 + _cd_rec(n // 10)
def _gcd_rec(a, b): return a if b == 0 else _gcd_rec(b, a % b)
