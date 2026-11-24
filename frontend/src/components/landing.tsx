import Link from "next/link";
import type { ReactNode } from "react";

export function Section({
  id,
  eyebrow,
  title,
  lead,
  children,
  tint = false,
}: {
  id?: string;
  eyebrow?: string;
  title: ReactNode;
  lead?: string;
  children: ReactNode;
  tint?: boolean;
}) {
  return (
    <section id={id} className={`border-b ${tint ? "bg-surface/60" : ""}`}>
      <div className="mx-auto w-full max-w-6xl px-6 py-20">
        <div className="mx-auto mb-12 max-w-2xl text-center">
          {eyebrow && <span className="chip">{eyebrow}</span>}
          <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">{title}</h2>
          {lead && <p className="mt-3 text-muted">{lead}</p>}
        </div>
        {children}
      </div>
    </section>
  );
}

const TRACKS = [
  {
    t: "Data structures & algorithms",
    d: "Arrays to graphs and dynamic programming, built up in a sensible order.",
    tag: "Core",
  },
  {
    t: "System design",
    d: "From requirements to trade-offs: how real systems are shaped and why.",
    tag: "Design",
  },
  {
    t: "Object-oriented design",
    d: "Model problems cleanly and talk through your class design with confidence.",
    tag: "Design",
  },
  {
    t: "Interview strategy",
    d: "How to approach a problem, communicate and recover when you are stuck.",
    tag: "Strategy",
  },
  {
    t: "Programming fundamentals",
    d: "The language and complexity basics every later topic leans on.",
    tag: "Basics",
  },
  {
    t: "Databases & backend",
    d: "Schemas, indexes, transactions and the APIs that sit on top.",
    tag: "Backend",
  },
];

export function Tracks() {
  return (
    <Section
      id="courses"
      eyebrow="Explore courses"
      title="Everything you need, in one learning path"
      lead="Pick a track, follow the chapters in order, and see your progress as you go."
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TRACKS.map((c) => (
          <Link key={c.t} href="/courses" className="card card-hover block p-6">
            <span className="chip border-accent/40 text-accent">{c.tag}</span>
            <h3 className="mt-4 text-lg font-semibold">{c.t}</h3>
            <p className="mt-2 text-sm text-muted">{c.d}</p>
            <span className="mt-4 inline-block text-sm text-accent">Browse courses →</span>
          </Link>
        ))}
      </div>
    </Section>
  );
}

const PATTERNS = [
  "Two pointers",
  "Sliding window",
  "Binary search",
  "Prefix sums",
  "Fast & slow pointers",
  "Monotonic stack",
  "Heap / top-K",
  "Intervals",
  "Backtracking",
  "BFS / DFS",
  "Union-find",
  "Topological sort",
  "1-D dynamic programming",
  "2-D dynamic programming",
  "Tries",
];

export function Patterns() {
  return (
    <Section
      id="patterns"
      tint
      eyebrow="Patterns"
      title={
        <>
          Master the pattern, <span className="text-accent">not just the problem</span>
        </>
      }
      lead="Problems repeat in shape. Learn to recognise the shape and the solution follows."
    >
      <div className="flex flex-wrap justify-center gap-3">
        {PATTERNS.map((p) => (
          <span key={p} className="card px-4 py-2 text-sm">
            {p}
          </span>
        ))}
      </div>
      <p className="mt-10 text-center">
        <Link href="/problems" className="btn-primary">
          Start practising
        </Link>
      </p>
    </Section>
  );
}

const FEATURES = [
  {
    t: "Step-by-step visualizations",
    d: "Watch an algorithm run one step at a time. Step, play, scrub, or try your own input.",
  },
  {
    t: "Chapter quizzes",
    d: "Check your understanding right inside the lesson, with explanations after you answer.",
  },
  {
    t: "Progress you can see",
    d: "Mark lessons complete, resume where you left off and track streaks on your dashboard.",
  },
  {
    t: "Hints, not spoilers",
    d: "Problems reveal hints in stages so you get unstuck without losing the learning.",
  },
  { t: "Bookmarks", d: "Save lessons and problems to come back to before an interview." },
  {
    t: "Fair, secure access",
    d: "Free previews on premium lessons, clear pricing in rupees, and receipts for everything.",
  },
];

export function Features() {
  return (
    <Section
      id="features"
      eyebrow="Built for focus"
      title="Focused, interactive learning"
      lead="Tools that keep you reading less passively and thinking more."
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((f) => (
          <div key={f.t} className="card p-6">
            <div aria-hidden className="mb-4 h-1 w-10 rounded-full bg-accent" />
            <h3 className="font-semibold">{f.t}</h3>
            <p className="mt-2 text-sm text-muted">{f.d}</p>
          </div>
        ))}
      </div>
    </Section>
  );
}

const STEPS = [
  {
    n: "1",
    t: "Learn the idea",
    d: "Read a short lesson, watch the algorithm step through, and answer a quiz.",
  },
  {
    n: "2",
    t: "Practise it",
    d: "Open a related problem, use hints if needed and track what you solved.",
  },
  {
    n: "3",
    t: "Review and repeat",
    d: "Your dashboard shows what is done, what is next and what to revisit.",
  },
];

export function HowItWorks() {
  return (
    <Section
      id="how-it-works"
      tint
      eyebrow="How it works"
      title="Learn. Practise. Get interview-ready."
    >
      <ol className="grid gap-4 md:grid-cols-3">
        {STEPS.map((s) => (
          <li key={s.n} className="card p-6">
            <span className="grid h-9 w-9 place-items-center rounded-full bg-accent font-bold text-accent-ink">
              {s.n}
            </span>
            <h3 className="mt-4 font-semibold">{s.t}</h3>
            <p className="mt-2 text-sm text-muted">{s.d}</p>
          </li>
        ))}
      </ol>
    </Section>
  );
}

const PRACTICE = [
  {
    t: "Practice problems",
    d: "A curated bank with difficulty, topics and your personal status.",
    href: "/problems",
  },
  {
    t: "Write real code",
    d: "An in-browser code runner is on the roadmap; until then, solve and self-check.",
    href: "/problems",
  },
  {
    t: "Mock interviews",
    d: "Timed practice sessions are planned; nothing here claims they exist yet.",
    href: "/problems",
  },
];

export function Practice() {
  return (
    <Section
      id="practice"
      eyebrow="Practise"
      title="Practice, write code, rehearse the interview"
      lead="We say plainly what is available today and what is still being built."
    >
      <div className="grid gap-4 md:grid-cols-3">
        {PRACTICE.map((p) => (
          <Link key={p.t} href={p.href} className="card card-hover block p-6">
            <h3 className="font-semibold">{p.t}</h3>
            <p className="mt-2 text-sm text-muted">{p.d}</p>
          </Link>
        ))}
      </div>
    </Section>
  );
}

const PRINCIPLES = [
  {
    t: "Original content",
    d: "Every lesson and problem is written for EngineerHub or licensed; nothing is copied.",
  },
  {
    t: "Honest by default",
    d: "No invented testimonials or inflated numbers. What you see is what exists.",
  },
  {
    t: "Your data stays yours",
    d: "Sign-in is handled by a dedicated identity service and your access is checked on the server.",
  },
];

export function Community() {
  return (
    <Section
      id="community"
      tint
      eyebrow="Our promise"
      title="Built on trust"
      lead="Reviews and community stories will appear here once real learners share them."
    >
      <div className="grid gap-4 md:grid-cols-3">
        {PRINCIPLES.map((p) => (
          <div key={p.t} className="card p-6">
            <h3 className="font-semibold text-accent">{p.t}</h3>
            <p className="mt-2 text-sm text-muted">{p.d}</p>
          </div>
        ))}
      </div>
    </Section>
  );
}

export const FAQS = [
  {
    q: "Is EngineerHub free?",
    a: "Many lessons and problems are free. Premium content unlocks with a pass or a subscription, and premium lessons offer a free preview.",
  },
  {
    q: "How much does Premium cost?",
    a: "Plans are priced in Indian rupees, with monthly and yearly options, either as a one-time pass or an auto-renewing subscription. See the pricing page for current amounts.",
  },
  {
    q: "Can I get a refund?",
    a: "Refund requests are reviewed under the refund policy shown at checkout. Approved refunds are issued with a credit note.",
  },
  {
    q: "Is my payment safe?",
    a: "Payments are handled by Razorpay. We never see or store your card details. The platform currently runs in payment test mode.",
  },
  {
    q: "Do I need an account?",
    a: "You can read free lessons without one. An account is needed to track progress, bookmark and buy Premium.",
  },
  {
    q: "Can I run code in the browser?",
    a: "Not yet. A secure code runner is planned. Today you can read problems, use hints and track your status.",
  },
  {
    q: "Which topics are covered?",
    a: "Data structures and algorithms, system design, object-oriented design, interview strategy and backend fundamentals, growing over time.",
  },
  {
    q: "Where does the content come from?",
    a: "All of it is original or properly licensed. We do not copy third-party problems or lessons.",
  },
];

export function Faq() {
  return (
    <Section id="faq" eyebrow="FAQ" title="Frequently asked questions">
      <div className="mx-auto max-w-3xl space-y-3">
        {FAQS.map((f) => (
          <details key={f.q} className="card group p-5 open:border-accent/50">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-medium">
              {f.q}
              <span aria-hidden className="text-accent transition group-open:rotate-45">
                +
              </span>
            </summary>
            <p className="mt-3 text-sm text-muted">{f.a}</p>
          </details>
        ))}
      </div>
    </Section>
  );
}

export function FinalCta({ signedIn }: { signedIn: boolean }) {
  return (
    <section className="hero-glow">
      <div className="mx-auto flex max-w-3xl flex-col items-center gap-5 px-6 py-20 text-center">
        <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
          Ready to crack your next interview?
        </h2>
        <p className="text-muted">Start with a free lesson today. No card needed.</p>
        <div className="flex gap-3">
          <Link href="/courses" className="btn-primary">
            Start learning
          </Link>
          <Link href={signedIn ? "/dashboard" : "/pricing"} className="btn-ghost">
            {signedIn ? "Open dashboard" : "See pricing"}
          </Link>
        </div>
      </div>
    </section>
  );
}
