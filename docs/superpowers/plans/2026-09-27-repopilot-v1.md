# RepoPilot v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a low-RAM personal Android app that diagnoses GitHub/Android repositories, generates safe fixes or AI-ready prompts, delegates heavy work to Termux, and only edits/commits/pushes after explicit user approval.

**Architecture:** A Kotlin/XML Android control plane communicates with a Bash `repopilot-bridge` in Termux via the supported RUN_COMMAND intent. Android owns UI, persistence, issue parsing, secret redaction, AI adapters, approvals, and recovery state; Termux owns Git, Gradle, patching, tests, builds, and shell-native repository inspection. Heavy operations are serialized and streamed into bounded log buffers.

**Tech Stack:** Kotlin, Android Views/XML + ViewBinding, Material Components, Navigation Component, Room, Kotlin coroutines/Flow, OkHttp, Android Keystore-backed encrypted preferences, Bash, Git, GitHub CLI, Gradle wrapper, JUnit, AndroidX Test/Espresso, shell tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-27-repopilot-design.md`

## Global Constraints

- Personal-use v1; no accounts, billing, collaboration backend, analytics SDK, or telemetry.
- No local LLM/model execution and no always-on/background repository scanner.
- Android app never runs Gradle itself; heavy work runs through Termux.
- Only one heavy Termux operation may run at a time.
- Every actionable issue exposes **Fix It** and **Copy AI Prompt** where applicable.
- Fixes are previewed as diffs and require explicit **Approve Fix** before file mutation.
- Default Git workflow creates `fix/...` branches; direct push to `main` is not the default.
- Commit and push are separate explicit user actions.
- Force-push, repository deletion, branch deletion, visibility changes, destructive history rewriting, and automatic hard reset are unavailable.
- Existing dirty worktrees are never discarded automatically.
- AI is optional and cloud-only; secrets must be redacted before any AI request.
- API keys must never be written to Termux scripts, repositories, Git history, logs, or prompts.
- Large logs are stored as files and loaded on demand; UI keeps only bounded live output.
- Main UI uses XML/ViewBinding and avoids WebView-heavy or animation-heavy architecture.
- v1 is strongest for Android/Gradle plus Git/GitHub Actions/repository hygiene; architecture must remain extensible.
- Package name: `com.repopilot.app`.
- Application name: `RepoPilot`.
- Minimum supported Android API: 26. Target/compile SDK: 36.
- JVM toolchain for the Android app: Java 17.

## Review Focus

- **Repository path tricks:** absolute paths, `../`, `.git/`, symlink escape, and binary patches must never reach apply; owning tests are in Task 8.
- **Dirty or interrupted Git state:** uncommitted work, detached HEAD, merge/rebase/cherry-pick state, and app restart must stop unsafe fix/commit flows; owning tests are in Tasks 6, 8, and 12.
- **Untrusted AI output:** malformed diffs, unexpected files, secret echoing, quota/auth/network failure must leave repository state unchanged; owning tests are in Task 11.
- **Termux missing/disconnected:** missing app/permission/bridge/dependencies and dropped result delivery must produce recoverable setup/error states, not silent fallback; owning tests are in Tasks 3 and 12.
- **Large/noisy build logs:** live output must remain bounded while full logs persist on disk and build errors remain extractable; owning tests are in Task 9.

---

## File Structure

```text
RepoPilot/
├── app/
│   ├── src/main/java/com/repopilot/app/
│   │   ├── App.kt
│   │   ├── MainActivity.kt
│   │   ├── core/
│   │   │   ├── model/
│   │   │   ├── util/
│   │   │   └── security/
│   │   ├── data/
│   │   │   ├── db/
│   │   │   ├── repository/
│   │   │   └── prefs/
│   │   ├── termux/
│   │   ├── doctor/
│   │   ├── fix/
│   │   ├── ai/
│   │   └── ui/
│   │       ├── home/
│   │       ├── repo/
│   │       ├── issues/
│   │       ├── fixpreview/
│   │       ├── operation/
│   │       ├── build/
│   │       ├── git/
│   │       ├── ai/
│   │       ├── setup/
│   │       ├── history/
│   │       └── settings/
│   ├── src/main/res/
│   │   ├── layout/
│   │   ├── navigation/
│   │   ├── values/
│   │   └── drawable/
│   ├── src/test/
│   └── src/androidTest/
├── termux-bridge/
│   ├── repopilot-bridge
│   └── tests/
├── .github/workflows/android.yml
└── docs/superpowers/
```

---

### Task 1: Android Foundation, Theme, Navigation, and CI Baseline

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/repopilot/app/App.kt`, `MainActivity.kt`
- Create: `app/src/main/res/values/{colors.xml,themes.xml,strings.xml}`, `navigation/nav_graph.xml`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `.github/workflows/android.yml`
- Test: `app/src/test/java/com/repopilot/app/SmokeTest.kt`

**Interfaces:**
- Produces: buildable `:app`, package `com.repopilot.app`, `MainActivity`, dark theme resources, Navigation host, and CI debug APK artifact.

- [ ] **Step 1: Write the failing smoke test** asserting the package-level app name constant is `RepoPilot` and the JVM test runtime starts.
- [ ] **Step 2: Run `./gradlew testDebugUnitTest`** and verify it fails because the project/app classes do not exist.
- [ ] **Step 3: Scaffold the Android project** with minSdk 26, compile/target 36, Java 17, Kotlin, ViewBinding, Material Components, Navigation, Room, coroutines, OkHttp, test dependencies, and no Compose.
- [ ] **Step 4: Implement the base near-black developer theme and empty Navigation host**; keep animations disabled/minimal by default.
- [ ] **Step 5: Add GitHub Actions** to run unit tests, lint, `assembleDebug`, bridge shell tests when present, and upload the debug APK artifact.
- [ ] **Step 6: Run `./gradlew testDebugUnitTest lintDebug assembleDebug`** and verify all commands pass and an APK exists under `app/build/outputs/apk/debug/`.
- [ ] **Step 7: Commit** with `chore: scaffold RepoPilot Android app`.

### Task 2: Local Data Model and Repository State Persistence

**Files:**
- Create: `app/src/main/java/com/repopilot/app/data/db/RepoPilotDatabase.kt`
- Create: `app/src/main/java/com/repopilot/app/data/db/entity/{RepositoryEntity,ScanRunEntity,IssueEntity,OperationEntity,FixProposalEntity,BuildResultEntity,GitActionEntity,AiProviderConfigEntity}.kt`
- Create: `app/src/main/java/com/repopilot/app/data/db/dao/*.kt`
- Create: `app/src/main/java/com/repopilot/app/data/repository/RepoPilotStore.kt`
- Test: `app/src/test/java/com/repopilot/app/data/RepoPilotStoreTest.kt`

**Interfaces:**
- Produces: `RepoPilotStore` methods to observe repositories/issues/operations and persist state transitions; large log bodies are represented by file-path references, not DB blobs.

- [ ] **Step 1: Write failing Room/store tests** for creating a repository, saving an issue, updating operation state, and restoring unfinished operations after restart.
- [ ] **Step 2: Run the targeted unit tests** and verify failure due to missing entities/store.
- [ ] **Step 3: Implement Room entities/DAOs/database and `RepoPilotStore`** with explicit enums for issue severity, operation type, and operation state.
- [ ] **Step 4: Add migration policy for v1** using schema export and destructive migration disabled.
- [ ] **Step 5: Run data-layer tests** and verify all persistence/state-transition assertions pass.
- [ ] **Step 6: Commit** with `feat: add RepoPilot local state database`.

### Task 3: Termux Command Contract, Dispatcher, and Setup Status

**Files:**
- Create: `app/src/main/java/com/repopilot/app/termux/{TermuxCommand.kt,TermuxDispatcher.kt,TermuxResult.kt,TermuxResultReceiver.kt,TermuxEnvironmentChecker.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/setup/{TermuxSetupFragment.kt,TermuxSetupViewModel.kt}`
- Create: `app/src/main/res/layout/fragment_termux_setup.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `nav_graph.xml`
- Test: `app/src/test/java/com/repopilot/app/termux/{TermuxDispatcherTest,TermuxEnvironmentCheckerTest}.kt`

**Interfaces:**
- Produces: `suspend fun TermuxDispatcher.execute(command: TermuxCommand): TermuxResult` and `Flow<OperationEvent>` for streamed events; fixed command names/argument arrays only, no arbitrary shell interpolation.
- Consumes: `RepoPilotStore` from Task 2.

- [ ] **Step 1: Write failing dispatcher tests** for exact command/argument mapping, one-heavy-job lock, timeout/cancel state, and missing Termux/RUN_COMMAND permission.
- [ ] **Step 2: Run targeted tests** and verify failure.
- [ ] **Step 3: Implement Termux RUN_COMMAND intent integration** with explicit package/action/extras, PendingIntent/result receiver handling, bounded event flow, and operation persistence.
- [ ] **Step 4: Implement setup checks** for Termux reachability, bridge path, Git, `gh`, Java, and GitHub auth by invoking bridge `environment-status` once Task 4 exists; until then expose bridge-missing state.
- [ ] **Step 5: Build the Termux Setup screen** with status rows and non-destructive setup guidance/actions.
- [ ] **Step 6: Run unit tests and `assembleDebug`**; verify no fallback executes Gradle inside Android.
- [ ] **Step 7: Commit** with `feat: add Termux command bridge client`.

### Task 4: Termux `repopilot-bridge` and Shell Safety Tests

**Files:**
- Create: `termux-bridge/repopilot-bridge`
- Create: `termux-bridge/tests/test_bridge.sh`
- Create: `termux-bridge/tests/fixtures/create_android_fixture.sh`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Produces bridge commands: `environment-status`, `repo-open`, `repo-clone`, `git-status`, `git-pull`, `scan`, `build`, `test`, `diff`, `apply-patch-check`, `apply-patch`, `rollback`, `commit`, `push`.
- Output contract: newline-delimited JSON events with `type`, `operation`, `message`, optional `path`, optional `progress`, and final `exitCode`; human stderr/stdout is preserved into log files.

- [ ] **Step 1: Write failing shell tests** for unknown command rejection, quoted paths with spaces, dirty repo detection, protected `main` push guard, patch-check failure, and command exit-code propagation.
- [ ] **Step 2: Run `bash termux-bridge/tests/test_bridge.sh`** and verify failure because bridge is absent.
- [ ] **Step 3: Implement the bridge** with `set -euo pipefail`, strict subcommand dispatch, `--` argument boundaries, repository-root checks, JSON event helpers, and no `eval`.
- [ ] **Step 4: Add environment checks** for Git, `gh`, Java, GitHub auth, Gradle wrapper presence/executable bit.
- [ ] **Step 5: Implement Git/build/patch operations** with explicit guards from the spec; `push` refuses `main`/`master` unless a future explicit override interface is added (not in v1 UI).
- [ ] **Step 6: Run bridge tests** and verify all pass in temporary repositories.
- [ ] **Step 7: Commit** with `feat: add safe Termux execution bridge`.

### Task 5: Repository Onboarding, Home, and Dashboard

**Files:**
- Create: `app/src/main/java/com/repopilot/app/ui/home/{HomeFragment.kt,HomeViewModel.kt,RepositoryAdapter.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/repo/{RepositoryDashboardFragment.kt,RepositoryDashboardViewModel.kt}`
- Create: `app/src/main/res/layout/{fragment_home.xml,item_repository.xml,fragment_repository_dashboard.xml}`
- Create: `app/src/main/java/com/repopilot/app/core/util/GitHubUrlParser.kt`
- Test: `app/src/test/java/com/repopilot/app/core/util/GitHubUrlParserTest.kt`
- Test: `app/src/test/java/com/repopilot/app/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Produces repository onboarding by GitHub URL or existing workspace and dashboard actions `scan`, `build`, `pull`, `git status`, `issues`, `AI`, `logs`.
- Consumes Task 3 dispatcher and Task 2 store.

- [ ] **Step 1: Write failing URL/parser and ViewModel tests** for HTTPS/SSH GitHub URLs, invalid URLs, existing local workspace, clone confirmation, and duplicate repository records.
- [ ] **Step 2: Run tests** and verify failure.
- [ ] **Step 3: Implement URL parsing and repository open/clone orchestration** through Termux; never clone before user confirmation of destination.
- [ ] **Step 4: Build Home and Repository Dashboard** following the Stitch dark compact baseline and showing branch, last scan, build state, and issue counts.
- [ ] **Step 5: Run unit tests and `assembleDebug`**; verify onboarding does not hold build logs in memory.
- [ ] **Step 6: Commit** with `feat: add repository onboarding and dashboard`.

### Task 6: Repo Doctor Engine and Android/Git/GitHub Actions Parsers

**Files:**
- Create: `app/src/main/java/com/repopilot/app/doctor/{DoctorEngine.kt,IssueNormalizer.kt,RuleResult.kt}`
- Create: `app/src/main/java/com/repopilot/app/doctor/rules/{EnvironmentRules,GitRules,GradleRules,GitHubActionsRules,HygieneRules}.kt`
- Create: `app/src/main/java/com/repopilot/app/doctor/parser/{GradleErrorParser,GitStatusParser,WorkflowFindingParser}.kt`
- Test: `app/src/test/java/com/repopilot/app/doctor/**/*.kt`
- Add fixtures: `app/src/test/resources/doctor/*`

**Interfaces:**
- Produces: `suspend fun DoctorEngine.scan(repoId: Long, depth: ScanDepth): List<NormalizedIssue>` where each issue includes severity, category, title, explanation, evidence, path/line, ruleId, autoFix capability, and AI-fix capability.
- Consumes bridge scan/status/build output.

- [ ] **Step 1: Write failing parser/rule tests** for dirty repo, detached HEAD, merge/rebase state, missing remote, Gradle config/compile/resource/manifest/dependency errors, wrapper problems, workflow Java mismatch, missing `gradlew` permission handling, tracked `.env`, likely token masking, and heuristic-warning labeling.
- [ ] **Step 2: Run doctor tests** and verify failure.
- [ ] **Step 3: Implement deterministic parsers/rules first**; heuristics must emit `WARNING` and include evidence without claiming certainty.
- [ ] **Step 4: Implement secret masking at issue creation time** so raw secret values never reach UI persistence.
- [ ] **Step 5: Integrate fast vs deep scan orchestration**; deep scan may invoke Gradle only through Termux.
- [ ] **Step 6: Run all doctor tests** and verify normalized issue outputs match fixtures.
- [ ] **Step 7: Commit** with `feat: add Repo Doctor scan engine`.

### Task 7: Issues UI, File Location View, and AI Prompt Generation

**Files:**
- Create: `app/src/main/java/com/repopilot/app/ui/issues/{IssuesFragment.kt,IssuesViewModel.kt,IssueAdapter.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/issues/FileLocationFragment.kt`
- Create: `app/src/main/java/com/repopilot/app/ai/{PromptBuilder.kt,SecretRedactor.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/ai/AiPromptPreviewFragment.kt`
- Create layouts for issues, issue item, file location, AI prompt preview.
- Test: `app/src/test/java/com/repopilot/app/ai/{PromptBuilderTest,SecretRedactorTest}.kt`

**Interfaces:**
- Produces `PromptBuilder.build(issue, contextSelection): RedactedPrompt` and UI actions **Fix It**, **Copy AI Prompt**, **View File**, **Ignore**.
- Consumes normalized issues from Task 6.

- [ ] **Step 1: Write failing tests** for prompt contents, minimal snippets, version context, unrelated-file exclusion, token/private-key/password redaction, and redaction markers.
- [ ] **Step 2: Run targeted tests** and verify failure.
- [ ] **Step 3: Implement redaction and prompt building**; prompt requires constraints to preserve unrelated behavior and return a unified diff plus validation command.
- [ ] **Step 4: Build Issues, File Location, and Prompt Preview screens**; copied/shared prompt always reflects the redacted preview.
- [ ] **Step 5: Run tests and `assembleDebug`**.
- [ ] **Step 6: Commit** with `feat: add issue actions and AI prompt preview`.

### Task 8: Safe Fix Proposals, Diff Preview, Apply, and Rollback

**Files:**
- Create: `app/src/main/java/com/repopilot/app/fix/{FixProposalService.kt,PatchValidator.kt,BranchNameGenerator.kt,RollbackManager.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/fixpreview/{FixPreviewFragment.kt,FixPreviewViewModel.kt}`
- Create: `app/src/main/res/layout/fragment_fix_preview.xml`
- Test: `app/src/test/java/com/repopilot/app/fix/{PatchValidatorTest,BranchNameGeneratorTest,RollbackManagerTest}.kt`

**Interfaces:**
- Produces `PatchValidationResult`, `FixProposal`, and `suspend fun approveAndApply(proposalId: Long): ApplyResult` that requires an already-recorded explicit approval.
- Consumes bridge `git-status`, `apply-patch-check`, `apply-patch`, `rollback`.

- [ ] **Step 1: Write failing safety tests** rejecting absolute paths, `../`, `.git/`, binary patches, symlink escapes, paths outside repo, dirty worktree auto-discard, and apply without approval.
- [ ] **Step 2: Add branch-name tests** ensuring `fix/<slug>-<id>` is safe, bounded, and never `main`/`master`.
- [ ] **Step 3: Run fix tests** and verify failure.
- [ ] **Step 4: Implement patch validation, proposal persistence, preview data, rollback metadata, and deterministic rule-based fix generation for the first safe rules** (for example executable `gradlew`/gitignore/workflow command corrections where evidence is exact).
- [ ] **Step 5: Implement Fix Preview UI** with rationale, affected files, unified diff, warnings, **Approve Fix**, and **Cancel**.
- [ ] **Step 6: On approval, create/switch safe fix branch, run bridge patch-check, apply, and persist rollback state**; stop on dirty/conflicted/interrupted Git state.
- [ ] **Step 7: Run tests and bridge fixture integration checks**.
- [ ] **Step 8: Commit** with `feat: add safe fix preview and rollback`.

### Task 9: Live Operations, Bounded Logs, Build/Test, and Build Results

**Files:**
- Create: `app/src/main/java/com/repopilot/app/ui/operation/{LiveOperationFragment.kt,LiveOperationViewModel.kt,BoundedLogBuffer.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/build/{BuildResultFragment.kt,BuildResultViewModel.kt}`
- Create layouts for live operation and build result.
- Test: `app/src/test/java/com/repopilot/app/ui/operation/BoundedLogBufferTest.kt`
- Test: `app/src/test/java/com/repopilot/app/ui/build/BuildResultViewModelTest.kt`

**Interfaces:**
- Produces serialized operation runner UI, bounded live logs, persisted full-log file references, safe cancellation when bridge operation supports it, and normalized build result with APK path when present.

- [ ] **Step 1: Write failing tests** for log buffer truncation, full-log persistence reference, build success, build failure extraction, cancellation, and second-heavy-job rejection.
- [ ] **Step 2: Run tests** and verify failure.
- [ ] **Step 3: Implement bounded log buffer and disk log writer**; large stdout never becomes a giant Room field.
- [ ] **Step 4: Implement Build APK/Test actions** through Termux `./gradlew`, capture duration/exit code/APK output path, and feed failure output back through Doctor parsers.
- [ ] **Step 5: Build Live Operation and Build Result screens** with elapsed time, current step, bounded logs, safe stop, Analyze Error, Copy AI Prompt, and Try AI Fix entry.
- [ ] **Step 6: Run tests and a fixture build integration test**.
- [ ] **Step 7: Commit** with `feat: add live build operations and results`.

### Task 10: Git Changes, Commit, and Separate Push Approval

**Files:**
- Create: `app/src/main/java/com/repopilot/app/ui/git/{GitChangesFragment.kt,GitChangesViewModel.kt}`
- Create: `app/src/main/java/com/repopilot/app/core/model/GitChangeSummary.kt`
- Create: `app/src/main/res/layout/fragment_git_changes.xml`
- Test: `app/src/test/java/com/repopilot/app/ui/git/GitChangesViewModelTest.kt`

**Interfaces:**
- Produces explicit `commit(message)` and separate `push()` actions; `push()` requires an existing commit on a non-protected fix branch and authenticated `gh`/Git remote.

- [ ] **Step 1: Write failing ViewModel tests** proving commit cannot occur before user action, push cannot occur before separate user action, push is blocked on `main`/`master`, and missing `gh` auth returns setup state without repo mutation.
- [ ] **Step 2: Run tests** and verify failure.
- [ ] **Step 3: Implement Git change summary, editable commit message, commit action, and separate push action** through fixed bridge commands.
- [ ] **Step 4: Build Git Changes screen** with branch, changed files, diff summary, commit, and push sections.
- [ ] **Step 5: Run tests and shell integration checks**.
- [ ] **Step 6: Commit** with `feat: add guarded commit and push workflow`.

### Task 11: Optional AI Providers, Encrypted Keys, and AI Fix Validation

**Files:**
- Create: `app/src/main/java/com/repopilot/app/ai/{AiProvider.kt,AiProviderClient.kt,AiFixService.kt,UnifiedDiffExtractor.kt}`
- Create adapters: `GeminiProvider.kt`, `GroqProvider.kt`, `OpenRouterProvider.kt`, `OpenAiCompatibleProvider.kt`
- Create: `app/src/main/java/com/repopilot/app/core/security/EncryptedSecretStore.kt`
- Create: `app/src/main/java/com/repopilot/app/ui/ai/{AiProvidersFragment.kt,AiProvidersViewModel.kt}`
- Create: `app/src/main/res/layout/fragment_ai_providers.xml`
- Test: `app/src/test/java/com/repopilot/app/ai/{AiFixServiceTest,UnifiedDiffExtractorTest}.kt`
- Test: `app/src/test/java/com/repopilot/app/core/security/EncryptedSecretStoreTest.kt`

**Interfaces:**
- Produces provider-agnostic `suspend fun AiFixService.propose(issueId: Long, providerId: String): FixProposalResult`.
- Consumes Task 7 redacted prompts and Task 8 patch validator; AI output never bypasses validation/preview/approval.

- [ ] **Step 1: Write failing mocked-provider tests** for valid unified diff, malformed response, unexpected path, secret echo, timeout, HTTP auth failure, quota/rate-limit response, and provider switch retry.
- [ ] **Step 2: Write failing secret-store tests** proving API keys are not returned in logs/serialized config and are separate from Room metadata.
- [ ] **Step 3: Run tests** and verify failure.
- [ ] **Step 4: Implement encrypted secret storage and provider adapters** with OkHttp, bounded timeouts, minimal redacted context, and no key logging.
- [ ] **Step 5: Implement AI fix extraction/validation**; only validated unified diff becomes a FixProposal and still requires Task 8 preview/approval.
- [ ] **Step 6: Build AI Providers screen** with provider enablement, masked key status, test connection, model/endpoint fields where relevant, and AI-off mode.
- [ ] **Step 7: Run tests** with network fully mocked.
- [ ] **Step 8: Commit** with `feat: add optional cloud AI fix providers`.

### Task 12: History, Conflict Recovery, Interruption Recovery, and Low-RAM Settings

**Files:**
- Create: `app/src/main/java/com/repopilot/app/ui/history/{HistoryFragment.kt,HistoryViewModel.kt}`
- Create: `app/src/main/java/com/repopilot/app/ui/settings/{SettingsFragment.kt,SettingsViewModel.kt}`
- Create: `app/src/main/java/com/repopilot/app/core/model/RecoveryState.kt`
- Create layouts for history/recovery/settings.
- Test: `app/src/test/java/com/repopilot/app/ui/history/HistoryViewModelTest.kt`
- Test: `app/src/test/java/com/repopilot/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces restart reconciliation, fix history/rollback entry points, conflict UI state, workspace/default settings, and low-RAM controls.

- [ ] **Step 1: Write failing tests** for app restart during command, unknown completion state, merge conflict, rollback availability, missing log file, and low-RAM defaults (background scan off, one heavy job, bounded live logs).
- [ ] **Step 2: Run tests** and verify failure.
- [ ] **Step 3: Implement recovery reconciliation** using persisted operation IDs plus bridge status refresh; never assume an interrupted mutation succeeded.
- [ ] **Step 4: Implement conflict/history screens** showing conflicted paths and offering only previewed/safe next actions; no automatic conflict resolution.
- [ ] **Step 5: Implement settings** for workspace folder, notifications, preferred AI provider, AI enable/disable, log retention, and optional Gradle cache cleanup action.
- [ ] **Step 6: Run tests and `assembleDebug`**.
- [ ] **Step 7: Commit** with `feat: add recovery history and low-RAM settings`.

### Task 13: End-to-End Approval Gates, UI Polish, Documentation, and Release Verification

**Files:**
- Create/modify: critical `app/src/androidTest/java/com/repopilot/app/*` tests
- Create: `README.md`
- Create: `docs/TERMUX_SETUP.md`, `docs/SECURITY.md`
- Modify: `.github/workflows/android.yml`
- Modify layouts/resources to align remaining Stitch visuals.

**Interfaces:**
- Produces a release-candidate debug APK and documented setup path from fresh install to safe repository fix/push.

- [ ] **Step 1: Write failing instrumentation tests** proving Fix cannot apply before approval, Commit cannot happen before explicit action, Push requires a second explicit action, destructive operations are absent, and secrets remain masked in Issues/Prompt/Logs views.
- [ ] **Step 2: Add a fake Termux dispatcher instrumentation mode** so approval flows are testable without real shell mutations.
- [ ] **Step 3: Run instrumentation tests** and verify failures before wiring final flows.
- [ ] **Step 4: Complete navigation/visual polish** against the Stitch baseline for Home, Dashboard, Issues, Fix Preview, Build Result, Git Changes, AI Providers, Termux Setup, Live Operation, Prompt Preview, File Viewer, History, Conflict Recovery, and Settings.
- [ ] **Step 5: Write setup/security documentation** including Termux RUN_COMMAND permission, bridge installation, Git/`gh` auth, API key behavior, and the exact safety guarantees/limitations.
- [ ] **Step 6: Run full verification:** `./gradlew testDebugUnitTest lintDebug assembleDebug`, bridge shell tests, and connected instrumentation tests when an emulator/device is available.
- [ ] **Step 7: Verify APK artifact exists and inspect CI workflow syntax**; on GitHub, confirm Actions uploads the latest debug APK artifact.
- [ ] **Step 8: Commit** with `test: verify RepoPilot v1 safety and release flow`.

---

## Completion Criteria

RepoPilot v1 is complete only when all of the following are true:

- A repository can be added/opened/cloned through Termux and shown on Home/Dashboard.
- Scan produces normalized issues with masked evidence and Android/Git/workflow coverage.
- Every actionable issue can generate a redacted Copy AI Prompt.
- Deterministic fixes and AI fixes both become validated proposals, never direct edits.
- Fix Preview blocks mutation until explicit approval.
- Approved fixes use a safe `fix/...` branch, build/test through Termux, and preserve rollback state.
- Commit and push require separate explicit user actions; protected branches and destructive Git operations are guarded.
- AI provider keys are encrypted locally and never leak into repo/log/prompt/Termux.
- Live logs stay memory-bounded and full logs persist to files.
- Interrupted operations/conflicts recover to an explicit state instead of being guessed successful.
- Unit tests, lint, debug build, bridge tests, and critical approval-gate UI tests pass.
- GitHub Actions produces a downloadable debug APK artifact.
