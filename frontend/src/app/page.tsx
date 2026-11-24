import Link from "next/link";
import {
  Community,
  Faq,
  Features,
  FinalCta,
  HowItWorks,
  Patterns,
  Practice,
  Tracks,
} from "@/components/landing";
import { SiteFooter } from "@/components/site-footer";
import { SiteNav } from "@/components/site-nav";
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
    <>
      <SiteNav username={auth?.session.username} />
      <main className="flex flex-1 flex-col">
        <section className="hero-glow relative overflow-hidden border-b">
          <div className="orb -left-20 top-10 h-72 w-72 bg-accent" />
          <div className="orb right-0 top-40 h-72 w-72 bg-accent-2 [animation-delay:-5s]" />
          <div className="grid-floor" aria-hidden />
          <div className="relative mx-auto grid w-full max-w-7xl items-center gap-10 px-6 py-24 lg:grid-cols-[1.2fr_1fr]">
            <div className="flex flex-col items-center gap-6 text-center lg:items-start lg:text-left">
              <span className="chip">Learn · Practice · Get hired</span>
              <h1 className="text-5xl font-bold tracking-tight sm:text-6xl">
                Crack interviews at top tech companies,
                <br />
                <span className="text-gradient">one pattern at a time.</span>
              </h1>
              <p className="max-w-xl text-lg text-muted">
                Software engineering learning and interview preparation platform.
              </p>
              {error && (
                <p
                  role="alert"
                  data-testid="auth-error"
                  className="rounded-lg border border-danger/50 bg-danger/10 p-3 text-sm text-danger"
                >
                  {error}
                </p>
              )}
              <div className="flex flex-wrap items-center justify-center gap-3">
                <Link href="/courses" className="btn-primary" data-testid="browse-courses">
                  Browse courses
                </Link>
                <Link href="/problems" className="btn-ghost" data-testid="browse-problems">
                  Problems
                </Link>
                <Link href="/dashboard" className="btn-ghost" data-testid="dashboard-link">
                  Dashboard
                </Link>
              </div>
              {auth ? (
                <section
                  className="flex flex-wrap items-center justify-center gap-4"
                  aria-label="Signed in"
                >
                  <p data-testid="signed-in" className="text-sm text-muted">
                    Signed in as{" "}
                    <strong className="text-foreground" data-testid="username">
                      {auth.session.username}
                    </strong>
                  </p>
                  <Link href="/account" className="text-sm underline">
                    Account
                  </Link>
                  <LogoutForm csrfToken={auth.session.csrfToken} />
                </section>
              ) : (
                <section className="flex items-center gap-3" aria-label="Signed out">
                  <p data-testid="signed-out" className="text-sm text-muted">
                    You are not signed in.
                  </p>
                </section>
              )}
            </div>
            <div className="scene hidden h-[440px] items-center justify-center lg:flex" aria-hidden>
              <div
                className="float relative flex h-full w-full items-center justify-center"
                style={{ ["--rx" as string]: "8deg", ["--ry" as string]: "-14deg" }}
              >
                <div className="cube">
                  <span>{"{ }"}</span>
                  <span>O(n)</span>
                  <span>{"</>"}</span>
                  <span>BFS</span>
                  <span>DP</span>
                  <span>[ ]</span>
                </div>
                <pre className="glass absolute -left-2 top-4 w-56 -rotate-6 p-4 text-xs text-accent-2 [transform:translateZ(60px)_rotate(-6deg)]">
                  {"for (i of nums)\n  seen.add(i)\nreturn best"}
                </pre>
                <div className="glass absolute -right-2 bottom-6 w-48 rotate-3 p-4 text-xs [transform:translateZ(90px)_rotate(4deg)]">
                  <div className="mb-2 text-muted">Lesson progress</div>
                  <div className="h-2 rounded-full bg-line">
                    <div className="h-2 w-2/3 rounded-full bg-gradient-to-r from-accent to-accent-3" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
        <Tracks />
        <Patterns />
        <Features />
        <HowItWorks />
        <Practice />
        <Community />
        <Faq />
        <FinalCta signedIn={Boolean(auth)} />
      </main>
      <SiteFooter />
    </>
  );
}
