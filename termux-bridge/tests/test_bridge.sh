#!/usr/bin/env bash
set -euo pipefail
BRIDGE="$(cd "$(dirname "$0")/.." && pwd)/repopilot-bridge"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
REPO="$TMP/repo with spaces"; mkdir -p "$REPO"; git -C "$REPO" init -b main >/dev/null
git -C "$REPO" config user.email test@example.com; git -C "$REPO" config user.name Test
printf 'hello\n' > "$REPO/a.txt"; git -C "$REPO" add a.txt; git -C "$REPO" commit -m init >/dev/null

echo '[1] unknown command rejects'; if bash "$BRIDGE" nope >/dev/null 2>&1; then exit 1; fi
echo '[2] path with spaces works'; OUT="$(bash "$BRIDGE" git-status "$REPO")"; grep -q 'branch: main' <<<"$OUT"
echo '[3] protected push guard'; if bash "$BRIDGE" push "$REPO" >/dev/null 2>&1; then exit 1; fi
echo '[4] patch check and apply'
PATCH="$TMP/p.patch"; cat > "$PATCH" <<'P'
diff --git a/a.txt b/a.txt
--- a/a.txt
+++ b/a.txt
@@ -1 +1 @@
-hello
+world
P
ENC="$(base64 -w0 "$PATCH" 2>/dev/null || base64 "$PATCH" | tr -d '\n')"
OUT="$(bash "$BRIDGE" apply-patch-check "$REPO" "$ENC" fix/test-1)"; grep -q PATCH_CHECK_OK <<<"$OUT"
OUT="$(bash "$BRIDGE" apply-patch "$REPO" "$ENC" fix/test-1)"; grep -q PATCH_APPLIED <<<"$OUT"
grep -q world "$REPO/a.txt"
echo '[5] dirty repo blocks second apply'; if bash "$BRIDGE" apply-patch-check "$REPO" "$ENC" fix/test-2 >/dev/null 2>&1; then exit 1; fi
echo '[6] rollback exact patch'; OUT="$(bash "$BRIDGE" rollback "$REPO" "$ENC")"; grep -q ROLLBACK_OK <<<"$OUT"; grep -q hello "$REPO/a.txt"
echo '[7] traversal patch rejected'
cat > "$PATCH" <<'P'
diff --git a/../evil b/../evil
--- a/../evil
+++ b/../evil
@@ -0,0 +1 @@
+x
P
ENC="$(base64 -w0 "$PATCH" 2>/dev/null || base64 "$PATCH" | tr -d '\n')"
if bash "$BRIDGE" apply-patch-check "$REPO" "$ENC" fix/evil-2 >/dev/null 2>&1; then exit 1; fi
echo 'BRIDGE_TESTS_OK'
