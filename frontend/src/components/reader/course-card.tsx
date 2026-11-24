import Link from "next/link";
import { CourseArt } from "@/components/reader/course-art";
import { TierBadge } from "@/components/reader/ui";
import { Tilt } from "@/components/tilt";

export function CourseCard({
  slug,
  title,
  description,
  topicName,
  difficulty,
  lessonCount,
  tier,
}: {
  slug?: string;
  title?: string;
  description?: string;
  topicName?: string;
  difficulty?: string;
  lessonCount?: number;
  tier?: string;
}) {
  return (
    <li data-testid="course-card" className="[perspective:900px]">
      <Tilt className="group card card-hover flex h-full flex-col">
        <Link href={`/courses/${slug}`} className="flex h-full flex-col" aria-label={title}>
          <div className="relative h-36 overflow-hidden rounded-t-xl bg-gradient-to-br from-blue-950 via-background to-blue-950/60 p-2">
            <div className="absolute inset-0 opacity-60 [background:radial-gradient(60%_80%_at_50%_0%,rgba(59,130,246,.35),transparent)]" />
            <div className="relative h-full transition duration-300 [transform:translateZ(24px)] group-hover:scale-105">
              <CourseArt slug={slug ?? ""} />
            </div>
            <span className="absolute right-3 top-3">
              <TierBadge tier={tier} />
            </span>
          </div>
          <div className="flex flex-1 flex-col gap-2 p-5">
            <h3 className="text-lg font-semibold group-hover:text-accent-2">{title}</h3>
            <p className="line-clamp-3 text-sm text-muted">{description}</p>
            <p className="mt-auto flex flex-wrap items-center gap-2 pt-3 text-xs text-muted">
              {topicName && <span className="chip">{topicName}</span>}
              <span className="chip capitalize">{difficulty}</span>
              <span>{lessonCount} lessons</span>
            </p>
          </div>
        </Link>
      </Tilt>
    </li>
  );
}
