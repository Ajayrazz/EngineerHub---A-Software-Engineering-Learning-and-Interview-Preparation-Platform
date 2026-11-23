/**
 * Headings of a markdown text for the "On this page" list. The reader's Markdown component derives the
 * same ids (same slug rule, same de-duplication), so the links land on the rendered headings.
 */
export type TocItem = { level: number; id: string; text: string };

export function headingSlug(text: string): string {
  return (
    text
      .toLowerCase()
      .normalize("NFKD")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "") || "section"
  );
}

/** Plain text of one markdown heading line's content (inline syntax removed). */
export function plainHeading(raw: string): string {
  return raw
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[`*_~]/g, "")
    .replace(/\s+#+\s*$/, "")
    .trim();
}

/** De-duplicating id generator, one per rendered block. */
export function idMaker(prefix: string) {
  const seen = new Map<string, number>();
  return (text: string) => {
    const base = `${prefix}-${headingSlug(text)}`;
    const n = seen.get(base) ?? 0;
    seen.set(base, n + 1);
    return n === 0 ? base : `${base}-${n}`;
  };
}

export function extractHeadings(markdown: string, prefix: string, maxLevel = 3): TocItem[] {
  const make = idMaker(prefix);
  const out: TocItem[] = [];
  let fence: string | null = null;
  for (const line of markdown.split(/\r?\n/)) {
    const f = /^\s{0,3}(`{3,}|~{3,})/.exec(line);
    if (f) {
      if (fence === null) fence = f[1][0];
      else if (f[1][0] === fence) fence = null;
      continue;
    }
    if (fence !== null) continue;
    const h = /^\s{0,3}(#{1,6})\s+(.+?)\s*$/.exec(line);
    if (!h) continue;
    const text = plainHeading(h[2]);
    if (text === "") continue;
    const id = make(text); // every heading consumes an id, so numbering matches the renderer
    if (h[1].length <= maxLevel) out.push({ level: h[1].length, id, text });
  }
  return out;
}
