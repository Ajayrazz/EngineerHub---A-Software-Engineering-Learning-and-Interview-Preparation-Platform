/** What the BFF keeps server-side for a logged-in browser. Never serialised to the client. */
export type Session = {
  /** Keycloak subject (opaque user id). */
  sub: string;
  username: string;
  name: string;
  /** Per-session CSRF token; the UI reads it from /api/session and echoes it in `X-CSRF-Token`. */
  csrfToken: string;
  accessToken: string;
  /** Epoch seconds. */
  accessExpiresAt: number;
  refreshToken: string;
  idToken: string;
  /** Epoch seconds: session creation (absolute lifetime is measured from here). */
  createdAt: number;
  /** Epoch seconds of the last successful token refresh (0 if never). */
  refreshedAt: number;
};

/** State of one in-flight login (authorization code + PKCE). Single use. */
export type LoginTransaction = {
  codeVerifier: string;
  nonce: string;
  returnTo: string;
  createdAt: number;
};
