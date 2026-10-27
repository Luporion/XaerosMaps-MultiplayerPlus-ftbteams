#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/verify-provenance.py
if command -v kotlinc >/dev/null 2>&1; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  kotlinc core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicy.kt \
    core/src/test/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicyTests.kt \
    -include-runtime -d "$tmp/policy.jar"
  java -jar "$tmp/policy.jar"
  kotlinc src/main/kotlin/dev/alinco8/xmmp/client/io/PersonalExplorationJournal.kt \
    src/test/kotlin/dev/alinco8/xmmp/client/io/PersonalExplorationJournalTests.kt \
    -include-runtime -d "$tmp/provenance.jar"
  java -jar "$tmp/provenance.jar"
else
  echo 'Local Kotlin compiler unavailable; GitHub Actions Gradle compilation required.'
fi
