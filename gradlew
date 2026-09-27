#!/usr/bin/env sh
set -eu
VERSION=8.13
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/repopilot-bootstrap"
HOME_DIR="$BASE/gradle-$VERSION"
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VERSION-bin.zip"
  echo "RepoPilot bootstrap: downloading Gradle $VERSION..." >&2
  curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  rm -rf "$HOME_DIR"
  unzip -q "$ZIP" -d "$BASE"
fi
exec "$HOME_DIR/bin/gradle" "$@"
