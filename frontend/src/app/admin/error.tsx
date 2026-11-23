"use client";

export default function AdminError({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-4 p-8">
      <h1 className="text-2xl font-semibold">Admin</h1>
      <p role="alert" data-testid="admin-error">
        This page could not be loaded. Please retry in a moment.
      </p>
      <div>
        <button
          onClick={reset}
          className="rounded border px-3 py-1.5 text-sm hover:bg-black/5 dark:hover:bg-white/10"
        >
          Retry
        </button>
      </div>
    </main>
  );
}
