#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
sdk_root="${WSC_ANDROID_EMULATOR_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
adb_bin="$sdk_root/platform-tools/adb"

if [[ ! -x "$adb_bin" ]]; then
    echo "adb not found at $adb_bin" >&2
    exit 1
fi

"$adb_bin" wait-for-device

until [[ "$("$adb_bin" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
done

"$adb_bin" shell settings put system accelerometer_rotation 0
"$adb_bin" shell wm user-rotation lock 1

exec "$project_dir/gradlew-java16" installDebug