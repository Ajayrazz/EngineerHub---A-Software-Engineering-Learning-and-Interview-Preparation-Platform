import { Binary, Boxes, Database, Hash, Layers, ListOrdered, type LucideIcon } from "lucide-react";
import { Tilt } from "@/components/tilt";

type Tool = { need: string; name: string; icon: LucideIcon };
type Lang = { name: string; facts: string[]; tools: Tool[] };

const NEEDS = {
  array: { need: "Dynamic array", icon: ListOrdered },
  map: { need: "Hash map", icon: Database },
  set: { need: "Hash set", icon: Hash },
  sorted: { need: "Sorted map / set", icon: Binary },
  linear: { need: "Stack · queue · deque", icon: Layers },
  heap: { need: "Heap / priority queue", icon: Boxes },
};
const t = (k: keyof typeof NEEDS, name: string): Tool => ({ ...NEEDS[k], name });

/** What each language calls the structures every DSA problem needs (the facts are explained in the lesson). */
const LANGS: Record<string, Lang> = {
  "java-for-dsa": {
    name: "Java",
    facts: ["Compiled to JVM bytecode", "Statically typed", "Garbage collected", "Built-in heap"],
    tools: [
      t("array", "ArrayList<T>"),
      t("map", "HashMap<K,V>"),
      t("set", "HashSet<T>"),
      t("sorted", "TreeMap · TreeSet"),
      t("linear", "ArrayDeque<T>"),
      t("heap", "PriorityQueue<T>"),
    ],
  },
  "python-for-dsa": {
    name: "Python",
    facts: ["Interpreted", "Dynamically typed", "Unlimited integers", "Built-in heap (heapq)"],
    tools: [
      t("array", "list"),
      t("map", "dict"),
      t("set", "set"),
      t("sorted", "bisect + list"),
      t("linear", "list · deque"),
      t("heap", "heapq"),
    ],
  },
  "cpp-for-dsa": {
    name: "C++",
    facts: [
      "Compiled to machine code",
      "Statically typed",
      "Manual control of memory",
      "STL containers",
    ],
    tools: [
      t("array", "vector<T>"),
      t("map", "unordered_map"),
      t("set", "unordered_set"),
      t("sorted", "map · set"),
      t("linear", "stack · queue · deque"),
      t("heap", "priority_queue"),
    ],
  },
  "csharp-for-dsa": {
    name: "C#",
    facts: ["Compiled to .NET IL", "Statically typed", "Garbage collected", "LINQ queries"],
    tools: [
      t("array", "List<T>"),
      t("map", "Dictionary<K,V>"),
      t("set", "HashSet<T>"),
      t("sorted", "SortedDictionary · SortedSet"),
      t("linear", "Stack · Queue"),
      t("heap", "PriorityQueue<E,P>"),
    ],
  },
  "go-for-dsa": {
    name: "Go",
    facts: ["Compiled, fast build", "Statically typed", "Slices and maps built in", "No classes"],
    tools: [
      t("array", "slice []T"),
      t("map", "map[K]V"),
      t("set", "map[T]struct{}"),
      t("sorted", "sort package"),
      t("linear", "slice · container/list"),
      t("heap", "container/heap"),
    ],
  },
  "javascript-for-dsa": {
    name: "JavaScript",
    facts: ["Interpreted + JIT", "Dynamically typed", "One Number type", "No built-in heap"],
    tools: [
      t("array", "Array"),
      t("map", "Map"),
      t("set", "Set"),
      t("sorted", "sorted Array + search"),
      t("linear", "Array (+ head index)"),
      t("heap", "write your own MinHeap"),
    ],
  },
  "typescript-for-dsa": {
    name: "TypeScript",
    facts: [
      "JavaScript + static types",
      "Types erased at run time",
      "strict null checks",
      "Generic data structures",
    ],
    tools: [
      t("array", "Array<T>"),
      t("map", "Map<K,V>"),
      t("set", "Set<T>"),
      t("sorted", "sorted T[] + search"),
      t("linear", "T[] · Queue<T>"),
      t("heap", "MinHeap<T> (yours)"),
    ],
  },
};

export function hasLanguageHero(slug: string): boolean {
  return slug in LANGS;
}

/** A tilting "toolbox" of the structures a language offers, shown above each language lesson. */
export function LanguageHero({ slug }: { slug: string }) {
  const lang = LANGS[slug];
  if (!lang) return null;
  return (
    <section
      aria-label={`${lang.name} toolbox`}
      className="hero-glow relative overflow-hidden rounded-2xl border p-6"
      data-testid="language-hero"
    >
      <div className="stars" aria-hidden />
      <div className="relative">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-2xl font-bold tracking-tight">
            {lang.name} <span className="text-gradient">toolbox</span>
          </h2>
          {lang.facts.map((f) => (
            <span key={f} className="chip">
              {f}
            </span>
          ))}
        </div>
        <p className="mt-1 text-sm text-muted">
          What {lang.name} calls the six structures almost every problem needs.
        </p>
        <ul className="mt-4 grid gap-4 [perspective:900px] sm:grid-cols-2 lg:grid-cols-3">
          {lang.tools.map((tool) => (
            <li key={tool.need}>
              <Tilt className="glass reveal h-full p-4">
                <p className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted">
                  <tool.icon size={14} aria-hidden className="text-accent-2" />
                  {tool.need}
                </p>
                <p className="mt-2 font-mono text-base font-semibold text-accent-2">{tool.name}</p>
              </Tilt>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
