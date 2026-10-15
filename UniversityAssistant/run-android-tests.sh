#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
trap 'adb logcat -d > emulator.log || true' EXIT
python3 verify-version.py source
adb logcat -c
gradle --no-daemon --no-build-cache --rerun-tasks :app:connectedDebugAndroidTest --stacktrace
