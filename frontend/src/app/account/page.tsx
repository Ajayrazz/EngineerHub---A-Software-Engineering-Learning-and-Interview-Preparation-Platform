import { redirect } from "next/navigation";
import { ApiProbe } from "@/components/api-probe";
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
      <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-4 p-8">
        <h1 className="text-2xl font-semibold">Account</h1>
        <p role="alert" data-testid="idp-unavailable">
          Sign-in services are temporarily unavailable. Your session is safe — please retry in a
          moment.
        </p>
      </main>
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
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 p-8">
      <h1 className="text-2xl font-semibold">Account</h1>
      <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
        <dt className="opacity-60">Username</dt>
        <dd data-testid="account-username">{fresh.session.username}</dd>
        <dt className="opacity-60">Name</dt>
        <dd>{fresh.session.name}</dd>
        <dt className="opacity-60">User id</dt>
        <dd data-testid="user-id">{me?.id ?? "unavailable"}</dd>
        <dt className="opacity-60">API subject</dt>
        <dd data-testid="api-subject">{whoami?.subject ?? "unavailable"}</dd>
        <dt className="opacity-60">Staff roles</dt>
        <dd data-testid="api-roles">
          {whoami ? (whoami.roles?.length ? whoami.roles.join(", ") : "none") : "unavailable"}
        </dd>
      </dl>
      <ApiProbe />
      <div>
        <LogoutForm csrfToken={fresh.session.csrfToken} />
      </div>
    </main>
  );
}
