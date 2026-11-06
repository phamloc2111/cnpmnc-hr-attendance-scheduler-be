# Repository instructions

Read CONTRIBUTING.md before starting implementation or Git operations.

- Create each new feature branch from the latest dev as feat/<short-kebab-case-name> (for example feat/mobile-notification). Never create or reuse a branch named only feat.
- Create a GitHub Pull Request for feat/<feature-name> -> dev. Existing legacy feat PRs may be merged once to finish the transition; leave branch deletion to the user.
- Create a separate GitHub Pull Request for dev -> main after dev verification.
- Before merging either PR, inspect its complete diff, resolve conflicts and review issues, run relevant tests, and confirm the current required checks pass.
- Never substitute a local merge followed by a direct push to dev or main.
- If PR tools/access are unavailable, report the limitation; do not bypass the PR workflow.
- Use commit prefixes such as [UPDATE], [FIX], [TEST], and [DOCS].
