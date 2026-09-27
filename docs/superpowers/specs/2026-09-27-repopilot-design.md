# RepoPilot — Design Specification

Date: 2026-09-27
Status: Design approved in conversation; implementation plan pending user review of this written spec.

## 1. Product Goal

RepoPilot is a personal Android developer utility that combines a lightweight repository doctor with a Termux-powered execution engine. It is optimized for one user, low RAM usage, and Android/GitHub workflows.

The app must let the user:

- Add or open a GitHub repository.
- Clone/open the repository through Termux.
- Scan it for build, Git, Android/Gradle, workflow, configuration, and common repository problems.
- Explain each issue in simple language.
- Offer two paths for every actionable issue:
  - **Fix It** — prepare a fix inside RepoPilot, show a preview, and apply it only after approval.
  - **Copy AI Prompt** — generate a complete prompt that can be pasted into another AI model.
- Optionally use a configured cloud AI API to propose fixes for complex issues.
- Build/test fixes through Termux.
- Commit and push approved fixes to a safe `fix/...` branch on GitHub.
- Never edit, commit, push, force-push, delete, or change repository visibility without explicit user approval at the relevant stage.

## 2. Non-Goals for v1

- No multi-user accounts, billing, collaboration, team permissions, or cloud backend.
- No local LLM/model execution on the phone.
- No continuous/background repository scanning.
- No automatic push to `main`.
- No force push, repository deletion, visibility changes, or destructive Git history rewriting.
- No full IDE/editor replacement.
- No attempt to support every language/framework in v1.

## 3. Primary v1 Scope

RepoPilot v1 is strongest for Android projects and common repository infrastructure:

- Android Gradle projects using Kotlin or Java.
- Gradle/Groovy/Kotlin DSL build files.
- Git/GitHub repository state.
- GitHub Actions YAML relevant to Android builds.
- JSON/YAML/shell/configuration sanity checks where feasible.
- Common repository structure and secret-leak warnings.

The architecture must leave room for Node/Web and other ecosystems later without redesigning the app.

## 4. Architecture

### 4.1 Android App — Control Plane

The Android app owns:

- UI and navigation.
- Repository metadata/history.
- Issue presentation and severity.
- Rule-result parsing.
- AI provider configuration.
- Secret redaction before cloud AI requests.
- AI response validation.
- Fix diff preview.
- User approval gates.
- Operation history and rollback metadata.
- Termux command dispatch and result handling.

The app must not run Gradle or local AI itself.

### 4.2 Termux — Execution Plane

Termux owns heavy or shell-native work:

- `git clone`, `git pull`, `git status`, branch creation, commit, and push.
- Gradle wrapper execution.
- Android builds and tests.
- Repository-level shell inspection.
- Applying validated patches.
- Producing stdout/stderr and exit codes for RepoPilot.

RepoPilot communicates with Termux using Termux's supported RUN_COMMAND intent integration.

### 4.3 RepoPilot Bridge Script

A small script installed once inside Termux (for example `$PREFIX/bin/repopilot-bridge`) provides a stable interface between Android and Termux.

Supported bridge operations should include:

- `doctor`
- `repo-open`
- `repo-clone`
- `git-status`
- `git-pull`
- `scan`
- `build`
- `test`
- `diff`
- `apply-patch-check`
- `apply-patch`
- `rollback`
- `commit`
- `push`
- `environment-status`

The bridge must return machine-readable status in addition to human-readable logs. JSON-lines is preferred for structured events, with raw stdout/stderr preserved for logs.

For patches too large for a single Android intent payload, the bridge will support chunked staging inside Termux before validation/application.

## 5. Low-RAM Strategy

- No local AI model.
- No always-on service.
- No automatic background scans.
- Only one heavy Termux job at a time.
- Streaming/bounded log buffers rather than holding entire build logs indefinitely in memory.
- Persist older logs to storage; load on demand.
- Cancel and release command/result resources when an operation ends.
- Use native Android Views/XML + ViewBinding rather than a heavier always-recomposing UI stack.
- Use a small local database only for repository metadata, operations, issues, and history.
- Avoid embedded webviews for the main UI.

## 6. Technology Choices

### Android

- Kotlin.
- XML layouts + ViewBinding.
- Material Components with a custom dark developer-console theme.
- Single-activity navigation with fragments or equivalent lightweight screen controllers.
- Room/SQLite for structured local history and repository records.
- Android Keystore backed encryption for API keys and other secrets.
- Coroutines for non-blocking intent/result, parsing, database, and network work.
- OkHttp (or equivalent small HTTP client) for optional AI API calls.

### Termux

- Bash bridge script.
- Git and GitHub CLI (`gh`) when available.
- Project-provided `./gradlew`; do not require a globally installed Gradle for normal Android builds.
- Java/JDK compatibility checked before builds.

## 7. Visual Design

The uploaded Google Stitch design is the visual baseline.

Design language:

- Near-black/OLED-friendly background.
- Compact developer-console information density.
- Thin borders instead of shadows.
- Inter for normal UI text.
- JetBrains Mono or an available monospace fallback for code, branches, paths, and logs.
- Green for successful/approved states, blue for Git/navigation, purple for AI actions, amber for warnings/conflicts, red for failures/destructive actions.
- No gradients, cartoon styling, excessive animation, or large decorative cards.

Stitch already defines the main screens:

1. Repositories/Home
2. Repository Dashboard
3. Issues Scanner
4. Fix Preview
5. Build Result
6. Git Changes
7. AI Providers
8. Termux Setup

RepoPilot adds these missing screens/flows:

9. Live Operation / Terminal Output
10. AI Prompt Preview
11. File/Location Viewer
12. Fix History & Undo
13. Conflict/Recovery UI
14. General/Low-RAM Settings

## 8. Core User Flow

### 8.1 Open a Repository

1. User pastes a GitHub URL or chooses an existing RepoPilot workspace.
2. RepoPilot asks Termux bridge whether the repository already exists locally.
3. If not, RepoPilot presents the clone destination and user confirms.
4. Termux clones the repository.
5. RepoPilot records repo name, URL, local path, default/current branch, and last scan time.

### 8.2 Scan

1. User taps **Scan Repo**.
2. RepoPilot requests fast static checks first.
3. Optional deeper checks may run project tasks such as Gradle configuration/lint/build when the user chooses a deeper scan.
4. Results are normalized into Issue records:
   - severity
   - category
   - title
   - explanation
   - evidence/log excerpt
   - file/path/line when known
   - rule ID
   - auto-fix capability
   - AI-fix capability

### 8.3 Fix It

1. User taps **Fix It**.
2. Rule-based fix is preferred when deterministic and safe.
3. For complex issues, RepoPilot may offer **Use AI Fix** if an AI provider is configured.
4. A candidate patch is generated but not applied.
5. RepoPilot validates the patch.
6. Fix Preview shows all affected files and unified diff.
7. User taps **Approve Fix**.
8. RepoPilot creates or switches to a `fix/...` branch if needed.
9. Termux checks and applies the patch.
10. RepoPilot runs the relevant build/test checks.
11. If checks pass, user may commit.
12. Push remains a separate explicit approval/action.

### 8.4 Copy AI Prompt

For every issue, RepoPilot can generate a prompt containing:

- repository/project type
- issue title and explanation
- exact error/log excerpt
- relevant file paths and minimal relevant snippets
- current toolchain versions when known
- constraints (do not change unrelated files, preserve behavior, provide a unified diff, etc.)
- desired validation/build command

Before copy/share, RepoPilot shows the prompt and redacts likely secrets.

## 9. Repo Doctor Rules — v1

### 9.1 Environment and Termux

- Termux installed/reachable.
- RUN_COMMAND permission/integration available.
- Git installed.
- `gh` installed and authenticated when push is requested.
- Java available and compatible with the project's Android Gradle Plugin/Gradle requirements when determinable.
- `gradlew` present for Gradle projects.
- Wrapper executable bit / invocation problems.

### 9.2 Git State

- Uncommitted changes before automated fixing.
- Detached HEAD.
- Merge/rebase/cherry-pick in progress.
- Unresolved conflicts.
- Missing/invalid remote.
- Current branch and upstream mismatch.
- Diverged branch warning.
- Accidental work on `main` before a fix operation.

### 9.3 Android/Gradle

- Missing or broken Gradle wrapper files.
- Gradle configuration failures.
- Kotlin/Java compilation failures parsed from build output.
- Android resource merge/link failures.
- Manifest merge failures.
- Duplicate resources/classes where build output identifies them.
- Dependency resolution failures.
- Common AGP/Gradle/JDK compatibility mismatches when a reliable rule exists.
- Missing Android namespace/package configuration when the toolchain reports it.
- Signing configuration problems for release builds, without exposing secrets.
- Lint failures/warnings when lint is explicitly run.

### 9.4 GitHub Actions

- YAML parse/syntax problems when detectable.
- Missing checkout/setup-java/Gradle preparation patterns for Android workflows.
- Java version mismatches with the project where determinable.
- Missing executable permission handling for `gradlew` when relevant.
- Workflow build command pointing to a missing task/script.

Heuristic checks must be labeled as warnings and never presented as guaranteed errors.

### 9.5 Repository Hygiene/Security Warnings

- Tracked `.env` or obviously sensitive local config files.
- Common API-key/token patterns in tracked text files, using conservative heuristics.
- Generated build artifacts accidentally tracked.
- Missing `.gitignore` coverage for common Android build outputs.

RepoPilot must never display full detected secrets in UI/logs; values are masked.

## 10. AI Provider System

Supported provider adapters in v1:

- Gemini
- Groq
- OpenRouter
- Custom OpenAI-compatible endpoint

Requirements:

- AI is optional; RepoPilot remains useful without any API key.
- API keys are encrypted at rest using Android Keystore backed encryption.
- Keys are never written to Termux scripts, repository files, Git commits, logs, or prompts.
- Requests send only the minimum context needed.
- Secret-redaction runs before network transmission.
- User can preview what will be sent for sensitive/large contexts.
- Provider errors, quotas, auth failures, and timeouts are handled without losing local state.

AI output must be treated as untrusted input. Preferred response format is a unified diff plus a short rationale. RepoPilot validates path scope and patch structure before offering approval.

## 11. Patch Safety

Before a proposed fix can be applied:

- Reject paths outside the selected repository.
- Reject `.git/` modifications.
- Reject absolute paths and traversal (`../`).
- Reject binary patch operations in v1.
- Show all files that will change.
- Run `git apply --check` (or equivalent safe dry-run) before apply.
- Create rollback metadata (Git stash/snapshot or recorded pre-fix state as appropriate).
- Never discard existing uncommitted work automatically.
- If the repository is dirty, require the user to choose how to proceed before a fix operation.

## 12. Git Safety Model

Default workflow:

1. Start from the user's current clean branch or updated base.
2. Create `fix/<short-issue-name>-<timestamp-or-id>`.
3. Apply approved changes.
4. Build/test.
5. Show diff/status.
6. User approves **Commit**.
7. User separately approves **Push to GitHub**.

Prohibited automatic operations:

- force push
- hard reset that discards user work
- branch deletion
- repository deletion
- changing repository visibility
- pushing directly to `main` by default

## 13. Error Handling and Recovery

### Termux unavailable

Show the exact missing prerequisite and a guided setup action. Do not silently fall back to running heavy build logic inside the Android app.

### Command failure

Preserve exit code, stdout, stderr, command category, start/end time, and enough context to retry. Offer **Analyze Error**, **Copy AI Prompt**, and **Open Logs** where relevant.

### Build failure after fix

Do not commit/push automatically. Show the new issue(s), offer rollback, and allow another fix attempt.

### Git conflict

Stop. Show conflicted paths and current operation state. Do not auto-resolve unless the user chooses a specific proposed resolution after preview.

### AI failure

Keep the original issue and local state. Show provider error without exposing the API key. Allow retry, provider switch, or Copy AI Prompt.

### App interruption/restart

Persist operation metadata so RepoPilot can report whether a Termux command completed, is unknown, or needs a status refresh. Never assume an interrupted write succeeded.

## 14. Data Model

Core local entities:

- `Repository`
- `ScanRun`
- `Issue`
- `Operation`
- `FixProposal`
- `BuildResult`
- `GitAction`
- `AiProviderConfig` (metadata only; secret stored separately)

Large raw logs are stored as files with references from `Operation`, not as giant database fields.

## 15. Screen Behavior

### Home / Repositories

- Add GitHub Repo
- Existing repo list
- Current branch
- Last scan
- Build status
- Issue counts

### Repository Dashboard

- Scan Repo
- Build APK
- Pull Latest
- Git Status
- Issues
- AI Fix entry
- Terminal Logs

### Issues

Each item includes severity, category, filename/location, explanation, evidence, and actions:

- Fix It
- Copy AI Prompt
- View File
- Ignore

### Fix Preview

- Rationale
- Affected files
- Unified diff
- Safety warnings
- Approve Fix / Cancel

### Live Operation

- Operation name
- Current step
- Bounded live logs
- Elapsed time
- Safe Stop when supported

### Build Result

- Passed/failed
- Duration
- APK output path when produced
- Relevant errors
- Analyze Error
- Copy AI Prompt
- Try AI Fix

### Git Changes

- Changed files
- Diff summary
- Commit message preview/edit
- Branch name
- Commit action
- Separate Push action

### AI Prompt Preview

- Prompt text
- Included files/logs
- Redaction indicator
- Copy prompt

### Termux Setup

Status for:

- Termux
- bridge script
- Git
- GitHub CLI
- Java
- repository build prerequisites
- GitHub authentication

## 16. Testing Strategy

### Unit Tests

- issue normalization/parsing
- build error parsers
- secret redaction
- AI response/diff validation
- safe path validation
- prompt generation
- branch-name generation
- operation state transitions

### Integration Tests

- fake Termux result receiver and command dispatch
- bridge JSON-lines parsing
- dirty repo safety flow
- patch dry-run/apply/rollback using fixture repositories
- AI adapter mocked responses
- database recovery after interrupted operations

### Termux Bridge Tests

Shell tests against temporary Git repositories for:

- clone/open/status
- dirty worktree detection
- fix branch creation
- patch validation/application
- rollback
- build command exit-code capture
- commit/push guard behavior

### UI Tests

Critical approval gates:

- fix cannot apply before approval
- commit cannot occur before user action
- push cannot occur before separate user action
- destructive operations are unavailable
- secrets are masked in issue/prompt/log views

### CI

GitHub Actions should run Android unit tests, lint, debug build, and bridge shell tests where practical, then upload the debug APK artifact.

## 17. Security and Privacy

- Personal-use app; no account system or telemetry by default.
- No analytics SDK in v1.
- No repository content uploaded anywhere unless the user invokes an AI feature that needs it.
- AI requests are minimal and redacted.
- API secrets encrypted locally and never committed.
- Command templates use fixed operations and strict argument escaping; avoid arbitrary shell interpolation from untrusted repository text.
- Logs mask known secrets and provider keys.
- External links and repository URLs are validated before use.

## 18. Success Criteria for v1

RepoPilot v1 is successful when the user can:

1. Connect RepoPilot to Termux.
2. Add an Android GitHub repository.
3. Clone/open it and run a scan.
4. See useful detected issues with evidence.
5. Generate a one-click AI prompt for an issue.
6. Configure an optional AI provider and receive a proposed fix.
7. Preview and approve a deterministic or AI-generated patch.
8. Apply the fix in a safe `fix/...` branch through Termux.
9. Build/test the project.
10. Commit and push only after explicit user actions.
11. Recover/rollback when a fix or build fails.
12. Do all of the above without running a local AI model or keeping a heavy background process alive.

## 19. Deferred Enhancements

- Node/Web/Python-specific doctor plugins.
- PR creation after push.
- GitHub Actions remote run inspection and log analysis.
- Multiple fix proposals ranked by confidence.
- Plugin architecture for custom repo doctors.
- Optional scheduled checks (still opt-in and battery-aware).
- Desktop/tablet split-pane enhancements beyond the initial responsive layout.
