# Development workflow

Use the existing shared branches in this order:

1. Start from the latest `dev`, bring `feat` up to date, and implement the feature on `feat`.
2. Commit with a descriptive prefix and push `feat`.
3. When the feature is complete, merge `feat` into `dev` using a merge commit and push `dev`.
4. Run the repository checks on `dev`. Fix failures on `feat` and merge the fix into `dev`.
5. Once checks pass and the feature is verified on `dev`, merge `dev` into `main` using a merge commit and push `main`.
6. Keep local work on `dev` between features. Synchronize `feat` from `dev` before starting the next feature.

Do not push feature work directly to `main`. Do not force-push shared branches. Keep `hotfix` and `release` separate from this normal feature flow.

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
