import { Markdown } from "@/components/markdown";
import { QuizBlock } from "@/components/reader/quiz-block";
import { VizPlayer } from "@/components/viz/viz-player";
import type { PublicQuestion } from "@/lib/quiz/logic";

type Block = { type?: string; data?: Record<string, unknown> };
const str = (v: unknown) => (typeof v === "string" ? v : "");

/** Renders a published lesson's blocks. Ids of markdown headings are `b<index>-<slug>` (see lib/cms/toc). */
export function LessonBlocks({
  blocks,
  lessonId,
  csrfToken,
  loginHref,
}: {
  blocks: Block[];
  lessonId: string;
  csrfToken: string | null;
  loginHref: string;
}) {
  return (
    <div className="flex flex-col gap-4" data-testid="lesson-body">
      {blocks.map((b, i) => {
        if (b.type === "markdown")
          return <Markdown key={i} text={str(b.data?.text)} idPrefix={`b${i}`} />;
        if (b.type === "code")
          return (
            <figure key={i}>
              <pre className="overflow-x-auto rounded bg-black/10 p-3 text-xs dark:bg-white/15">
                <code data-language={str(b.data?.language)}>{str(b.data?.code)}</code>
              </pre>
              {str(b.data?.caption) && (
                <figcaption className="text-xs opacity-70">{str(b.data?.caption)}</figcaption>
              )}
            </figure>
          );
        if (b.type === "callout")
          return (
            <aside
              key={i}
              className="rounded-xl border bg-surface/70-l-4 p-3 text-sm"
              data-kind={str(b.data?.kind)}
            >
              <strong className="capitalize">{str(b.data?.kind)}: </strong>
              {str(b.data?.text)}
            </aside>
          );
        if (b.type === "viz")
          return (
            <VizPlayer
              key={i}
              algorithm={str(b.data?.algorithm)}
              input={str(b.data?.input)}
              param={str(b.data?.param)}
              allowCustom
            />
          );
        if (b.type === "quiz")
          return (
            <QuizBlock
              key={str(b.data?.quizId) || i}
              quizId={str(b.data?.quizId)}
              title={str(b.data?.title) || undefined}
              questions={
                Array.isArray(b.data?.questions) ? (b.data.questions as PublicQuestion[]) : []
              }
              lessonId={lessonId}
              csrfToken={csrfToken}
              loginHref={loginHref}
            />
          );
        return null;
      })}
    </div>
  );
}
