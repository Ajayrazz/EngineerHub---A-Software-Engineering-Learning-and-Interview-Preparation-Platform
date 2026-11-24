import Link from "next/link";
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

  const features = [
    {
      t: "Structured courses",
      d: "Chapters, lessons, quizzes and step-by-step visualizations, tracked as you go.",
    },
    {
      t: "Practice problems",
      d: "A curated problem bank with hints, difficulty, tags and your own progress.",
    },
    {
      t: "Interview preparation",
      d: "Patterns and strategy lessons written for real interview loops.",
    },
    { t: "Know where you stand", d: "A dashboard with streaks, completion and what to do next." },
  ];
  return (
    <>
      <SiteNav username={auth?.session.username} />
      <main className="flex flex-1 flex-col">
        <section className="hero-glow border-b">
          <div className="mx-auto flex w-full max-w-4xl flex-col items-center gap-6 px-6 py-24 text-center">
            <span className="chip">Learn · Practice · Get hired</span>
            <h1 className="text-5xl font-bold tracking-tight sm:text-6xl">
              Master software engineering,
              <br />
              <span className="text-accent">one lesson at a time.</span>
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
        </section>
        <section className="mx-auto grid w-full max-w-6xl gap-4 px-6 py-16 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <div key={f.t} className="card card-hover p-5">
              <h2 className="font-semibold">{f.t}</h2>
              <p className="mt-2 text-sm text-muted">{f.d}</p>
            </div>
          ))}
        </section>
      </main>
    </>
  );
}
