#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if ! command -v java >/dev/null || ! command -v gradle >/dev/null; then
  echo 'JDK 17 and Gradle 8.9 are required.' >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f local.properties ]]; then
  echo 'Set ANDROID_HOME or sdk.dir in local.properties.' >&2
  exit 1
fi
python3 verify-version.py source
variant="${BUILD_VARIANT:-debug}"
mkdir -p dist
rm -f dist/*.apk dist/build-info.json
if [[ "$variant" == release ]]; then
  if [[ -z "${ANDROID_SIGNING_FILE:-}" || -z "${ANDROID_SIGNING_PASSWORD:-}" || ! -f "${ANDROID_SIGNING_FILE:-}" ]]; then
    echo 'Release signing is required. Configure the existing signing key. See UPDATE_SECURITY_FA.md.' >&2
    exit 1
  fi
  gradle --no-daemon :app:assembleRelease :app:lintRelease "$@"
elif [[ "$variant" == debug ]]; then
  gradle --no-daemon :app:assembleDebug :app:lintDebug "$@"
else
  echo 'BUILD_VARIANT must be debug or release.' >&2
  exit 1
fi

if [[ "$variant" == release ]]; then
  apk=app/build/outputs/apk/release/app-release.apk
else
  apk=app/build/outputs/apk/debug/app-debug.apk
fi
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -n "$sdk_dir" && -x "$sdk_dir/build-tools/35.0.0/apksigner" ]]; then
  "$sdk_dir/build-tools/35.0.0/apksigner" verify "$apk"
else
  echo 'APK signature verification tool missing (Build Tools 35.0.0).' >&2
  exit 1
fi
if command -v apkanalyzer >/dev/null; then
  permissions="$(apkanalyzer manifest permissions "$apk")"
  if [[ "$permissions" == *"android.permission.INTERNET"* ]]; then
    echo 'Unexpected INTERNET permission in merged APK manifest.' >&2
    exit 1
  fi
  if [[ "$variant" == release && "$(apkanalyzer manifest debuggable "$apk")" != false ]]; then
    echo 'Release APK unexpectedly debuggable.' >&2
    exit 1
  fi
else
  echo 'apkanalyzer is required to verify the final APK manifest.' >&2
  exit 1
fi
python3 verify-version.py apk "$apk"
echo 'APK signature, version and offline manifest checks passed.'

cp "$apk" "dist/Daneshyar-1.2.3-$variant.apk"
echo "Verified build artifact: dist/Daneshyar-1.2.3-$variant.apk (build type: $variant)"
