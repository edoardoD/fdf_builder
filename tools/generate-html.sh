#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :desktopApp:runHtmlCli --console=plain -q "$@"
