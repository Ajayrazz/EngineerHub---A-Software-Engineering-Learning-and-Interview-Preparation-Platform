"use client";

import { useState } from "react";
import { btn } from "@/components/admin/ui";

/** Hints are revealed one at a time so a learner can stop before the spoiler they do not want. */
export function Hints({ hints }: { hints: string[] }) {
  const [shown, setShown] = useState(0);
  if (hints.length === 0) return null;
  return (
    <section aria-label="Hints" className="flex flex-col gap-2">
      <h2 className="text-lg font-medium">Hints</h2>
      <ol className="list-decimal space-y-1 pl-6 text-sm" data-testid="hints">
        {hints.slice(0, shown).map((h, i) => (
          <li key={i}>{h}</li>
        ))}
      </ol>
      {shown < hints.length && (
        <div>
          <button className={btn} onClick={() => setShown((n) => n + 1)} data-testid="hint-next">
            {shown === 0 ? "Show a hint" : "Show the next hint"} ({shown}/{hints.length})
          </button>
        </div>
      )}
    </section>
  );
}
