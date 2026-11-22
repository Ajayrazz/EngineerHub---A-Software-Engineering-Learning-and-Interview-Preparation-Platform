import "server-only";
import * as client from "openid-client";
import { getEnv } from "../env";
import { RefreshRejectedError, type TokenSet } from "./refresh";

/**
 * OpenID Connect client (Authorization Code + PKCE S256, confidential client) built on the certified
 * `openid-client` library: ID-token signature/issuer/audience/nonce validation and PKCE/state checks are
 * done by the library, not hand-rolled.
 */
const CONFIG_TTL_MS = 10 * 60 * 1000;
let cached: { config: client.Configuration; at: number } | undefined;

export async function getOidcConfig(): Promise<client.Configuration> {
  if (cached && Date.now() - cached.at < CONFIG_TTL_MS) return cached.config;
  const env = getEnv();
  const issuer = new URL(env.KEYCLOAK_ISSUER_URI);
  const insecureAllowed = issuer.protocol === "http:"; // getEnv() already restricted this to localhost
  const config = await client.discovery(
    issuer,
    env.KEYCLOAK_BFF_CLIENT_ID,
    env.KEYCLOAK_BFF_CLIENT_SECRET,
    undefined,
    insecureAllowed ? { execute: [client.allowInsecureRequests] } : undefined,
  );
  cached = { config, at: Date.now() };
  return config;
}

export const callbackUrl = () => new URL("/auth/callback", getEnv().APP_URL);

export async function startAuthorization(returnTo: string) {
  const config = await getOidcConfig();
  const codeVerifier = client.randomPKCECodeVerifier();
  const state = client.randomState();
  const nonce = client.randomNonce();
  const url = client.buildAuthorizationUrl(config, {
    redirect_uri: callbackUrl().href,
    scope: "openid profile email",
    code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
    code_challenge_method: "S256",
    state,
    nonce,
  });
  return {
    url,
    state,
    transaction: { codeVerifier, nonce, returnTo, createdAt: Math.floor(Date.now() / 1000) },
  };
}

/** Completes the code exchange. `search` is the raw query string of the callback request. */
export async function completeAuthorization(
  search: string,
  expected: { state: string; nonce: string; codeVerifier: string },
) {
  const config = await getOidcConfig();
  // Built from the configured APP_URL, never from the Host header (no host-header injection).
  const currentUrl = new URL(callbackUrl().pathname + search, getEnv().APP_URL);
  const tokens = await client.authorizationCodeGrant(config, currentUrl, {
    pkceCodeVerifier: expected.codeVerifier,
    expectedState: expected.state,
    expectedNonce: expected.nonce,
    idTokenExpected: true,
  });
  return { tokens, claims: tokens.claims() };
}

/** OAuth error codes meaning "this refresh token will never work again". */
const PERMANENT_REFRESH_ERRORS = new Set([
  "invalid_grant",
  "invalid_client",
  "unauthorized_client",
]);

export async function refreshTokens(refreshToken: string): Promise<TokenSet> {
  let tokens;
  try {
    tokens = await client.refreshTokenGrant(await getOidcConfig(), refreshToken);
  } catch (error) {
    // Only a definitive refusal by the IdP ends the session. Network errors, timeouts and 5xx are
    // transient and must NOT log users out during an identity-provider outage.
    if (error instanceof client.ResponseBodyError && PERMANENT_REFRESH_ERRORS.has(error.error)) {
      throw new RefreshRejectedError(error.error);
    }
    throw error;
  }
  return {
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    id_token: tokens.id_token,
    expires_in: tokens.expires_in,
  };
}

export async function endSessionUrl(idToken: string): Promise<URL> {
  return client.buildEndSessionUrl(await getOidcConfig(), {
    id_token_hint: idToken,
    post_logout_redirect_uri: new URL("/", getEnv().APP_URL).href,
  });
}
