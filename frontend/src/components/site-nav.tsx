import Link from "next/link";
import {
  Boxes,
  Building2,
  ChevronDown,
  Code2,
  Coffee,
  Cpu,
  Crown,
  Database,
  FileText,
  FolderOpen,
  GitBranch,
  GraduationCap,
  Layers,
  Mail,
  Map,
  MessageSquareQuote,
  Network,
  Menu,
  Play,
  Plug,
  Radio,
  Search,
  Server,
  Shuffle,
  Star,
  Target,
  Terminal,
  Users,
  Workflow,
  type LucideIcon,
} from "lucide-react";

type Item = { label: string; href?: string; icon: LucideIcon; tone: string; soon?: boolean };
type Group = { title?: string; items: Item[] };
type Menu = { label: string; icon: LucideIcon; groups: Group[]; wide?: boolean };

const q = (text: string) => `/courses?q=${encodeURIComponent(text)}`;

const TONES = {
  blue: "bg-blue-500/20 text-blue-300",
  sky: "bg-sky-400/20 text-sky-300",
  indigo: "bg-indigo-400/20 text-indigo-300",
  cyan: "bg-cyan-400/20 text-cyan-300",
  slate: "bg-slate-400/20 text-slate-300",
};
const t = (k: keyof typeof TONES) => TONES[k];

/** Every link goes to a page that exists; items without content yet are shown as "Soon", not linked. */
const MENUS: Menu[] = [
  {
    label: "Learn",
    icon: GraduationCap,
    wide: true,
    groups: [
      {
        title: "Interview prep",
        items: [
          {
            label: "Data structures & algorithms",
            href: q("algorithms"),
            icon: Network,
            tone: t("blue"),
          },
          { label: "System design", href: q("system design"), icon: Server, tone: t("sky") },
          {
            label: "Low-level design",
            href: q("low-level design"),
            icon: Boxes,
            tone: t("indigo"),
          },
          { label: "Concurrency", href: q("concurrency"), icon: Workflow, tone: t("cyan") },
          {
            label: "Behavioral",
            href: q("behavioral"),
            icon: MessageSquareQuote,
            tone: t("slate"),
          },
        ],
      },
      {
        title: "Backend",
        items: [
          { label: "SQL & databases", href: q("sql"), icon: Database, tone: t("blue") },
          { label: "API design", href: q("api"), icon: Plug, tone: t("sky") },
          { label: "Microservices", href: q("microservices"), icon: Layers, tone: t("indigo") },
          { label: "Messaging & Kafka", href: q("kafka"), icon: Radio, tone: t("cyan") },
        ],
      },
      {
        title: "Fundamentals",
        items: [
          { label: "Java", href: q("java"), icon: Coffee, tone: t("blue") },
          { label: "Python", href: q("python"), icon: Terminal, tone: t("sky") },
          { label: "C++", href: q("c++"), icon: Cpu, tone: t("indigo") },
          { label: "Go", href: q("go"), icon: Code2, tone: t("cyan") },
          { label: "Git", href: q("git"), icon: GitBranch, tone: t("slate") },
        ],
      },
    ],
  },
  {
    label: "Practice",
    icon: Target,
    groups: [
      {
        items: [
          { label: "Problem bank", href: "/problems", icon: Network, tone: t("blue") },
          { label: "Random problem", href: "/problems/random", icon: Shuffle, tone: t("sky") },
          { label: "My problems", href: "/my-problems", icon: Star, tone: t("indigo") },
          { label: "Company-wise problems", icon: Building2, tone: t("cyan"), soon: true },
        ],
      },
    ],
  },
  {
    label: "Interview",
    icon: MessageSquareQuote,
    groups: [
      {
        items: [
          { label: "Coding interview", href: "/problems", icon: Code2, tone: t("blue") },
          {
            label: "System design interview",
            href: q("system design"),
            icon: Server,
            tone: t("sky"),
          },
          {
            label: "Low-level design interview",
            href: q("low-level design"),
            icon: Boxes,
            tone: t("indigo"),
          },
          { label: "Mock interviews", icon: Users, tone: t("cyan"), soon: true },
        ],
      },
    ],
  },
  {
    label: "Resources",
    icon: FolderOpen,
    groups: [
      {
        items: [
          { label: "Visualizations", href: q("visualization"), icon: Play, tone: t("sky") },
          { label: "Plans & pricing", href: "/pricing", icon: Crown, tone: t("blue") },
          { label: "Roadmaps", icon: Map, tone: t("indigo"), soon: true },
          { label: "Resume builder", icon: FileText, tone: t("cyan"), soon: true },
        ],
      },
    ],
  },
  {
    label: "Newsletter",
    icon: Mail,
    groups: [
      {
        items: [
          { label: "All articles", icon: FileText, tone: t("blue"), soon: true },
          { label: "Premium articles", icon: Crown, tone: t("sky"), soon: true },
        ],
      },
    ],
  },
];

const USER_LINKS = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/my-courses", label: "My courses" },
  { href: "/my-problems", label: "My problems" },
  { href: "/billing", label: "My access" },
  { href: "/account", label: "Account" },
];

export function Logo() {
  return (
    <Link href="/" className="flex items-center gap-2 text-lg font-bold tracking-tight">
      <span
        aria-hidden
        className="grid h-7 w-7 place-items-center rounded-md bg-accent font-mono text-sm text-accent-ink"
      >
        {"</>"}
      </span>
      <span>
        Engineer<span className="text-accent">Hub</span>
      </span>
    </Link>
  );
}

function Row({ item }: { item: Item }) {
  const body = (
    <>
      <span
        aria-hidden
        className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${item.tone}`}
      >
        <item.icon size={16} />
      </span>
      <span className="flex-1 whitespace-nowrap">{item.label}</span>
      {item.soon && <span className="chip py-0 text-[10px]">Soon</span>}
    </>
  );
  const base = "flex items-center gap-3 rounded-lg px-2 py-2 text-sm font-medium";
  return item.href ? (
    <Link href={item.href} className={`${base} hover:bg-surface-2`}>
      {body}
    </Link>
  ) : (
    <span className={`${base} cursor-default text-muted`} aria-disabled="true">
      {body}
    </span>
  );
}

function Dropdown({ menu }: { menu: Menu }) {
  const cols = menu.groups.length;
  return (
    <div className="group relative">
      <button
        type="button"
        aria-haspopup="menu"
        className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-foreground/90 group-hover:bg-surface-2 group-hover:text-accent-2 group-focus-within:bg-surface-2 group-focus-within:text-accent-2"
      >
        <menu.icon size={16} aria-hidden className="text-accent-2" />
        {menu.label}
        <ChevronDown
          size={14}
          aria-hidden
          className="opacity-60 transition group-hover:rotate-180"
        />
      </button>
      <div
        className={`invisible absolute left-0 top-full z-40 translate-y-1 pt-2 opacity-0 transition duration-150 group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:visible group-focus-within:translate-y-0 group-focus-within:opacity-100 ${menu.wide ? "w-[min(56rem,92vw)]" : "w-max"}`}
      >
        <div className="overflow-hidden rounded-xl border bg-surface shadow-2xl shadow-black/60">
          <div className={`grid gap-6 p-4 ${cols > 1 ? "md:grid-cols-3" : ""}`}>
            {menu.groups.map((g, i) => (
              <div key={g.title ?? i} className="min-w-52">
                {g.title && (
                  <h3 className="mb-2 border-b px-2 pb-2 text-[11px] font-semibold uppercase tracking-widest text-muted">
                    {g.title}
                  </h3>
                )}
                <ul className="space-y-0.5">
                  {g.items.map((it) => (
                    <li key={it.label}>
                      <Row item={it} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          {menu.wide && (
            <div className="flex items-center justify-between gap-4 border-t bg-background/50 px-5 py-3 text-xs text-muted">
              <span>
                <b className="text-foreground">Looking for more?</b> The full catalog has every
                course, with filters.
              </span>
              <Link href="/courses" className="btn-primary">
                View all courses →
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function SiteNav({ username }: { username?: string | null }) {
  const flat = MENUS.flatMap((m) => m.groups.flatMap((g) => g.items)).filter((i) => i.href);
  const seen = new Set<string>();
  const mobile = flat.filter((i) => (seen.has(i.href!) ? false : (seen.add(i.href!), true)));
  return (
    <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur">
      <nav
        aria-label="Main"
        className="mx-auto flex w-full max-w-7xl items-center gap-3 px-6 py-2.5 text-sm"
      >
        <Logo />
        <div className="ml-3 hidden items-center gap-1 lg:flex">
          {MENUS.map((m) => (
            <Dropdown key={m.label} menu={m} />
          ))}
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Link
            href="/pricing"
            className="hidden items-center gap-1.5 rounded-lg border border-accent/40 bg-accent/10 px-3 py-1.5 text-sm font-medium text-accent-2 hover:bg-accent/20 sm:flex"
          >
            <Crown size={14} aria-hidden /> Premium
          </Link>
          <form action="/courses" method="get" role="search" className="relative hidden md:block">
            <Search
              size={14}
              aria-hidden
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted"
            />
            <input
              name="q"
              maxLength={100}
              placeholder="Search courses"
              aria-label="Search courses"
              className="w-40 rounded-lg border bg-surface py-1.5 pl-8 pr-3 text-sm placeholder:text-muted focus:w-56"
            />
          </form>
          {username ? (
            <div className="group relative">
              <Link
                href="/account"
                className="flex items-center gap-2 rounded-full border px-3 py-1.5 hover:border-accent"
                data-testid="nav-user"
              >
                {username}
              </Link>
              <div className="invisible absolute right-0 top-full z-40 pt-2 opacity-0 transition group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100">
                <ul className="min-w-44 space-y-0.5 rounded-xl border bg-surface p-2 shadow-2xl shadow-black/60">
                  {USER_LINKS.map((l) => (
                    <li key={l.href}>
                      <Link href={l.href} className="block rounded-lg px-3 py-2 hover:bg-surface-2">
                        {l.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ) : (
            <a href="/auth/login" className="btn-primary">
              Log in
            </a>
          )}
          <details className="relative lg:hidden">
            <summary
              className="cursor-pointer list-none rounded-lg border px-3 py-1.5"
              aria-label="Menu"
            >
              <Menu size={16} aria-hidden />
            </summary>
            <ul className="absolute right-0 top-full mt-2 max-h-[70vh] w-64 space-y-0.5 overflow-auto rounded-xl border bg-surface p-2 shadow-2xl">
              {mobile.map((i) => (
                <li key={i.href}>
                  <Link href={i.href!} className="block rounded-lg px-3 py-2 hover:bg-surface-2">
                    {i.label}
                  </Link>
                </li>
              ))}
              {username &&
                USER_LINKS.map((l) => (
                  <li key={`u-${l.href}`}>
                    <Link
                      href={l.href}
                      className="block rounded-lg px-3 py-2 text-muted hover:bg-surface-2"
                    >
                      {l.label}
                    </Link>
                  </li>
                ))}
            </ul>
          </details>
        </div>
      </nav>
    </header>
  );
}
