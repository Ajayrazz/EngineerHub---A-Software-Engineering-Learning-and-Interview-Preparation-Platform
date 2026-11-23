"use client";

import { useState } from "react";
import type { components } from "@/lib/api/schema";
import { adminFetch } from "./admin-fetch";
import { Notice, btn } from "./ui";

type Result = components["schemas"]["Result"];

/** Re-computes the audit hash chain on the server and reports whether it is intact. */
export function AuditVerify() {
  const [busy, setBusy] = useState(false);
  const [out, setOut] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  async function verify() {
    setBusy(true);
    const r = await adminFetch("", "admin/audit-logs/verify", "GET");
    setBusy(false);
    if (!r.ok) return setOut({ kind: "error", text: r.message });
    const d = r.data as Result;
    setOut(
      d.ok
        ? { kind: "ok", text: `Chain intact: ${d.checked ?? 0} entries verified.` }
        : {
            kind: "error",
            text: `Chain BROKEN at entry ${d.firstBadId}: ${d.problem ?? "unknown problem"}`,
          },
    );
  }
  return (
    <div className="flex flex-col gap-2">
      <div>
        <button className={btn} disabled={busy} onClick={verify} data-testid="audit-verify">
          Verify chain
        </button>
      </div>
      {out && (
        <div data-testid="audit-verify-result">
          <Notice kind={out.kind}>{out.text}</Notice>
        </div>
      )}
    </div>
  );
}
