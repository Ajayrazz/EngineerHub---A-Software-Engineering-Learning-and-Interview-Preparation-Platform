/**
 * The BFF forwards `/api/proxy/<path>` to `<API_BASE_URL>/api/v1/<path>`. The caller controls `<path>`,
 * so it must be impossible to escape that prefix (path traversal, encoded dots, extra schemes/hosts)
 * or to reach non-API endpoints such as /actuator.
 */
const SEGMENT = /^[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$/;

/** Returns the safe forwarded path ("a/b/c") or null if any segment is not acceptable. */
export function toApiPath(segments: string[] | undefined): string | null {
  if (!segments || segments.length === 0 || segments.length > 12) return null;
  for (const raw of segments) {
    let seg: string;
    try {
      seg = decodeURIComponent(raw);
    } catch {
      return null;
    }
    // reject anything that changed on decoding (double-encoding tricks) and dot segments
    if (seg !== raw || seg === "." || seg === ".." || !SEGMENT.test(seg)) return null;
  }
  return segments.join("/");
}
