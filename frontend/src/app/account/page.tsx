import { redirect } from "next/navigation";
import { ApiProbe } from "@/components/api-probe";
import { PageHeader, ReaderShell } from "@/components/reader/ui";
import { LogoutForm } from "@/components/logout-form";
import { callApi } from "@/lib/auth/api";
import { currentSession, withFreshToken } from "@/lib/auth/current";
import { RefreshUnavailableError } from "@/lib/auth/refresh";
import type { components } from "@/lib/api/schema";

type WhoAmI = components["schemas"]["WhoAmI"];
type Me = components["schemas"]["MeResponse"];

export default async function AccountPage() {
  const auth = await currentSession();
  if (!auth) redirect("/auth/login?returnTo=/account");
  let fresh;
  try {
    fresh = await withFreshToken(auth);
  } catch (error) {
    if (!(error instanceof RefreshUnavailableError)) throw error;
    // Identity provider outage: keep the session, do NOT bounce to login (that would loop and, once
    // Keycloak returns, is unnecessary). Ask the user to retry.
    return (
      <ReaderShell username={auth.session.username}>
        <PageHeader title="Account" lead="Your identity, preferences and sign-out." />
        <p role="alert" data-testid="idp-unavailable">
          Sign-in services are temporarily unavailable. Your session is safe — please retry in a
          moment.
        </p>
      </ReaderShell>
    );
  }
  if (!fresh) redirect("/auth/login?returnTo=/account");

  // Server-side call: the access token never leaves the server.
  let whoami: WhoAmI | null = null;
  let me: Me | null = null;
  try {
    const response = await callApi(fresh.session.accessToken, "system/whoami");
    if (response.ok) whoami = (await response.json()) as WhoAmI;
    const meResponse = await callApi(fresh.session.accessToken, "me");
    if (meResponse.ok) me = (await meResponse.json()) as Me;
  } catch {
    whoami = null;
  }

  return (
    <ReaderShell username={fresh.session.username}>
      <PageHeader title="Account" lead="Your identity, preferences and sign-out." />
      <dl className="glass grid grid-cols-[8rem_1fr] gap-y-3 p-6 text-sm">
        <dt className="text-muted">Username</dt>
        <dd data-testid="account-username">{fresh.session.username}</dd>
        <dt className="text-muted">Name</dt>
        <dd>{fresh.session.name}</dd>
        <dt className="text-muted">User id</dt>
        <dd data-testid="user-id">{me?.id ?? "unavailable"}</dd>
        <dt className="text-muted">API subject</dt>
        <dd data-testid="api-subject">{whoami?.subject ?? "unavailable"}</dd>
        <dt className="text-muted">Staff roles</dt>
        <dd data-testid="api-roles">
          {whoami ? (whoami.roles?.length ? whoami.roles.join(", ") : "none") : "unavailable"}
        </dd>
      </dl>
      <ApiProbe />
      <div>
        <LogoutForm csrfToken={fresh.session.csrfToken} />
      </div>
    </ReaderShell>
  );
}
