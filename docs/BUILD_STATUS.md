# Build / Verification Status

Verified locally in the ChatGPT build environment:

- Pure Kotlin RepoPilot core safety tests: PASS (`CORE_TESTS_OK`).
- Termux bridge shell syntax: PASS.
- Termux bridge safety integration tests: PASS (`BRIDGE_TESTS_OK`), including protected main push, dirty-worktree blocking, patch check/apply, exact rollback, path-with-spaces, and traversal rejection.
- `git diff --check`: PASS.

Android SDK tooling is not installed in this environment, so the Android APK compile is intentionally verified by `.github/workflows/android.yml` after the repository is pushed. The workflow installs SDK 36, Java 17 and Gradle 8.13, then runs unit tests, lint and `assembleDebug`, and uploads `RepoPilot-debug-apk`.
