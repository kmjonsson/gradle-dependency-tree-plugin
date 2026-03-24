#!/usr/bin/env bash
set -euo pipefail

PLUGIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-.}"
TASK="${2:-allDependencyTrees}"

if [[ ! -d "$TARGET" ]]; then
    echo "error: '$TARGET' is not a directory" >&2
    echo "usage: $0 [project-dir] [task]" >&2
    exit 1
fi

echo "Publishing plugin to local Maven..."
cd "$PLUGIN_DIR"
./gradlew publishToMavenLocal -q

cd "$TARGET"
GRADLEW="$([ -f ./gradlew ] && echo ./gradlew || echo gradle)"

"$GRADLEW" \
    --init-script "$PLUGIN_DIR/init.gradle" \
    "$TASK"
