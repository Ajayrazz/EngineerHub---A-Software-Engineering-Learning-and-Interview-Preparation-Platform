# Contributing

## Workflow
- Trunk-based. The solo owner + Claude commit directly to `main` in small logical Conventional Commits and push after every verified change set; use short-lived branches (`feat/<module>-<topic>`, `fix/...`, `docs/...`) + PRs when collaborators join or for risky changes.
- Conventional Commits (`feat(problem): ...`). PRs require green CI and one review (two for `identity`, `payment`, `judge`, `infra`).
- Every PR: tests, updated docs (`docs/19-PROJECT-PROGRESS.md`, API/DB docs if changed), CHANGELOG entry.

## Standards
- Follow [docs/09](docs/09-SECURITY-THREAT-MODEL.md) §8 secure-coding checklist on every PR.
- Respect module boundaries (doc 03/05): no cross-module repository access.
- DB changes only via Flyway, backward compatible (expand/contract).
- No new library/framework without an ADR entry (doc 17).
- Never commit secrets; `gitleaks` runs in pre-commit and CI.
- Do not document unimplemented features as done.

## Definition of done
Acceptance criteria met · unit/integration/authz tests pass · security checklist ticked · docs updated · observability added (logs/metrics) · no known critical issues.
