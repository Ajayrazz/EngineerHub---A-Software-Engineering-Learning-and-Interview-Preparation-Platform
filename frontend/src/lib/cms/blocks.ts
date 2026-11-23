/**
 * Pure editor logic for lesson blocks (no React). The server re-validates everything (BlockValidator);
 * these checks only give authors fast feedback and mirror its limits.
 */
export type BlockType = "markdown" | "code" | "callout";

export type Block = { key: string; type: BlockType; data: Record<string, string> };
export type ApiBlock = { type?: string; data?: Record<string, unknown> };

export const LIMITS = {
  markdown: 50_000,
  code: 20_000,
  callout: 2_000,
  caption: 200,
  blocks: 200,
} as const;
export const LANGUAGES = ["java", "python", "cpp", "text"] as const;
export const CALLOUT_KINDS = ["info", "warning", "tip"] as const;

let counter = 0;
const nextKey = () => `b${Date.now().toString(36)}${(counter++).toString(36)}`;

export function newBlock(type: BlockType): Block {
  const data: Record<string, string> =
    type === "markdown"
      ? { text: "" }
      : type === "code"
        ? { language: "java", code: "", caption: "" }
        : { kind: "info", text: "" };
  return { key: nextKey(), type, data };
}

export function fromApi(blocks: readonly ApiBlock[] | undefined): Block[] {
  return (blocks ?? []).flatMap((b) => {
    if (b.type !== "markdown" && b.type !== "code" && b.type !== "callout") return [];
    const data: Record<string, string> = {};
    for (const [k, v] of Object.entries(b.data ?? {})) data[k] = typeof v === "string" ? v : "";
    return [{ key: nextKey(), type: b.type, data }];
  });
}

/** What the API accepts: only known keys, empty optional values dropped. */
export function toApi(blocks: readonly Block[]): ApiBlock[] {
  return blocks.map((b) => {
    if (b.type === "markdown") return { type: b.type, data: { text: b.data.text ?? "" } };
    if (b.type === "callout")
      return { type: b.type, data: { kind: b.data.kind ?? "info", text: b.data.text ?? "" } };
    const data: Record<string, unknown> = {
      language: b.data.language ?? "text",
      code: b.data.code ?? "",
    };
    if ((b.data.caption ?? "").trim() !== "") data.caption = b.data.caption;
    return { type: b.type, data };
  });
}

export function move<T>(items: readonly T[], index: number, delta: -1 | 1): T[] {
  const to = index + delta;
  if (index < 0 || index >= items.length || to < 0 || to >= items.length) return [...items];
  const copy = [...items];
  [copy[index], copy[to]] = [copy[to], copy[index]];
  return copy;
}

export function remove<T>(items: readonly T[], index: number): T[] {
  return items.filter((_, i) => i !== index);
}

const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;

/** Messages for problems the server would also reject (empty list = fine). */
export function validateBlocks(blocks: readonly Block[]): string[] {
  const errors: string[] = [];
  if (blocks.length > LIMITS.blocks) errors.push(`At most ${LIMITS.blocks} blocks per lesson.`);
  blocks.forEach((b, i) => {
    const n = i + 1;
    const body = b.type === "code" ? b.data.code : b.data.text;
    const max = LIMITS[b.type];
    if (!body || body.trim() === "") errors.push(`Block ${n}: the ${b.type} block is empty.`);
    else if (body.length > max)
      errors.push(`Block ${n}: too long (${body.length}/${max} characters).`);
    if (body && CONTROL.test(body)) errors.push(`Block ${n}: contains a control character.`);
    if (b.type === "code" && !(LANGUAGES as readonly string[]).includes(b.data.language ?? ""))
      errors.push(`Block ${n}: choose a language.`);
    if (b.type === "code" && (b.data.caption ?? "").length > LIMITS.caption)
      errors.push(`Block ${n}: caption is too long.`);
    if (b.type === "callout" && !(CALLOUT_KINDS as readonly string[]).includes(b.data.kind ?? ""))
      errors.push(`Block ${n}: choose a callout kind.`);
  });
  return errors;
}

export function sameBlocks(a: readonly Block[], b: readonly Block[]): boolean {
  return JSON.stringify(toApi(a)) === JSON.stringify(toApi(b));
}
