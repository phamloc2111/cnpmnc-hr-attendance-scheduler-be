# Development workflow

Use a separate feature branch for each feature:

1. Start from the latest `dev` and create `feat/<short-kebab-case-name>`, for example `feat/mobile-notification`. Never create or reuse a branch named only `feat`.
2. Commit with a descriptive prefix and push that feature branch.
3. When the feature is complete, open a **GitHub Pull Request from `feat/<feature-name>` to `dev`**. Review the complete diff, run the repository checks, inspect configured CI/status checks, address review comments, and resolve every merge conflict. Merge through the PR only after the required checks and review requirements are satisfied.
4. Verify the merged feature on `dev`. Make fixes on a feature branch and submit them through another PR to `dev`.
5. Once `dev` is verified, open a **GitHub Pull Request from `dev` to `main`**. Check the complete diff, current mergeability, conflicts, review comments, and all configured checks again. Merge through the PR only after validation is complete.
6. Synchronize local branches from the remote after the PR is merged. Create the next feature branch from the updated `dev`.

**Never merge locally into `dev` or `main` and push those branches directly. Both promotion steps require a GitHub Pull Request, even when local tests already pass.** Pending, failed, or stale required checks do not count as passing. Validate the latest PR commit after any changes.

If CI is not configured, run the relevant local checks and record the results in the PR; do not claim that GitHub checks ran. If PR creation, inspection, or merging is unavailable, report that limitation and keep the work on its feature branch; never fall back to a direct push to `dev` or `main`.

Transition: existing legacy `feat` PRs may be merged once into `dev`. Leave deletion of those branches to the user. Git cannot create `feat/<name>` while a branch named `feat` still exists in the same local or remote namespace; remove the merged legacy reference before creating the new feature branches.

Do not force-push shared branches. Keep `hotfix` and `release` separate from this normal feature flow.

Example commit messages:

- `[UPDATE] Add employee account management`
- `[UPDATE] Update mobile login UI`
- `[FIX] Hide scheduling fields for management roles`
- `[TEST] Cover account permissions and session revocation`
- `[DOCS] Document account setup and API contracts`
- `[MERGE] Merge completed feature into dev`
- `[MERGE] Promote tested dev to main`

Never commit real `.env` files, credentials, access tokens, signing keys, or build outputs. Keep fake test credentials clearly confined to tests and examples.

## Backend checks

Use Java 21 or a compatible JDK and a running Docker engine.

```powershell
.\mvnw.cmd test
```

SS-15 integration tests use an isolated Testcontainers PostgreSQL instance. Never run tests against the real NeonDB connection. Flyway applies new migrations when the application starts; do not edit an already deployed migration.

See [AUTH.md](AUTH.md) for authentication and [ACCOUNTS.md](ACCOUNTS.md) for account management. The module conventions are packaged in `.codex/skills/hr-attendance-backend.skill`.
