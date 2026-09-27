# RepoPilot / Repo Doctor

RepoPilot is a low-RAM Android developer utility that combines a repository doctor with a safe Termux execution engine.

## What it does

- Open or clone GitHub repositories through Termux.
- Scan Git/Gradle/Android repository health.
- Explain common build and repository failures.
- **Fix It** flow: AI or manual unified diff → local safety validation → Termux `git apply --check` → explicit **Approve Fix** → `fix/...` branch → apply.
- **Copy AI Prompt** for every issue with secret redaction.
- Optional Gemini, Groq, OpenRouter, or custom OpenAI-compatible provider.
- Build/test with the target repository's `./gradlew` in Termux.
- Separate explicit **Commit** and **Push** actions; direct push from main/master is blocked.
- Local operation history, exact-patch rollback, low-RAM defaults, bounded logs.

## Android requirements

- Android 8.0+ (API 26)
- Target / compile SDK 36
- Java 17 build toolchain
- No Compose, no WebView UI, no local LLM

## Build

GitHub Actions builds a debug APK on push and uploads artifact **RepoPilot-debug-apk**.

Local desktop build with Android SDK installed:

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The included `gradlew` is a small Gradle 8.13 bootstrap for environments with curl/unzip.

## Termux

See [`docs/TERMUX_SETUP.md`](docs/TERMUX_SETUP.md).

## Safety

See [`docs/SECURITY.md`](docs/SECURITY.md).
