import { BookOpen, Flag, Layers, Rocket, Sprout } from "lucide-react";

/** Phases of the DSA course. Hours follow the roadmap lesson's table; weeks follow its Steady plan. */
const PHASES = [
  {
    key: "foundations",
    title: "Foundations",
    weeks: "Weeks 1-2",
    hours: 16,
    icon: Sprout,
    chapters: [
      "Welcome",
      "Interview Strategy",
      "Programming Fundamentals",
      "DSA Warmup",
      "Big O",
      "Math",
      "Sorting",
    ],
    outcome: "Explain the cost of a loop and write clean basic programs.",
  },
  {
    key: "patterns",
    title: "Core patterns",
    weeks: "Weeks 3-7",
    hours: 38,
    icon: Layers,
    chapters: [
      "Arrays",
      "Strings",
      "Bit Manipulation",
      "Hash Tables",
      "Two Pointers",
      "Sliding Window",
      "Prefix Sum",
      "Kadane",
      "Binary Search",
      "Matrix",
    ],
    outcome: "Recognise the pattern behind most array and string problems.",
  },
  {
    key: "structures",
    title: "Data structures",
    weeks: "Weeks 8-10",
    hours: 36,
    icon: BookOpen,
    chapters: [
      "Linked List",
      "Stacks",
      "Queues",
      "Recursion",
      "Binary Tree",
      "BST",
      "Heaps",
      "Intervals",
      "Tries",
      "Design",
    ],
    outcome: "Choose the right structure and write recursive solutions.",
  },
  {
    key: "advanced",
    title: "Interview-ready",
    weeks: "Weeks 11-12",
    hours: 36,
    icon: Rocket,
    chapters: ["Greedy", "Graphs", "Dynamic Programming", "Advanced Topics", "Mock interviews"],
    outcome: "Solve new medium problems in about 30 minutes while explaining.",
  },
];

/** Animated course roadmap: a drawn path with a travelling marker and milestone cards. Decorative SVG + real text. */
export function RoadmapAnimation({ chapters, lessons }: { chapters: number; lessons: number }) {
  const total = PHASES.reduce((n, p) => n + p.hours, 0);
  const xs = [60, 220, 380, 540];
  return (
    <section aria-label="Course roadmap" className="conic" data-testid="roadmap">
      <div className="conic-in p-6">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-xl font-bold">Your roadmap</h2>
          <p className="text-sm text-muted">
            {chapters} chapters · {lessons} lessons · about {total} hours · 12 weeks at ~8 h a week
          </p>
        </div>

        <svg viewBox="0 0 600 150" className="mt-4 w-full" aria-hidden role="presentation">
          <defs>
            <linearGradient id="road" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stopColor="#2563eb" />
              <stop offset="1" stopColor="#38bdf8" />
            </linearGradient>
          </defs>
          <path
            id="roadPath"
            d="M60 100 C 120 20, 160 20, 220 80 S 320 150, 380 70 S 480 10, 540 60"
            fill="none"
            stroke="#17254a"
            strokeWidth="10"
            strokeLinecap="round"
          />
          <path
            d="M60 100 C 120 20, 160 20, 220 80 S 320 150, 380 70 S 480 10, 540 60"
            fill="none"
            stroke="url(#road)"
            strokeWidth="4"
            strokeLinecap="round"
            className="flow-line"
          />
          {xs.map((x, i) => (
            <g key={x}>
              <circle
                cx={x}
                cy={[100, 80, 70, 60][i]}
                r="14"
                fill="#080d1a"
                stroke="#38bdf8"
                strokeWidth="3"
              >
                <animate
                  attributeName="r"
                  values="14;17;14"
                  dur="2.6s"
                  begin={`${i * 0.5}s`}
                  repeatCount="indefinite"
                />
              </circle>
              <text
                x={x}
                y={[104, 84, 74, 64][i]}
                textAnchor="middle"
                fontSize="12"
                fontWeight="700"
                fill="#e8eaed"
              >
                {i + 1}
              </text>
            </g>
          ))}
          <circle r="6" fill="#bae6fd">
            <animateMotion dur="9s" repeatCount="indefinite" rotate="auto">
              <mpath href="#roadPath" />
            </animateMotion>
          </circle>
          <g transform="translate(556 28)">
            <circle r="13" fill="#2563eb" opacity=".25">
              <animate attributeName="r" values="13;19;13" dur="2s" repeatCount="indefinite" />
            </circle>
            <text textAnchor="middle" y="5" fontSize="14">
              🏁
            </text>
          </g>
        </svg>

        <ol className="mt-2 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {PHASES.map((p, i) => (
            <li
              key={p.key}
              className="glass reveal flex flex-col gap-2 p-4"
              data-testid="roadmap-phase"
            >
              <div className="flex items-center gap-2">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-gradient-to-br from-accent to-accent-2 text-sm font-bold text-white">
                  {i + 1}
                </span>
                <p.icon size={16} aria-hidden className="shrink-0 text-accent-2" />
                <h3 className="font-semibold">{p.title}</h3>
              </div>
              <p className="text-xs text-muted">
                {p.weeks} · about {p.hours} hours
              </p>
              <p className="flex flex-wrap gap-1.5">
                {p.chapters.map((c) => (
                  <span key={c} className="chip">
                    {c}
                  </span>
                ))}
              </p>
              <p className="mt-auto flex items-start gap-1.5 pt-1 text-sm">
                <Flag size={14} aria-hidden className="mt-0.5 shrink-0 text-accent-2" />
                {p.outcome}
              </p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
