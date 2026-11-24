"""Long-form lessons for the DSA course's first two chapters (original text)."""
from courses_a import Q, L

INTRO = L("course-introduction", "Course introduction", "free", 14, "", blocks=[
 ("md", """## Welcome

This course is a complete, structured path through **data structures and algorithms (DSA)**, written for one goal: helping you solve coding-interview problems you have never seen before, calmly and in a clear order. You do not need to be a genius, and you do not need to have studied computer science. You need curiosity, a language you can write simple programs in, and a regular habit of practice.

### Who this course is for

- **Students and new graduates** preparing for their first software job.
- **Working engineers** who have not touched algorithms for years and want a refresher before interviewing.
- **Career switchers** who can code but have never been taught the vocabulary of complexity, hashing, recursion and graphs.
- **Anyone who wants to think more clearly about programs**: the habits you build here (define the problem, find a pattern, test on edge cases) help in everyday engineering too.

### What you need before you start

| You should be able to... | If not |
|---|---|
| Write a small program with variables, `if` and loops in one language | Start with the *Programming Fundamentals* and *DSA Warmup* chapters |
| Read and write arrays (lists) and strings | Chapters *Arrays* and *Strings* cover them from scratch |
| Use a terminal or an editor | Any editor works; our practice problems use plain input and output |

No special math is required. The *Math Fundamentals* chapter explains the few ideas (logarithms, remainders, primes) the rest of the course relies on.

## What you will be able to do at the end

By the last chapter you should be able to:

1. **Estimate the cost** of a solution (time and memory) before writing it, and say why it is fast enough.
2. **Choose a data structure** on purpose: array, hash map, stack, queue, heap, tree, graph, trie.
3. **Recognise the pattern** behind a problem (two pointers, sliding window, binary search on the answer, BFS, dynamic programming...) from a few clues.
4. **Turn an idea into correct code**, including the edge cases that cause most bugs.
5. **Explain your thinking out loud**, which is half of what an interviewer scores.

## How the course is organised

The list on the left is the **table of contents**. It is grouped into chapters, and every chapter has short lessons.

| Part | Chapters | What it gives you |
|---|---|---|
| **Getting ready** | Welcome, Coding Interview Strategy, Programming Fundamentals, DSA Warmup | How interviews work, and the programming muscle memory everything else needs |
| **Measuring and tools** | Big O, Math Fundamentals, Sorting Algorithms | A language for speed and the building blocks you will reuse |
| **Core techniques** | Arrays, Strings, Bit Manipulation, Hash Tables, Two Pointers, Sliding Window, Prefix Sum, Kadane's Algorithm, Binary Search, Matrix | The patterns that solve most array and string problems |
| **Data structures** | Linked List, Stacks, Queues, Recursion and Backtracking, Binary Tree, BST, Heaps, Intervals, Tries, Data Structure Design | Structures that organise data, and the problems they unlock |
| **Advanced ideas** | Greedy, Graphs, Dynamic Programming, Advanced Topics | The topics that separate a good interview from a great one |

## What every lesson contains

- **A concept explained in plain language**, with a small example and short code.
- **A visualization** where seeing the algorithm run helps: step forward and back, change the speed, and try your own input.
- **A quiz** with explanations after you answer, so mistakes teach you something.
- **Practice problems** in our problem bank, with graded hints (a nudge first, the full idea last) and example inputs and outputs.
- **A "Mark as complete" button**. Your progress percentage at the top of the sidebar updates as you finish lessons.

## How to learn from this course (and actually remember it)

Reading is the easy part. These habits turn reading into skill.

1. **Predict before you look.** When a lesson poses a problem, pause and try it for five to ten minutes before reading the idea. Even a wrong attempt makes the explanation stick.
2. **Do the visualization with a pencil.** Run the animation once, then run your own small input by hand and compare.
3. **Write the code yourself.** Do not copy and paste. Typing it out reveals the off-by-one mistakes that reading hides.
4. **Keep an error log.** For every problem you miss, write one line: what the problem was, which idea you missed, and what clue should have pointed to it. Re-read the log weekly.
5. **Space your reviews.** Revisit a solved problem after 1 day, 1 week and 1 month. If you can still solve it quickly, it is learned.
6. **Practise out loud.** Explain your approach to a rubber duck, a friend, or a recording. Clear speech is clear thinking.
7. **Time yourself sometimes.** Once a week, solve a medium problem in 30 minutes with no hints, as in a real interview.

### A good study session (60 to 90 minutes)

| Minutes | Activity |
|---|---|
| 0 - 10 | Review yesterday's error log and one old problem |
| 10 - 30 | Read the new lesson and run its visualization |
| 30 - 70 | Solve two practice problems without peeking; use hints only after a real attempt |
| 70 - 80 | Write the error-log entries; note the pattern and the clue |
| 80 - 90 | Take the lesson quiz and mark the lesson complete |

## Ways to improve faster

- **Depth beats breadth.** Solving 10 problems of one pattern until it feels easy beats touching 50 patterns once.
- **Always state complexity.** After every solution say, out loud, "time O(...), space O(...), because...". This becomes automatic.
- **Compare approaches.** For each problem, write the simple approach first, then the better one, and note what changed (usually a data structure).
- **Test like a skeptic.** Before running code, test it by hand on an empty input, one element, duplicates, negative numbers and the maximum size.
- **Rest.** Sleep and short breaks consolidate memory. A rested 45 minutes beats an exhausted three hours.

## Free and premium content

Many lessons and problems are free. Some are premium; premium lessons show a lock in the sidebar, and some offer a free preview. See the *Pricing* page for plans.

## Common questions

**How long will it take?** See the *Course roadmap* lesson for plans from two weeks to six months, with hours per chapter.

**Do I need to learn every chapter?** No. The roadmap shows which chapters matter most if time is short.

**Which language should I use?** The one you know best. The *Choosing a language* lesson lists what to learn in each.

**What if I get stuck?** That is normal and useful. Use the hints in order, take a break, and write down what you tried. Being stuck is where the learning happens.

**When will I feel ready?** Usually when you can solve a new medium problem in about 30 minutes while explaining your thinking, and your error log is mostly repeats you now catch yourself.

Ready? Open the next lesson, the *Course roadmap*, and choose your plan."""),
 ("tip", "Make a promise to yourself right now: how many days a week, and at what time, will you study? A fixed slot predicts success better than motivation."),
], quiz=[
 Q("Which habit most improves long-term retention?", ["Spaced reviews of problems you solved", "Reading each lesson once quickly", "Copying solutions", "Skipping hard problems forever"], 0, "Revisiting after a day, a week and a month moves knowledge into long-term memory."),
 Q("What should you do first when a lesson poses a problem?", ["Try it yourself for several minutes", "Read the full solution", "Skip to the quiz", "Close the lesson"], 0, "Predicting first makes the explanation far more memorable."),
 Q("An error log should record:", ["The missed idea and the clue that should have pointed to it", "Only the problem title", "Your mood", "Nothing; just solve more"], 0, "The clue is what you will look for next time."),
 Q("Do you need premium access to start this course?", ["No, many lessons and problems are free", "Yes, always", "Only on weekends", "Only for quizzes"], 0, "Free lessons and previews stay open to everyone.", "single"),
])

ROADMAP = L("course-roadmap", "Course roadmap", "free", 16, "", blocks=[
 ("md", """## Your journey at a glance

Above this text is the animated roadmap of the course. It is built from the real chapter list, so the totals update as lessons are added. Four phases take you from your first loop to confident interview performance: **Foundations**, **Core patterns**, **Data structures** and **Advanced and interview-ready**. Follow the line from the left to the right; each stop lists its chapters, the weeks it takes on the Steady plan, and the hours.

## How to read the plans

Time estimates assume **about 1.5 hours a day**, five days a week, which is roughly **8 hours a week**. If you study more, you finish sooner; if less, stretch each plan proportionally. These are estimates, not promises: people differ, and understanding matters more than speed.

## Choose a plan

| Plan | Duration | Weekly time | Best for |
|---|---|---|---|
| **Sprint** | 2 weeks | 14 - 20 hours | An interview is days away; you already code comfortably |
| **Focused** | 4 weeks | 10 - 12 hours | A job search starting now |
| **Steady** | 10 - 12 weeks | 6 - 8 hours | Working while studying; the recommended pace |
| **Complete** | 6 months | 4 - 6 hours | Students and career switchers who want depth |

## Plan 1: Sprint (2 weeks)

Cover only what interviews use most.

| Days | Chapters | Goal |
|---|---|---|
| 1 - 2 | Coding Interview Strategy, Big O Notation | Know the format and how to talk about speed |
| 3 - 5 | Arrays, Strings, Hash Tables | Solve most easy problems with a hash map |
| 6 - 8 | Two Pointers, Sliding Window, Prefix Sum | The three patterns behind many medium problems |
| 9 - 10 | Binary Search | Including binary search on the answer |
| 11 - 12 | Stacks, Queues, Linked List basics | Brackets, next-greater, list reversal |
| 13 - 14 | Mixed practice and one mock interview | Timed problems, error-log review |

## Plan 2: Focused (4 weeks)

| Week | Chapters | Milestone |
|---|---|---|
| 1 | Strategy, Big O, Math (light), Arrays, Strings, Hash Tables | Solve easy array/string problems in 15 minutes |
| 2 | Two Pointers, Sliding Window, Prefix Sum, Kadane, Binary Search | Recognise the pattern from the clues |
| 3 | Linked List, Stacks, Queues, Recursion and Backtracking, Binary Tree | Comfortable with recursion on trees |
| 4 | Graphs (BFS/DFS), Heaps, Intervals, intro to Dynamic Programming, mock interviews | Two timed mock interviews |

## Plan 3: Steady (10 to 12 weeks, recommended)

| Weeks | Focus | Chapters |
|---|---|---|
| 1 | **Getting ready** | Welcome, Coding Interview Strategy, Programming Fundamentals, DSA Warmup |
| 2 | **Measuring** | Big O Notation, Math Fundamentals, Sorting Algorithms |
| 3 - 4 | **Arrays and strings** | Arrays, Strings, Bit Manipulation, Hash Tables |
| 5 - 6 | **Pointers and windows** | Two Pointers, Sliding Window, Prefix Sum, Kadane's Algorithm |
| 7 | **Searching** | Binary Search, Matrix |
| 8 | **Linear structures** | Linked List, Stacks, Queues |
| 9 | **Recursion and trees** | Recursion and Backtracking, Binary Tree, BST |
| 10 | **Priority and prefix** | Heaps, Intervals, Tries, Data Structure Design |
| 11 | **Graphs and greedy** | Greedy, Graphs |
| 12 | **Dynamic programming and review** | Dynamic Programming, Advanced Topics, mock interviews |

## Plan 4: Complete (6 months)

Follow the Steady plan but spend **two weeks per phase** instead of one, add the *Advanced Topics* chapter fully, and finish each chapter by solving several problems of every difficulty. Add one mock interview every two weeks.

## Hours per chapter (estimate)

| Chapter group | Study | Practice | Total |
|---|---|---|---|
| Getting ready (4 chapters) | 3 h | 5 h | 8 h |
| Big O, Math, Sorting | 3 h | 5 h | 8 h |
| Arrays, Strings, Bits, Hash tables | 4 h | 10 h | 14 h |
| Two pointers, Window, Prefix, Kadane | 4 h | 12 h | 16 h |
| Binary search, Matrix | 2 h | 6 h | 8 h |
| Linked list, Stacks, Queues | 3 h | 7 h | 10 h |
| Recursion, Trees, BST | 4 h | 10 h | 14 h |
| Heaps, Intervals, Tries, Design | 3 h | 9 h | 12 h |
| Greedy, Graphs | 4 h | 12 h | 16 h |
| Dynamic Programming, Advanced | 5 h | 15 h | 20 h |
| **Total** | **35 h** | **91 h** | **~126 h** |

At about 8 hours a week that is roughly **16 weeks for everything**, and about **10 weeks** if you skip the lightest chapters.

## Weekly rhythm that works

| Day | Activity |
|---|---|
| Mon - Thu | One new lesson, its visualization, and two practice problems |
| Fri | Review the error log; redo two problems from earlier weeks |
| Sat | One timed problem with no hints, then write up the pattern |
| Sun | Rest, or light reading |

## Milestones: how to know you are on track

| After | You can... |
|---|---|
| Week 2 | State the time and space cost of any simple loop |
| Week 4 | Solve an easy array problem in 15 minutes and explain it |
| Week 6 | Recognise two-pointer, window and prefix-sum problems from the clues |
| Week 8 | Reverse a list, match brackets and use a monotonic stack |
| Week 10 | Write recursive tree solutions without looking anything up |
| Week 12 | Solve a new medium problem in 30 minutes while explaining |

## If you fall behind

It happens to everyone. Do not try to catch up by doubling your hours. Instead: **drop the least important chapters** (Advanced Topics first, then Tries and Data Structure Design), keep the weekly review, and keep the finish date by trimming scope rather than sleep.

## If you are ahead

Add mock interviews earlier, solve harder variants, and teach a concept to someone else. Explaining is the strongest test of understanding."""),
 ("tip", "Pick your plan today and put the first week in your calendar as real appointments. A plan that is not scheduled is a wish."),
], quiz=[
 Q("Roughly how many hours a week does the Steady plan assume?", ["About 8", "About 40", "About 1", "About 100"], 0, "1.5 hours a day, five days a week."),
 Q("If you fall behind you should:", ["Trim scope and keep your review habit", "Double your hours every day", "Quit", "Skip all practice"], 0, "Cutting the least important chapters keeps the plan realistic."),
 Q("Which pair do you cover first in every plan?", ["Strategy and Big O", "Tries and segment trees", "Dynamic programming and KMP", "Bit tricks only"], 0, "Knowing the format and how to talk about speed supports everything else."),
 Q("The total study plus practice time for everything is about:", ["126 hours", "10 hours", "1000 hours", "2 hours"], 0, "Roughly 35 hours of study and 91 hours of practice."),
])

HOW = L("how-coding-interviews-work", "How coding interviews work", "free", 18, "", blocks=[
 ("md", """## The big picture

A coding interview is a **structured conversation** in which you solve a problem while an interviewer watches how you think. It is less like an exam and more like pair programming with a stranger. Understanding the process removes a lot of the fear, because most of the surprise disappears.

## The stages of a hiring process

Processes differ by company, but most follow this shape.

| Stage | What happens | Typical length | What is tested |
|---|---|---|---|
| **Recruiter call** | Background, role fit, logistics, salary range | 20 - 30 min | Communication, motivation |
| **Online assessment** | Timed problems in a browser, often auto-graded | 60 - 120 min | Correctness and speed |
| **Technical phone screen** | One or two problems in a shared editor with an engineer | 45 - 60 min | Core problem solving |
| **Onsite or virtual loop** | 3 - 6 interviews in a day (coding, design, behavioural) | 3 - 5 h total | Depth, breadth, collaboration |
| **Hiring decision** | Interviewers compare notes; sometimes a team-match call | days to weeks | Overall signal |
| **Offer** | Compensation, level, start date | - | Negotiation |

## Types of coding rounds

- **Shared online editor** (most common): you type code that the interviewer sees live. You may or may not be able to run it.
- **Whiteboard or plain document**: no compiler; write clear, careful code and trace it by hand.
- **Online assessment**: a timer, hidden tests, no interaction. Read the constraints and write efficient code the first time.
- **Take-home project**: a few hours of work on a small real task; quality and readability matter.
- **Pair-programming round**: you and the interviewer work on a realistic task together.

## Anatomy of a 45 minute round

| Minutes | What you do | What the interviewer notes |
|---|---|---|
| 0 - 5 | Introductions, short chat | Are you comfortable and clear? |
| 5 - 10 | **Understand** the problem: restate it, ask questions, try an example | Do you clarify before building? |
| 10 - 20 | **Plan**: simple approach first, then improve; state complexity | How you reason about trade-offs |
| 20 - 35 | **Code**: write clean code while narrating | Fluency, naming, structure |
| 35 - 42 | **Test**: walk through examples and edge cases; fix bugs | Care and debugging habits |
| 42 - 45 | Your questions for the interviewer | Genuine interest |

If there are two problems, each phase is shorter. Do not rush the first five minutes: a correct understanding saves the most time.

## What interviewers actually evaluate

Most companies score a rubric with four or five dimensions:

1. **Problem solving**: can you break a new problem into steps and find a workable idea?
2. **Algorithms and data structures**: do you pick fitting tools and analyse their cost?
3. **Coding**: is the code correct, readable and organised?
4. **Testing and debugging**: do you verify your work and find your own mistakes?
5. **Communication**: do you explain, listen and respond to hints?

Notice that **only part of the score is "did you get the optimal answer"**. A candidate who communicates well, reaches a reasonable solution with a small hint and tests carefully often scores higher than one who silently produces a perfect answer.

## A routine you can rely on

Use the same five steps every time:

1. **Restate** the problem in your own words.
2. **Clarify**: input size, value ranges, empty input, duplicates, sorted or not, what to return when no answer exists.
3. **Example**: work a small case by hand, including a tricky one.
4. **Approach**: say the brute-force idea, its cost, then a better idea and why it is better.
5. **Code, then test**: write it, then trace an example and edge cases.

## Levels and expectations

| Level | Typical expectation |
|---|---|
| New graduate / junior | Solve easy-to-medium problems with some guidance; clear basics |
| Mid-level | Solve medium problems independently; discuss trade-offs and complexity |
| Senior | Solve harder or open-ended problems cleanly, own the discussion, anticipate edge cases and scale |

## Common mistakes (and fixes)

| Mistake | Fix |
|---|---|
| Coding before understanding | Spend the first five minutes on questions and an example |
| Silence for long stretches | Narrate your thinking, even when unsure |
| Jumping straight to a clever solution | Start with brute force; it is a safe baseline |
| Ignoring edge cases | Name them early: empty, one item, duplicates, extremes |
| Never testing | Trace an example through your code line by line |
| Arguing with hints | Treat hints as help, not criticism |
| Giving up when stuck | Say what you have tried; ask for a nudge |

## Before the interview: a preparation timeline

- **4 - 6 weeks out**: follow the roadmap; build the error log.
- **2 weeks out**: start timed practice and one mock interview a week.
- **Week of**: light review of patterns; practise your introduction; check the tools (editor, camera, internet).
- **Night before**: sleep. No new topics.

## On the day: a short checklist

- Join early; have water, paper and a pen.
- Test the shared editor and your microphone.
- Take a breath before you start; speaking slowly is fine.
- If you blank, restate the problem; the answer often appears while you talk.

## After the interview

Write down the questions and what went well or badly within an hour. Whatever the result, this feedback improves the next round. Rejections are common and rarely say anything about your ability; consistent practice is what changes outcomes."""),
 ("tip", "Print the five-step routine (restate, clarify, example, approach, code and test) and keep it beside your keyboard while you practise, until it is automatic."),
], quiz=[
 Q("What is the most common first step in a good interview routine?", ["Restate the problem and clarify it", "Write code immediately", "Ask for the answer", "Pick a language"], 0, "Understanding first prevents solving the wrong problem."),
 Q("Which statement about scoring is true?", ["Communication and testing count, not just the final answer", "Only the optimal solution counts", "Silence is rewarded", "Hints always lose points"], 0, "Rubrics include communication, coding and testing."),
 Q("In a 45-minute round, how much time is typically spent coding?", ["About 15 minutes", "40 minutes", "2 minutes", "None"], 0, "Understanding and planning take as long as coding."),
 Q("What should you do if you blank during the interview?", ["Restate the problem and talk through what you know", "Stop talking", "Leave", "Guess code silently"], 0, "Talking usually reveals the next step."),
])

COMM = L("communicating-while-you-solve", "Communicating while you solve", "free", 16, "", blocks=[
 ("md", """## Why communication is half the interview

An interviewer cannot see your thoughts. If you work silently, they can only judge your final code, and they cannot help you when you wander off course. When you **narrate**, they can follow, give a hint at the right moment, and give you credit for correct reasoning even when your code has a bug. Strong communication also predicts how you will work with teammates, which is what the company is really hiring.

## The five habits

1. **Restate** the problem before solving.
2. **Think aloud** with short sentences.
3. **Ask** clarifying questions and check assumptions.
4. **Check in** before big decisions.
5. **Summarise** what you did and what you would improve.

## Step 1: Restate and clarify

Say the problem back in one or two sentences. Then ask questions that narrow the problem.

**Useful clarifying questions**

- How large can the input be? (This tells you which complexity is acceptable.)
- Can values be negative, zero or duplicated?
- Is the input sorted? Can I modify it, or do I need to preserve it?
- What should I return if there is no answer, or the input is empty?
- Is it one query or many queries on the same data?
- Are there memory limits?

**Sample dialogue**

> **You:** So I get an array of prices and a budget, and I need two different items that add up exactly to the budget, and return their positions. Is that right?
> **Interviewer:** Yes.
> **You:** Can the same price appear twice? And what do I return if no pair exists?
> **Interviewer:** Duplicates are possible. Return -1 if there is no pair.
> **You:** Got it. Let me try a small example first.

## Step 2: Think aloud while you plan

Use short, specific sentences:

- "The brute force checks every pair, which is O(n²). With n up to 100,000 that is too slow."
- "I need a fast way to ask, have I already seen the matching price? A hash map does that in O(1)."
- "So I scan once, and for each price I look up `budget - price`."

Name **trade-offs** explicitly: time versus memory, simplicity versus speed. Interviewers love hearing "I could do X, which costs..., or Y, which costs...; I will choose Y because...".

## Step 3: Check in before committing

Before writing a lot of code, confirm the direction: "I plan to use a hash map from value to index. Does that approach sound reasonable before I code it?" A short nod saves ten minutes if you were heading the wrong way.

## Step 4: Code with narration

You do not need to speak every keystroke. Narrate **intent** at each block: "Now I handle the empty case...", "This loop maintains the invariant that the window has no repeats...". Choose meaningful names; say what a variable means the first time you create it.

## Step 5: Test out loud

Walk through your code with a concrete example and say the values: "At i = 0, x = 2, seen is empty, so I store 2 at index 0..." Then run the edge cases: empty input, one element, duplicates, the largest size, negatives. If you find a bug, say so calmly and fix it; **finding your own bug is a positive signal.**

## Handling hints

Hints are normal. Treat them as collaboration, not failure.

- Repeat the hint in your words: "So you are suggesting I think about what I have already seen..."
- Connect it to your approach: "That would let me replace the inner loop with a lookup."
- Say thank you and continue. Never argue defensively.

## When you are stuck

1. Say it: "I am not sure how to speed this up yet."
2. Say what you have tried and why it is not enough.
3. Return to a **small example** and look for what repeats.
4. Ask a focused question: "Is there an ordering I can exploit?"
5. If time runs out, write the **brute force** and state its cost; a correct slow solution beats nothing.

## Recovering from a mistake

Everyone makes mistakes in interviews. The recovery is what is graded. Pause, say what is wrong, trace one small example to find the cause, fix it, and re-test. Do not apologise repeatedly or rewrite everything in panic.

## Phrases that help

| Situation | Try saying |
|---|---|
| Starting | "Let me restate the problem to make sure I understand it." |
| Clarifying | "What is the maximum size of the input?" |
| Planning | "The simple approach is... It costs...; I think we can do better by..." |
| Choosing | "I will use X because it gives O(1) lookups; the cost is O(n) extra memory." |
| Checking in | "Does this direction make sense before I code it?" |
| Hint | "That is helpful; it suggests..." |
| Bug found | "I see the issue: on an empty list this indexes zero. Let me fix that." |
| Finishing | "Time O(n), space O(n). If memory mattered I could instead..." |

## Anti-patterns to avoid

| Anti-pattern | Why it hurts |
|---|---|
| Long silence | The interviewer cannot help or credit your thinking |
| Narrating every character you type | Noise instead of insight |
| "I do not know" and stopping | Shows no problem-solving process |
| Over-confidence ("this is easy") | Sounds careless; invites tougher follow-ups |
| Blaming the problem or the tool | Signals poor teamwork |
| Talking over the interviewer | Misses hints and questions |

## Practising communication

- **Record yourself** solving a problem and listen back: where did you go silent or ramble?
- **Mock interviews** with a friend or a peer group; swap roles so you also learn what a good interviewer notices.
- **Explain solved problems** in two minutes as if teaching a classmate.
- **Rubber-duck** your approach before writing code, every time you practise.

## Remote interviews

Look at the camera when explaining, keep a notepad nearby, and say what you are doing during pauses ("I am drawing an example on paper"). If the connection drops, calmly say so and resume where you left off."""),
 ("tip", "Practise the opening line until it is automatic: \"Let me restate the problem, and then I would like to ask a couple of questions.\""),
], quiz=[
 Q("Why narrate your reasoning?", ["So the interviewer can follow and help, and credit your thinking", "To fill time", "To hide bugs", "Because silence is forbidden"], 0, "Visible reasoning earns credit and enables hints."),
 Q("A good clarifying question about input size helps you decide:", ["Which complexity is acceptable", "Which language to use", "The editor theme", "The interviewer's name"], 0, "Constraints tell you whether O(n²) is fine."),
 Q("When you find a bug in your own code, you should:", ["Say so calmly and fix it", "Hide it", "Restart the interview", "Blame the problem"], 0, "Catching your own bug is a positive signal."),
 Q("If time is running out and you have no optimal idea, you should:", ["Write and explain the correct brute force", "Leave the code empty", "Guess", "Stop talking"], 0, "A correct slow solution is better than nothing."),
 Q("How should you respond to a hint?", ["Restate it and connect it to your approach", "Argue", "Ignore it", "Apologise repeatedly"], 0, "Hints are collaboration."),
])

PATTERN = L("recognising-the-pattern", "Recognising the pattern", "free", 20, "", blocks=[
 ("md", """## Why patterns matter

There are thousands of interview problems but only a few dozen **patterns** behind them. When you can say "this is a sliding window problem", you already know the shape of the solution and most of the code. Instead of memorising answers, you train yourself to **spot clues** in a problem statement and map them to a technique. This lesson is your decoder. Come back to it as you finish each chapter.

## The three-step habit

1. **Read for clues**: what does the input look like (sorted, string, graph, intervals)? What is asked (count, longest, shortest, minimum possible value, is it possible)?
2. **Use the constraints**: the input size tells you the target complexity.
3. **Test the pattern on a tiny example** before committing.

## Step 1: Clue to pattern table

| Clue in the problem | Likely pattern | Chapter |
|---|---|---|
| "Find two numbers that add up to..." and the array is **sorted** | Two pointers | Two Pointers |
| "Find two numbers that add up to..." and the array is **not sorted** | Hash map of seen values | Hash Tables |
| "Longest / shortest **contiguous** part where a rule holds" | Sliding window | Sliding Window |
| "Sum of a **range**" asked many times | Prefix sums | Prefix Sum |
| "Number of subarrays with sum k" (negatives allowed) | Prefix sums + hash map | Prefix Sum |
| "Maximum sum **subarray**" | Kadane's algorithm | Kadane's Algorithm |
| Sorted input, or "find the **minimum value** that still works" | Binary search (on the answer) | Binary Search |
| "Next greater / smaller element", spans, brackets that must match | Stack, monotonic stack | Stacks |
| "k-th largest", "top k", "merge k sorted lists" | Heap | Heaps |
| Overlapping time ranges, merging, scheduling | Intervals: sort by start | Intervals |
| "All subsets / permutations / combinations", small n | Backtracking | Recursion and Backtracking |
| Tree traversal, depth, path sums | Recursion (DFS) on trees | Binary Tree |
| "Shortest path", "fewest steps" with equal costs | BFS | Graphs |
| "Is there a path", "count regions", flood fill | DFS or union-find | Graphs |
| "Order tasks with prerequisites" | Topological sort | Graphs |
| Weighted shortest path, non-negative costs | Dijkstra | Graphs |
| "Count the ways", "minimum/maximum cost" with overlapping subproblems | Dynamic programming | Dynamic Programming |
| "Choose the best option at each step" and a proof it is safe | Greedy | Greedy |
| Prefix lookups, autocomplete, word dictionaries | Trie | Tries |
| Linked list: middle, cycle, reverse | Fast/slow pointers, in-place reversal | Linked List |
| Frequency of items, anagrams, duplicates | Hash map or counting array | Hash Tables |
| Bits, powers of two, "single number among pairs" | Bit manipulation, XOR | Bit Manipulation |
| Grid problems: islands, spirals, rotate | DFS/BFS or index arithmetic | Matrix, Graphs |

## Step 2: What the constraints tell you

Computers perform roughly 10⁸ simple operations per second, so the size `n` hints at the intended complexity.

| Constraint on n | Acceptable complexity | Typical techniques |
|---|---|---|
| n ≤ 10 - 12 | O(n!), O(2ⁿ · n) | Brute force, backtracking |
| n ≤ 20 - 25 | O(2ⁿ) | Bitmask subsets, backtracking |
| n ≤ 500 | O(n³) | Triple loops, Floyd-Warshall |
| n ≤ 5,000 | O(n²) | Nested loops, simple DP tables |
| n ≤ 10⁵ - 10⁶ | O(n log n) or O(n) | Sorting, heaps, hashing, two pointers, binary search |
| n ≤ 10⁹ | O(log n) or O(1) | Math, binary search |

Rule of thumb: **if n is 100,000, a nested loop will not pass.** Look for a pattern that drops a factor of n.

## Step 3: Test on a small example

Pick an example with 5 to 8 items and apply your guessed pattern by hand. If it works naturally and you can see why each step is correct, go ahead. If you keep needing special cases, the pattern may be wrong.

## Worked examples

### Example 1: Pair that sums to a target

*Clues:* two numbers, a sum, an unsorted array of up to 100,000 values.

- Brute force: every pair, O(n²) - too slow for n = 100,000.
- Pattern: for each value we need to ask "have I seen `target - value`?" That is a lookup, so a **hash map** gives O(n).

Practise it: [Pair With Target Sum](/problems/pair-with-target-sum).

### Example 2: Longest stretch with a rule

*Clues:* "longest", "contiguous", a rule about the window (for example no repeated letters).

- The window can grow on the right and must shrink on the left when the rule breaks.
- Pattern: **sliding window**, O(n).

Practise it: [Longest Unique Stretch](/problems/longest-unique-stretch) and [Calm Stretch](/problems/calm-stretch).

### Example 3: Smallest value that works

*Clues:* "minimum capacity such that it finishes in d days". No array to search, but answers to "does capacity c work?" go no, no, no, yes, yes, yes.

- Pattern: **binary search on the answer**, with a greedy check for each guess.

Practise it: [Minimum Ship Capacity](/problems/minimum-ship-capacity).

### Example 4: Fewest steps in a maze

*Clues:* grid, "minimum number of moves", every move costs the same.

- Pattern: **BFS** from the start; the first time you reach the exit is the shortest.

Practise it: [Shortest Maze Path](/problems/shortest-maze-path).

### Example 5: Count the ways

*Clues:* "number of ways to climb", "fewest coins to make an amount", choices that repeat in smaller forms.

- Pattern: **dynamic programming**. Define the state, write the recurrence, fill from small to large.

Practise it: [Staircase Ways](/problems/staircase-ways) and [Fewest Coins](/problems/fewest-coins).

## If no pattern fits

1. **Solve a smaller version** by hand and watch what you do.
2. **Sort the input** and see whether the structure becomes obvious.
3. **Think about what you keep recomputing**; store it (hash map, prefix array, memo).
4. **Try a different representation**: a graph, a tree of choices, a set of intervals.
5. **Start with brute force**; improvements often reveal themselves.

## Common traps

| Trap | Reality |
|---|---|
| Matching on keywords only | "Subarray" does not always mean sliding window (negatives break it); check the conditions |
| Using greedy without proof | Test it on small counterexamples first |
| Forcing DP | If subproblems do not overlap, simple recursion or greedy may fit |
| Ignoring the constraints | They are the biggest hint you get for free |

## Build your own pattern notebook

For every problem you solve, record three lines: the **clue**, the **pattern**, and the **trick** (the one idea that made it work). Review the notebook before interviews; it becomes your personal decoder."""),
 ("viz", "two-pointers", "1, 3, 4, 6, 8, 11, 15", "14"),
 ("md", """The visualization above is the two-pointers pattern on a sorted array: the clue was *sorted input and a pair with a given sum*. Watch how the pointers move and why one move can never skip the answer.

Next: use the matching chapters to learn each technique in depth."""),
], quiz=[
 Q("\"Longest contiguous part where a rule holds\" most suggests:", ["Sliding window", "Topological sort", "Trie", "Segment tree"], 0, "A window grows and shrinks to keep the rule true."),
 Q("n = 100,000. Which complexity is generally acceptable?", ["O(n log n)", "O(n²)", "O(n!)", "O(2ⁿ)"], 0, "Around 10^5 inputs, nested loops are too slow."),
 Q("\"Minimum value that still works\" with a yes/no check suggests:", ["Binary search on the answer", "Bubble sort", "A queue only", "Recursion without a base case"], 0, "The check is monotone: no, no, yes, yes."),
 Q("Shortest path where every move costs the same:", ["BFS", "DFS", "Sorting", "Two pointers"], 0, "BFS explores in order of distance."),
 Q("What should you do first when no pattern is obvious?", ["Solve a smaller example by hand", "Give up", "Guess a pattern", "Skip the constraints"], 0, "Small examples reveal structure."),
])
