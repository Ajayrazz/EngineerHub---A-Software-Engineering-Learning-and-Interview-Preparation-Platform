import "server-only";
import { getEnv } from "../env";

type CookieSpec = {
  name: string;
  options: {
    httpOnly: true;
    secure: boolean;
    sameSite: "lax";
    path: "/";
    maxAge: number;
  };
};

/**
 * Cookie names/attributes. Over HTTPS the `__Host-` prefix makes browsers enforce Secure + Path=/ and
 * forbid a Domain attribute (so sibling subdomains cannot overwrite them). Plain-HTTP localhost cannot
 * use the prefix (it requires Secure), so development uses un-prefixed names.
 *
 * SameSite=Lax (not Strict): the cookie must accompany the top-level redirect back from Keycloak.
 */
export function sessionCookie(): CookieSpec {
  const env = getEnv();
  return {
    name: env.secureCookies ? "__Host-eh_session" : "eh_session",
    options: {
      httpOnly: true,
      secure: env.secureCookies,
      sameSite: "lax",
      path: "/",
      maxAge: env.SESSION_ABSOLUTE_TTL_SECONDS,
    },
  };
}

/** Binds a login attempt (its `state`) to the browser that started it: defeats login CSRF. */
export function loginCookie(): CookieSpec {
  const env = getEnv();
  return {
    name: env.secureCookies ? "__Host-eh_login" : "eh_login",
    options: { httpOnly: true, secure: env.secureCookies, sameSite: "lax", path: "/", maxAge: 300 },
  };
}
