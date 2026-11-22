import Link from "next/link";
import { LogoutForm } from "@/components/logout-form";
import { currentSession } from "@/lib/auth/current";

const ERRORS: Record<string, string> = {
  login_failed: "Sign-in failed. Please try again.",
  access_denied: "Sign-in was cancelled.",
};

export default async function Home(props: PageProps<"/">) {
  const params = await props.searchParams;
  const rawError = Array.isArray(params.auth_error) ? params.auth_error[0] : params.auth_error;
  const error = rawError ? ERRORS[rawError] : undefined; // fixed messages only; input is never reflected
  const auth = await currentSession();

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 p-8">
      <h1 className="text-3xl font-semibold">EngineerHub</h1>
      <p className="opacity-70">
        Software engineering learning and interview preparation platform.
      </p>
      {error && (
        <p
          role="alert"
          data-testid="auth-error"
          className="rounded border border-red-500/50 p-3 text-sm text-red-600 dark:text-red-400"
        >
          {error}
        </p>
      )}
      {auth ? (
        <section className="space-y-4" aria-label="Signed in">
          <p data-testid="signed-in">
            Signed in as <strong data-testid="username">{auth.session.username}</strong>
          </p>
          <div className="flex items-center gap-3">
            <Link href="/account" className="underline">
              Account
            </Link>
            <LogoutForm csrfToken={auth.session.csrfToken} />
          </div>
        </section>
      ) : (
        <section className="space-y-4" aria-label="Signed out">
          <p data-testid="signed-out">You are not signed in.</p>
          <a
            href="/auth/login"
            className="inline-block w-fit rounded bg-foreground px-4 py-2 text-background"
          >
            Log in
          </a>
        </section>
      )}
    </main>
  );
}
