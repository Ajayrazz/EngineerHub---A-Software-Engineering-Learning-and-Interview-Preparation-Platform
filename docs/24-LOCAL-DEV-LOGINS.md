# 24 — Local development logins

**Fake accounts for the local Docker stack only.** They exist in the dev Keycloak realm (`infra/keycloak/dev/overlay.json`), are never part of a production realm, and must never be reused anywhere else. The repository is public, so nothing real may ever be written in this file.

## Web app — <http://localhost:3000> → **Log in**
Password for every user: `Dev-Only-Pass-1!`

| Username | Role | What you can see |
|---|---|---|
| `learner` | none (learner) | dashboard, courses, problems, billing |
| `admin` | ADMIN | admin console |
| `superadmin` | SUPER_ADMIN | everything, incl. Live readiness |
| `contentmanager` | CONTENT_MANAGER | course authoring |
| `problemsetter` | PROBLEM_SETTER | problem authoring |
| `moderator` | MODERATOR | moderation |
| `instructor` | INSTRUCTOR | scoped instructor access |

You can also **register** a new account on the sign-in screen. Mail the app sends (verification, reset) appears in Mailpit: <http://localhost:8025>.

## One-time code (staff accounts only)
Every account except `learner` is asked for a time-based one-time code after the password. The dev seed is `dev-only-totp-seed-01` (also in `frontend/e2e/totp.ts`). Add it by hand (as a time-based key) to an authenticator app, or let the browser tests do it.

## Keycloak admin console — <http://localhost:8081>
User `kcadmin`, password `dev-only-kc-admin-pw` (master realm). Separate from the app login; for managing users and realm settings.

## Other local services
Postgres `localhost:5440` (app user `engineerhub_app` / `dev-only-app-pw`), Redis `6380`, RabbitMQ UI <http://localhost:15672>, S3 `9000`. Values come from `.env` (created by `make dev-env` from `.env.example`).

## Loading sample content
`make seed-content` (courses and problems), `make clean-test-content` (removes browser-test leftovers).

See also [docs/18](18-ENVIRONMENT-SETUP.md) for the full setup.
