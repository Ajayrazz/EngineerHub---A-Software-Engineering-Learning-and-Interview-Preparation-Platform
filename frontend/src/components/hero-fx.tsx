"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";

const reduce = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Types and erases each word in turn. The first word is rendered on the server (no layout jump). */
export function Typewriter({ words }: { words: string[] }) {
  const [text, setText] = useState(words[0]);
  useEffect(() => {
    if (reduce()) return;
    let w = 0;
    let n = words[0].length;
    let erasing = true;
    let t: ReturnType<typeof setTimeout>;
    const tick = () => {
      if (erasing) {
        n--;
        if (n <= 0) {
          erasing = false;
          w = (w + 1) % words.length;
        }
      } else {
        n++;
        if (n >= words[w].length) {
          erasing = true;
          t = setTimeout(tick, 1600);
          setText(words[w].slice(0, n));
          return;
        }
      }
      setText(words[w].slice(0, Math.max(n, 0)));
      t = setTimeout(tick, erasing ? 40 : 80);
    };
    t = setTimeout(tick, 1800);
    return () => clearTimeout(t);
  }, [words]);
  return (
    <span className="text-gradient">
      {text}
      <span className="caret" aria-hidden />
    </span>
  );
}

/** Cursor-following light inside a section. */
export function Spotlight({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  return (
    <div
      ref={ref}
      className={className}
      onPointerMove={(e) => {
        const el = ref.current;
        if (!el || reduce()) return;
        const r = el.getBoundingClientRect();
        el.style.setProperty("--mx", `${e.clientX - r.left}px`);
        el.style.setProperty("--my", `${e.clientY - r.top}px`);
      }}
    >
      {children}
    </div>
  );
}
