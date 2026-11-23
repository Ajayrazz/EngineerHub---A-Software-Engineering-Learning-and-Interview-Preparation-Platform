import type { ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

/**
 * Renders author-written markdown safely. Raw HTML in the source is NOT interpreted (react-markdown has
 * no HTML pass without rehype-raw, which we do not use); URL protocols are filtered by react-markdown's
 * default transform (javascript: etc. are dropped); images are not rendered (authors cannot hot-link
 * remote content, which would leak reader IPs); external links get rel="noopener noreferrer nofollow".
 * Used by the CMS preview and the reader, so what authors preview is what learners get.
 */
export function Markdown({ text }: { text: string }) {
  return (
    <div className="prose-sm max-w-none space-y-3 text-sm leading-relaxed [&_code]:rounded [&_code]:bg-black/10 [&_code]:px-1 dark:[&_code]:bg-white/15 [&_h1]:text-2xl [&_h1]:font-semibold [&_h2]:text-xl [&_h2]:font-semibold [&_h3]:text-lg [&_h3]:font-semibold [&_li]:ml-5 [&_ol]:list-decimal [&_pre]:overflow-x-auto [&_pre]:rounded [&_pre]:bg-black/10 [&_pre]:p-3 [&_table]:border-collapse [&_td]:border [&_td]:px-2 [&_th]:border [&_th]:px-2 [&_ul]:list-disc">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children }: { href?: string; children?: ReactNode }) => (
            <a href={href} rel="noopener noreferrer nofollow" className="underline">
              {children}
            </a>
          ),
          img: ({ alt }: { alt?: string }) => <span>{alt ? `[image: ${alt}]` : "[image]"}</span>,
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
}
