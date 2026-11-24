"use client";

import { useEffect, useState } from "react";

/** How far down the page the reader is (presentation only). */
export function ReadingProgress() {
  const [pct, setPct] = useState(0);
  useEffect(() => {
    const onScroll = () => {
      const h = document.documentElement;
      const max = h.scrollHeight - h.clientHeight;
      setPct(max > 0 ? Math.min(100, Math.round((h.scrollTop / max) * 100)) : 100);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);
  return (
    <div className="mb-3" data-testid="reading-progress">
      <div className="flex justify-between text-xs text-muted">
        <span>Reading progress</span>
        <span>{pct}%</span>
      </div>
      <progress className="meter mt-1" value={pct} max={100} aria-label="Reading progress" />
    </div>
  );
}
