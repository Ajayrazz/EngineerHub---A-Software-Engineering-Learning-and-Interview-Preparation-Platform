"use client";

import { useState } from "react";

/**
 * Downloads a document of the user's own (receipt, tax invoice or credit note) from an API path built by the caller. The file is fetched (so a refusal shows
 * a message instead of saving an error page) and handed to the browser as a download.
 */
export function ReceiptLink({ apiPath, label }: { apiPath: string; label: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function download() {
    setBusy(true);
    setError(null);
    try {
      const r = await fetch(`/api/proxy/${apiPath}`, {
        credentials: "same-origin",
        cache: "no-store",
      });
      if (!r.ok) {
        const problem = (await r.json().catch(() => ({}))) as { detail?: string };
        setError(problem.detail ?? "The document could not be downloaded.");
        return;
      }
      const name =
        /filename="?([^";]+)"?/.exec(r.headers.get("content-disposition") ?? "")?.[1] ??
        "document.pdf";
      const url = URL.createObjectURL(await r.blob());
      const a = document.createElement("a");
      a.href = url;
      a.download = name;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("Network error. Please retry.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="flex flex-col">
      <button
        type="button"
        className="underline disabled:opacity-60"
        disabled={busy}
        onClick={() => void download()}
        data-testid="receipt-download"
      >
        {label}
      </button>
      {error && (
        <span role="alert" className="text-xs" data-testid="receipt-error">
          {error}
        </span>
      )}
    </span>
  );
}
