import Link from "next/link";
import {
  Binary,
  Bot,
  Boxes,
  Brain,
  Cloud,
  Code2,
  Cpu,
  Database,
  GitBranch,
  Globe,
  LineChart,
  Network,
  Server,
  Shield,
  Terminal,
  type LucideIcon,
} from "lucide-react";
import { TierBadge } from "@/components/reader/ui";

const ICONS: Record<string, LucideIcon> = {
  "system-design": Server,
  "low-level-design": Boxes,
  databases: Database,
  python: Terminal,
  java: Code2,
  git: GitBranch,
  "web-apis": Globe,
  "machine-learning": Brain,
  "deep-learning": Cpu,
  "llm-engineering": Bot,
  "data-science": LineChart,
  "data-engineering": Database,
  cybersecurity: Shield,
  "application-security": Shield,
  networking: Network,
  "cloud-devops": Cloud,
  "operating-systems": Cpu,
};
// A fixed set of cool-toned gradients, chosen by a stable hash so each course keeps its colour.
const BANNERS = [
  "from-blue-600/40 via-indigo-500/20 to-transparent",
  "from-sky-500/40 via-blue-500/20 to-transparent",
  "from-cyan-500/35 via-sky-500/20 to-transparent",
  "from-indigo-500/40 via-blue-600/20 to-transparent",
  "from-blue-500/40 via-cyan-500/20 to-transparent",
];
const hash = (s: string) => [...s].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);

export function CourseCard({
  slug,
  title,
  description,
  topicSlug,
  topicName,
  difficulty,
  lessonCount,
  tier,
}: {
  slug?: string;
  title?: string;
  description?: string;
  topicSlug?: string;
  topicName?: string;
  difficulty?: string;
  lessonCount?: number;
  tier?: string;
}) {
  const Icon = (topicSlug && ICONS[topicSlug]) || Binary;
  return (
    <li className="group card card-hover flex flex-col overflow-hidden" data-testid="course-card">
      <div
        className={`relative flex h-24 items-center bg-gradient-to-br ${BANNERS[hash(slug ?? "") % BANNERS.length]} px-5`}
      >
        <span className="grid h-12 w-12 place-items-center rounded-xl border bg-background/60 text-accent-2 backdrop-blur transition group-hover:scale-110">
          <Icon size={24} aria-hidden />
        </span>
        <span className="ml-auto">
          <TierBadge tier={tier} />
        </span>
      </div>
      <div className="flex flex-1 flex-col gap-2 p-5">
        <Link href={`/courses/${slug}`} className="text-lg font-semibold hover:text-accent-2">
          {title}
        </Link>
        <p className="line-clamp-3 text-sm text-muted">{description}</p>
        <p className="mt-auto flex flex-wrap items-center gap-2 pt-3 text-xs text-muted">
          {topicName && <span className="chip">{topicName}</span>}
          <span className="chip capitalize">{difficulty}</span>
          <span>{lessonCount} lessons</span>
        </p>
      </div>
    </li>
  );
}
