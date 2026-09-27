# RepoPilot Termux Setup

RepoPilot delegates Git, Gradle, patching, tests, builds, commit and push operations to Termux.

## 1. Termux packages

```bash
pkg update
pkg install git curl bash coreutils openjdk-17 gh
```

Authenticate GitHub when push access is needed:

```bash
gh auth login
```

## 2. Allow RUN_COMMAND integration

RepoPilot requests the official `com.termux.permission.RUN_COMMAND` permission. In Termux:

```bash
mkdir -p ~/.termux
grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

Fully restart Termux once after changing this setting.

## 3. Install the RepoPilot bridge

After this repository is on GitHub:

```bash
curl -fsSL https://raw.githubusercontent.com/mazharmnzoor4227-beep/Repo-doctor/main/termux-bridge/repopilot-bridge -o $PREFIX/bin/repopilot-bridge
chmod +x $PREFIX/bin/repopilot-bridge
repopilot-bridge environment-status
```

## Safety

The bridge uses fixed subcommands and never `eval`. It refuses direct RepoPilot commit/push from `main` or `master`, refuses dirty worktrees before patch application, refuses path traversal / `.git` / binary patches, and does not force-push or rewrite Git history.
