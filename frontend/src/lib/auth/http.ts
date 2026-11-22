import "server-only";
import { NextResponse } from "next/server";

const NO_STORE = { "Cache-Control": "no-store" } as const;

/** RFC 9457-style error body, same shape as the Spring API's problems. */
export function problem(
  status: number,
  code: string,
  title: string,
  detail: string,
  extraHeaders: HeadersInit = {},
) {
  return NextResponse.json(
    {
      type: `urn:engineerhub:problem:${code.toLowerCase().replace(/_/g, "-")}`,
      title,
      status,
      detail,
      code,
    },
    {
      status,
      headers: { "Content-Type": "application/problem+json", ...NO_STORE, ...extraHeaders },
    },
  );
}

/** Structured server log line. Callers must never pass tokens, cookies or other secrets. */
export function logEvent(
  level: "info" | "warn" | "error",
  event: string,
  fields: Record<string, unknown> = {},
) {
  const line = JSON.stringify({ level, event, ...fields });
  if (level === "error") console.error(line);
  else console.log(line);
}

export { NO_STORE };
