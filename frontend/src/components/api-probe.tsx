"use client";

import { useState } from "react";

type Result = { label: string; status: number; body: string } | null;

/**
 * Diagnostic panel: calls the Spring API through the BFF proxy (`/api/proxy/*`). The browser never sees
 * an access token; it only holds the HttpOnly session cookie and echoes a CSRF token for unsafe methods.
 */
export function ApiProbe() {
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);

  async function call(label: string, path: string) {
    setBusy(true);
    try {
      const response = await fetch(`/api/proxy/${path}`, {
        credentials: "same-origin",
        cache: "no-store",
      });
      setResult({ label, status: response.status, body: await response.text() });
    } catch (error) {
      setResult({ label, status: 0, body: String(error) });
    } finally {
      setBusy(false);
    }
  }

  const button =
    "rounded border px-3 py-1.5 text-sm hover:bg-black/5 disabled:opacity-50 dark:hover:bg-white/10";
  return (
    <section aria-label="API probe" className="space-y-3">
      <div className="flex gap-2">
        <button className={button} disabled={busy} onClick={() => call("whoami", "system/whoami")}>
          Call whoami via BFF
        </button>
        <button
          className={button}
          disabled={busy}
          onClick={() => call("admin/ping", "system/admin/ping")}
        >
          Call admin endpoint via BFF
        </button>
      </div>
      {result && (
        <pre
          data-testid="api-result"
          data-status={result.status}
          className="overflow-x-auto rounded bg-black/5 p-3 text-xs dark:bg-white/10"
        >
          {`${result.label} -> HTTP ${result.status}\n${result.body}`}
        </pre>
      )}
    </section>
  );
}
