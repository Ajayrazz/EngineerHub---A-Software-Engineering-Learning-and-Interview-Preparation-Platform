"""Original warm-up exercises (textbook-level programming practice). Each has a reference solution and an
independent brute force; build_problems.py refuses to emit one if they disagree on any test."""
import math
from problems_a import ints

def one(r, lo, hi): return str(r.randint(lo, hi))
def vals(inp): return ints(inp.replace("\n", " "))

def make(P):
    W = []
    def add(slug, title, diff, topics, statement, inp, out, cons, hints, ref, brute, gen, samples, edges=()):
        W.append(P(slug, title, diff, "free", topics, [], statement, inp, out, cons, hints, ref, brute, gen, samples, edges))

    add("even-or-odd", "Even or Odd", "easy", ["math"], "Read an integer and print `EVEN` if it is divisible by 2, otherwise `ODD`.",
        "One integer `n`.", "`EVEN` or `ODD`.", "-10^9 ≤ n ≤ 10^9.",
        ["What is the remainder when you divide by 2?", "Be careful with negative numbers: -3 is odd."],
        lambda i: "EVEN" if int(i) % 2 == 0 else "ODD", lambda i: "EVEN" if bin(abs(int(i)))[-1] == "0" else "ODD", lambda r: one(r, -50, 50),
        [("7", "7 leaves remainder 1 when divided by 2."), ("-4", "-4 is divisible by 2.")], ["0"])
    add("leap-year-check", "Leap Year Check", "easy", ["math"], "A year is a leap year if it is divisible by 4, except century years, which must be divisible by 400. Print `YES` or `NO`.",
        "One integer `y`.", "`YES` or `NO`.", "1 ≤ y ≤ 10^9.", ["Apply the three rules in order.", "1900 is not a leap year but 2000 is."],
        lambda i: "YES" if (int(i) % 4 == 0 and int(i) % 100 != 0) or int(i) % 400 == 0 else "NO",
        lambda i: "YES" if __import__("calendar").isleap(int(i)) else "NO", lambda r: one(r, 1, 3000),
        [("2024", "Divisible by 4 and not a century year."), ("1900", "A century year not divisible by 400.")], ["2000", "1"])
    add("count-digits", "Count Digits", "easy", ["math"], "Print how many digits the non-negative integer `n` has (0 has one digit).",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 10^18.", ["Repeatedly divide by 10.", "Treat 0 as a special case."],
        lambda i: str(len(str(int(i)))), lambda i: str(max(1, math.floor(math.log10(int(i))) + 1)) if int(i) > 0 else "1", lambda r: one(r, 0, 10**r.randint(1, 12)),
        [("98765", "Five digits."), ("0", "Zero has one digit.")], ["10", "999999999999"])
    add("sum-of-digits", "Sum of Digits", "easy", ["math"], "Print the sum of the decimal digits of `n`.",
        "One non-negative integer `n`.", "One integer.", "0 ≤ n ≤ 10^18.", ["`n % 10` is the last digit.", "Drop it with integer division by 10."],
        lambda i: str(sum(map(int, str(int(i))))), lambda i: str(sum(int(c) for c in i.strip())), lambda r: one(r, 0, 10**r.randint(1, 15)),
        [("1234", "1 + 2 + 3 + 4 = 10."), ("90", "9 + 0 = 9.")], ["0"])
    add("reverse-a-number", "Reverse a Number", "easy", ["math"], "Print the number formed by reversing the digits of non-negative `n` (drop leading zeros of the result).",
        "One non-negative integer `n`.", "One integer.", "0 ≤ n ≤ 10^18.", ["Build the result digit by digit: `rev = rev * 10 + last`."],
        lambda i: str(int(str(int(i))[::-1])), lambda i: str(int("".join(reversed(list(i.strip()))))), lambda r: one(r, 0, 10**r.randint(1, 15)),
        [("1200", "Reversed digits are 0021, which is 21."), ("345", "543.")], ["0"])
    add("palindrome-number", "Palindrome Number", "easy", ["math"], "Print `YES` if the non-negative integer reads the same forwards and backwards, otherwise `NO`.",
        "One non-negative integer.", "`YES` or `NO`.", "0 ≤ n ≤ 10^18.", ["Compare the number with its reversal."],
        lambda i: "YES" if str(int(i)) == str(int(i))[::-1] else "NO",
        lambda i: "YES" if all(str(int(i))[k] == str(int(i))[-1 - k] for k in range(len(str(int(i))))) else "NO", lambda r: one(r, 0, 2000),
        [("12321", "Same from both ends."), ("120", "Reversed it is 021.")], ["0", "11"])
    add("factorial", "Factorial", "easy", ["math"], "Print `n!` modulo 1 000 000 007 (0! = 1).",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 1000.", ["Multiply 1..n, taking the modulo after each step."],
        lambda i: str(math.factorial(int(i)) % (10**9 + 7)), lambda i: str(__import__("functools").reduce(lambda a, b: a * b % (10**9 + 7), range(1, int(i) + 1), 1)), lambda r: one(r, 0, 25),
        [("5", "5 × 4 × 3 × 2 × 1 = 120."), ("0", "By definition 0! = 1.")], ["1", "1000"])
    add("nth-fibonacci", "Nth Fibonacci Number", "easy", ["math", "dynamic-programming"], "With F(0)=0, F(1)=1 and F(n)=F(n-1)+F(n-2), print F(n) modulo 1 000 000 007.",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 100000.", ["Keep only the last two values.", "Take the modulo at each addition."],
        lambda i: str(_fib_iter(int(i))), lambda i: str(_fib_rec(int(i))), lambda r: one(r, 0, 22),
        [("10", "0 1 1 2 3 5 8 13 21 34 55: F(10) = 55."), ("1", "F(1) = 1.")], ["0", "50"])
    add("gcd-of-two-numbers", "GCD of Two Numbers", "easy", ["math"], "Print the greatest common divisor of two positive integers.",
        "Two integers `a b`.", "One integer.", "1 ≤ a, b ≤ 10^18.", ["Euclid: gcd(a, b) = gcd(b, a mod b)."],
        lambda i: str(math.gcd(*vals(i))), lambda i: str(max(d for d in range(1, min(vals(i)) + 1) if all(x % d == 0 for x in vals(i)))), lambda r: f"{r.randint(1, 60)} {r.randint(1, 60)}",
        [("12 18", "The largest number dividing both is 6."), ("17 5", "They share no divisor except 1.")], ["1 1"])
    add("lcm-of-two-numbers", "LCM of Two Numbers", "easy", ["math"], "Print the least common multiple of two positive integers.",
        "Two integers `a b`.", "One integer.", "1 ≤ a, b ≤ 10^9.", ["lcm(a, b) = a / gcd(a, b) × b."],
        lambda i: str(math.lcm(*vals(i))), lambda i: str(next(m for m in range(max(vals(i)), 10**7, max(vals(i))) if all(m % x == 0 for x in vals(i)))), lambda r: f"{r.randint(1, 40)} {r.randint(1, 40)}",
        [("4 6", "12 is the smallest number both divide."), ("5 5", "Equal numbers.")], ["1 7"])
    add("prime-number-check", "Prime Number Check", "easy", ["math"], "Print `YES` if `n` is prime, otherwise `NO`.",
        "One integer `n`.", "`YES` or `NO`.", "1 ≤ n ≤ 10^12.", ["Only test divisors up to √n.", "1 is not prime."],
        lambda i: "YES" if _is_prime(int(i)) else "NO", lambda i: "YES" if int(i) > 1 and all(int(i) % d for d in range(2, int(i))) else "NO", lambda r: one(r, 1, 200),
        [("17", "Only 1 and 17 divide it."), ("1", "1 is not prime.")], ["2", "91"])
    add("primes-up-to-n", "Count Primes Up To N", "medium", ["math"], "Print how many prime numbers are ≤ `n`.",
        "One integer `n`.", "One integer.", "1 ≤ n ≤ 5 000 000.", ["Testing each number separately is slow.", "The Sieve of Eratosthenes crosses out multiples of each prime."],
        lambda i: str(_sieve_count(int(i))), lambda i: str(sum(1 for k in range(2, int(i) + 1) if all(k % d for d in range(2, k)))), lambda r: one(r, 1, 120),
        [("10", "2, 3, 5 and 7."), ("1", "There are no primes up to 1.")], ["2", "100"])
    add("perfect-number-check", "Perfect Number Check", "easy", ["math"], "A number is **perfect** if it equals the sum of its proper divisors (excluding itself). Print `YES` or `NO`.",
        "One integer `n`.", "`YES` or `NO`.", "1 ≤ n ≤ 10^6.", ["Add the divisors from 1 to n/2, or pair them up to √n."],
        lambda i: "YES" if int(i) > 1 and _divsum(int(i)) == int(i) else "NO", lambda i: "YES" if int(i) > 1 and sum(d for d in range(1, int(i)) if int(i) % d == 0) == int(i) else "NO", lambda r: one(r, 1, 500),
        [("28", "1 + 2 + 4 + 7 + 14 = 28."), ("12", "The proper divisors add up to 16.")], ["1", "6", "496"])
    add("decimal-to-binary", "Decimal to Binary", "easy", ["math", "bit-manipulation"], "Print the binary representation of the non-negative integer `n` without leading zeros (print `0` for 0).",
        "One integer `n`.", "A string of 0s and 1s.", "0 ≤ n ≤ 10^18.", ["Repeatedly divide by 2 and collect remainders (in reverse)."],
        lambda i: bin(int(i))[2:], lambda i: _to_bin(int(i)), lambda r: one(r, 0, 300),
        [("10", "10 = 8 + 2 = 1010 in binary."), ("0", "Zero is `0`.")], ["1", "255"])
    add("power-of-two-check", "Power of Two Check", "easy", ["bit-manipulation"], "Print `YES` if `n` is a power of two (1, 2, 4, 8, ...), otherwise `NO`.",
        "One integer `n`.", "`YES` or `NO`.", "-10^9 ≤ n ≤ 10^9.", ["A power of two has exactly one bit set.", "`n & (n - 1)` clears the lowest set bit."],
        lambda i: "YES" if int(i) > 0 and int(i) & (int(i) - 1) == 0 else "NO", lambda i: "YES" if int(i) > 0 and bin(int(i)).count("1") == 1 else "NO", lambda r: one(r, -5, 70),
        [("64", "64 = 2^6."), ("48", "48 has two bits set.")], ["1", "0", "-8"])
    add("sum-of-array", "Sum of Array Elements", "easy", ["arrays"], "Print the sum of all numbers in the array.",
        "Line 1: `n`. Line 2: `n` integers.", "One integer.", "1 ≤ n ≤ 10^5; |a_i| ≤ 10^9.", ["Keep a running total."],
        lambda i: str(sum(ints(i.split("\n")[1]))), lambda i: str(_loop_sum(ints(i.split("\n")[1]))), _gen_arr,
        [("4\n3 -1 4 10", "3 - 1 + 4 + 10 = 16.")], ["1\n5"])
    add("second-largest-element", "Second Largest Element", "easy", ["arrays"], "Print the second largest **distinct** value, or `-1` if there is none.",
        "Line 1: `n`. Line 2: `n` integers.", "One integer.", "1 ≤ n ≤ 10^5.", ["Track the largest and second largest in one pass.", "Equal values do not count twice."],
        lambda i: _second(ints(i.split("\n")[1])), lambda i: (lambda s: str(s[-2]) if len(s) > 1 else "-1")(sorted(set(ints(i.split("\n")[1])))), _gen_arr,
        [("5\n4 9 9 2 7", "Distinct values are 2, 4, 7, 9; the second largest is 7."), ("3\n5 5 5", "Only one distinct value.")], ["1\n3"])
    add("count-even-and-odd", "Count Even and Odd Numbers", "easy", ["arrays"], "Print the number of even values and the number of odd values, separated by a space.",
        "Line 1: `n`. Line 2: `n` integers.", "`even odd`.", "1 ≤ n ≤ 10^5.", ["Check each value's remainder mod 2 (negative numbers too)."],
        lambda i: (lambda a: f"{sum(1 for x in a if x % 2 == 0)} {sum(1 for x in a if x % 2 != 0)}")(ints(i.split("\n")[1])),
        lambda i: (lambda a: f"{len([x for x in a if abs(x) % 2 == 0])} {len([x for x in a if abs(x) % 2 == 1])}")(ints(i.split("\n")[1])), _gen_arr,
        [("5\n1 2 3 4 6", "Evens: 2, 4, 6. Odds: 1, 3.")], ["1\n-3"])
    add("reverse-an-array", "Reverse an Array", "easy", ["arrays"], "Print the array in reverse order, separated by spaces.",
        "Line 1: `n`. Line 2: `n` integers.", "The reversed array.", "1 ≤ n ≤ 10^5.", ["Swap from both ends moving inward."],
        lambda i: " ".join(map(str, ints(i.split("\n")[1])[::-1])), lambda i: " ".join(str(ints(i.split("\n")[1])[len(ints(i.split("\n")[1])) - 1 - k]) for k in range(len(ints(i.split("\n")[1])))), _gen_arr,
        [("4\n1 2 3 4", "The last element comes first.")], ["1\n9"])
    add("is-array-sorted", "Check if an Array Is Sorted", "easy", ["arrays"], "Print `YES` if the array is in non-decreasing order, otherwise `NO`.",
        "Line 1: `n`. Line 2: `n` integers.", "`YES` or `NO`.", "1 ≤ n ≤ 10^5.", ["Compare each element with the next."],
        lambda i: "YES" if all(a <= b for a, b in zip(ints(i.split("\n")[1]), ints(i.split("\n")[1])[1:])) else "NO", lambda i: "YES" if ints(i.split("\n")[1]) == sorted(ints(i.split("\n")[1])) else "NO",
        lambda r: (lambda n, a: f"{n}\n" + " ".join(map(str, sorted(a) if r.random() < .5 else a)))(*(lambda n: (n, [r.randint(-5, 5) for _ in range(n)]))(r.randint(1, 8))),
        [("4\n1 2 2 5", "Never decreases."), ("3\n3 1 2", "3 is followed by 1.")], ["1\n4"])
    add("count-vowels", "Count Vowels", "easy", ["strings"], "Print how many vowels (`a e i o u`, case-insensitive) the text contains.",
        "One line of text.", "One integer.", "1 ≤ length ≤ 10^5.", ["Normalise to lower case, then count."],
        lambda i: str(sum(1 for c in i.lower() if c in "aeiou")), lambda i: str(len([c for c in i if c in "aeiouAEIOU"])), lambda r: "".join(r.choice("abcdeXYZ AEIOu") for _ in range(r.randint(1, 15))).strip() or "a",
        [("Hello World", "e, o, o."), ("rhythm", "No vowels.")], ["AEIOU"])
    add("palindrome-string-check", "Palindrome String Check", "easy", ["strings"], "Print `YES` if the string (letters only, lower case) reads the same both ways, otherwise `NO`.",
        "One string.", "`YES` or `NO`.", "1 ≤ length ≤ 10^5.", ["Compare characters from both ends moving inward."],
        lambda i: "YES" if i.strip() == i.strip()[::-1] else "NO", lambda i: "YES" if all(i.strip()[k] == i.strip()[len(i.strip()) - 1 - k] for k in range(len(i.strip()))) else "NO", lambda r: "".join(r.choice("ab") for _ in range(r.randint(1, 7))),
        [("level", "Same from both sides."), ("hello", "h is not o.")], ["a"])
    add("count-words", "Count Words in a Sentence", "easy", ["strings"], "Words are separated by one or more spaces. Print how many words the line contains.",
        "One line.", "One integer.", "0 ≤ length ≤ 10^5.", ["Split on whitespace and count the non-empty parts."],
        lambda i: str(len(i.split())), lambda i: str(sum(1 for k, c in enumerate(i) if c != " " and (k == 0 or i[k - 1] == " "))), lambda r: " ".join("w" * r.randint(1, 3) for _ in range(r.randint(1, 6))).replace(" ", " " * r.randint(1, 3)),
        [("the quick  brown fox", "Four words; extra spaces do not count."), ("hello", "One word.")], ["a"])
    add("toggle-case", "Toggle Case", "easy", ["strings"], "Swap the case of every letter (upper to lower and lower to upper); other characters stay the same.",
        "One line.", "The converted line.", "1 ≤ length ≤ 10^5.", ["Use the language's case helpers or add/subtract 32 on ASCII letters."],
        lambda i: i.swapcase(), lambda i: "".join(c.lower() if c.isupper() else c.upper() if c.islower() else c for c in i), lambda r: "".join(r.choice("aBc1 -Z") for _ in range(r.randint(1, 10))).strip() or "a",
        [("Hello, World 1", "Letters flip, the rest is unchanged.")], ["a"])
    add("sum-of-first-n-recursive", "Sum of First N Numbers", "easy", ["recursion", "math"], "Print 1 + 2 + ... + n. Practise writing it recursively: sum(n) = n + sum(n - 1).",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 5000.", ["Base case: sum(0) = 0.", "A closed formula also exists: n(n+1)/2."],
        lambda i: str(int(i) * (int(i) + 1) // 2), lambda i: str(_rec_sum(int(i))), lambda r: one(r, 0, 60),
        [("5", "1 + 2 + 3 + 4 + 5 = 15."), ("0", "Nothing to add.")], ["1"])
    add("largest-of-three", "Largest of Three", "easy", ["math"], "Print the largest of three integers.",
        "Three integers.", "One integer.", "|values| ≤ 10^9.", ["Compare in pairs."], lambda i: str(max(vals(i))), lambda i: str(sorted(vals(i))[-1]), lambda r: " ".join(str(r.randint(-9, 9)) for _ in range(3)),
        [("3 9 4", "9 is the greatest.")], ["5 5 5"])
    add("trailing-zeroes-of-factorial", "Trailing Zeroes of a Factorial", "medium", ["math"], "Print how many zeroes `n!` ends with.",
        "One integer `n`.", "One integer.", "0 ≤ n ≤ 10^9.", ["A zero comes from a factor 10 = 2 × 5.", "There are always more 2s than 5s, so count the 5s: n/5 + n/25 + ..."],
        lambda i: str(_tz(int(i))), lambda i: str(len(str(math.factorial(int(i)))) - len(str(math.factorial(int(i))).rstrip("0"))), lambda r: one(r, 0, 120),
        [("10", "10! = 3628800."), ("4", "4! = 24 has none.")], ["0", "100"])
    add("armstrong-number", "Armstrong Number", "easy", ["math"], "A number is an **Armstrong number** if it equals the sum of its digits each raised to the power of the number of digits (153 = 1³ + 5³ + 3³). Print `YES` or `NO`.",
        "One integer `n`.", "`YES` or `NO`.", "0 ≤ n ≤ 10^9.", ["Count the digits first."],
        lambda i: "YES" if int(i) == sum(int(c) ** len(i.strip()) for c in i.strip()) else "NO", lambda i: "YES" if int(i) == sum(int(c) ** len(str(int(i))) for c in str(int(i))) else "NO", lambda r: one(r, 0, 1000),
        [("153", "1 + 125 + 27 = 153."), ("154", "The sum is 190.")], ["0", "9474"])
    return W

def _fib_iter(n):
    a, b = 0, 1
    for _ in range(n): a, b = b, (a + b) % (10**9 + 7)
    return a
def _fib_rec(n):
    memo = {0: 0, 1: 1}
    def f(k):
        if k not in memo: memo[k] = f(k - 1) + f(k - 2)
        return memo[k]
    return f(n) % (10**9 + 7)
def _is_prime(n):
    if n < 2: return False
    d = 2
    while d * d <= n:
        if n % d == 0: return False
        d += 1
    return True
def _sieve_count(n):
    if n < 2: return 0
    s = bytearray([1]) * (n + 1); s[0] = s[1] = 0
    for i in range(2, int(n**0.5) + 1):
        if s[i]: s[i*i::i] = bytearray(len(s[i*i::i]))
    return sum(s)
def _divsum(n):
    t = 1
    d = 2
    while d * d <= n:
        if n % d == 0: t += d + (n // d if d != n // d else 0)
        d += 1
    return t
def _to_bin(n):
    if n == 0: return "0"
    s = ""
    while n: s = str(n % 2) + s; n //= 2
    return s
def _loop_sum(a):
    t = 0
    for x in a: t += x
    return t
def _second(a):
    hi = lo = None
    for x in a:
        if hi is None or x > hi: lo, hi = hi, x
        elif x != hi and (lo is None or x > lo): lo = x
    return str(lo) if lo is not None else "-1"
def _rec_sum(n): return 0 if n == 0 else n + _rec_sum(n - 1)
def _tz(n):
    c = 0
    while n: n //= 5; c += n
    return c
def _gen_arr(r):
    n = r.randint(1, 8); return f"{n}\n" + " ".join(str(r.randint(-9, 9)) for _ in range(n))
