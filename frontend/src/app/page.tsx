import Link from "next/link";
import { redirect } from "next/navigation";
import { staffInfo } from "@/lib/admin/staff";
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
import { HeroTerminal } from "@/components/hero-terminal";
import { Spotlight, Typewriter } from "@/components/hero-fx";
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
  if (auth && !error && (await staffInfo())) redirect("/admin"); // staff work in the console, not on the landing page

  return (
    <>
      <SiteNav username={auth?.session.username} />
      <main className="flex flex-1 flex-col">
        <Spotlight className="hero-glow relative overflow-hidden border-b">
          <div className="stars" aria-hidden />
          <div className="beams" aria-hidden />
          <div className="spot" aria-hidden />
          <div className="relative mx-auto grid w-full max-w-7xl items-center gap-12 px-6 py-24 lg:grid-cols-[1.1fr_1fr]">
            <div className="flex flex-col items-center gap-6 text-center lg:items-start lg:text-left">
              <span className="chip">Learn · Practice · Get hired</span>
              <h1 className="text-5xl font-bold tracking-tight sm:text-6xl">
                Crack interviews at top tech companies, master{" "}
                <Typewriter
                  words={["arrays.", "graphs.", "system design.", "dynamic programming."]}
                />
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
            <HeroTerminal />
          </div>
        </Spotlight>
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
