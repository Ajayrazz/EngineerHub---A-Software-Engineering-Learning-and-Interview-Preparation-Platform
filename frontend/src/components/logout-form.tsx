/** Logout is a POST with the session's CSRF token (never a GET link). Server component: no JS needed. */
export function LogoutForm({ csrfToken }: { csrfToken: string }) {
  return (
    <form method="post" action="/auth/logout">
      <input type="hidden" name="csrf" value={csrfToken} />
      <button
        type="submit"
        className="rounded border px-3 py-1.5 text-sm hover:bg-black/5 dark:hover:bg-white/10"
      >
        Log out
      </button>
    </form>
  );
}
