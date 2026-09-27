# RepoPilot Security Model

- API keys are encrypted with Android Keystore and are not stored in repositories, bridge scripts, prompts, or logs.
- Secret-like strings are redacted before issue persistence, copy-to-AI prompts, and cloud AI requests.
- AI output is treated as untrusted. Only a validated unified diff can become a fix proposal.
- Patch paths cannot be absolute, contain `..`, target `.git`, or use binary patch bodies.
- File mutation requires the user to tap **Approve Fix**.
- RepoPilot creates/uses `fix/...` branches for fixes.
- **Commit** and **Push** are separate explicit UI actions.
- The bridge refuses push from `main` / `master`.
- No force push, repository deletion, branch deletion, visibility change, `git reset --hard`, or automatic conflict resolution is implemented.
- Existing dirty worktrees are never auto-discarded.
- Logs shown in the UI are bounded and redacted.
