import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { Markdown } from "./markdown";

const html = (text: string) => renderToStaticMarkup(<Markdown text={text} />);

describe("Markdown (author content is data, never markup)", () => {
  it("renders ordinary markdown", () => {
    const out = html("# Title\n\nSome **bold** text and `code`.\n\n- a\n- b");
    expect(out).toContain("<h1>Title</h1>");
    expect(out).toContain("<strong>bold</strong>");
    expect(out).toContain("<code>code</code>");
    expect(out).toContain("<li>a</li>");
  });

  it("supports tables (GFM)", () => {
    expect(html("| a | b |\n|---|---|\n| 1 | 2 |")).toContain("<table>");
  });

  it("does not interpret raw HTML", () => {
    for (const hostile of [
      "<script>alert(1)</script>",
      "<img src=x onerror=alert(1)>",
      '<a href="javascript:alert(1)" onclick="x()">click</a>',
      "<iframe src='https://evil.example'></iframe>",
      "<div onmouseover=alert(1)>hover</div>",
    ]) {
      const out = html(hostile);
      // no real element/attribute from the input (escaped text such as "&lt;script&gt;" is fine)
      expect(out, hostile).not.toMatch(/<(script|iframe|img|object|embed)\b/i);
      expect(out, hostile).not.toMatch(/<[a-z][^>]*\son\w+\s*=/i);
      expect(out, hostile).not.toMatch(/<div onmouseover|<a [^>]*href="javascript:/i);
      expect(out, hostile).toContain("&lt;"); // shown as literal text
    }
  });

  it("drops javascript: and data: link targets", () => {
    for (const md of [
      "[x](javascript:alert(1))",
      "[x](JaVaScRiPt:alert(1))",
      "[x](data:text/html;base64,PHNjcmlwdD4=)",
      "[x](vbscript:msgbox(1))",
    ]) {
      const out = html(md);
      expect(out, md).not.toMatch(/href="(javascript|data|vbscript):/i);
    }
  });

  it("marks links safe for external use and keeps https links", () => {
    const out = html("[docs](https://example.com/a)");
    expect(out).toContain('href="https://example.com/a"');
    expect(out).toContain('rel="noopener noreferrer nofollow"');
  });

  it("never renders images (no remote hot-linking)", () => {
    const out = html("![diagram](https://tracker.example/pixel.png)");
    expect(out).not.toContain("<img");
    expect(out).not.toContain("tracker.example");
    expect(out).toContain("[image: diagram]");
  });
});

describe("Markdown heading ids", () => {
  it("adds prefixed, de-duplicated ids only when asked", () => {
    const withIds = renderToStaticMarkup(
      <Markdown text={"## Setup\n\n## Setup\n\n### `Code` heading"} idPrefix="b0" />,
    );
    expect(withIds).toContain('<h2 id="b0-setup">Setup</h2>');
    expect(withIds).toContain('<h2 id="b0-setup-1">Setup</h2>');
    expect(withIds).toContain('id="b0-code-heading"');
    expect(html("## Setup")).not.toContain("id=");
  });
});
