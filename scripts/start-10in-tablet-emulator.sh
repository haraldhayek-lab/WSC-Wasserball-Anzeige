#!/usr/bin/env bash
set -euo pipefail

sdk_root="${WSC_ANDROID_EMULATOR_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
cmdline_tools_root="${WSC_ANDROID_CMDLINE_TOOLS_ROOT:-$HOME/.local/android-sdk/cmdline-tools/latest}"
emulator_bin="$sdk_root/emulator/emulator"
avdmanager_bin="$cmdline_tools_root/bin/avdmanager"
adb_bin="$sdk_root/platform-tools/adb"
avd_name="${WSC_TABLET_AVD_NAME:-WSC_10in_Tablet_API_36_1}"
device_name="${WSC_TABLET_DEVICE_NAME:-Nexus 10}"
system_image="${WSC_TABLET_SYSTEM_IMAGE:-system-images;android-36.1;google_apis_playstore;x86_64}"
source_avd_id="${WSC_TABLET_SOURCE_AVD_ID:-Medium_Phone_API_36.1}"
avd_home="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

force_landscape() {
    "$adb_bin" wait-for-device

    until [[ "$("$adb_bin" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 2
    done

    "$adb_bin" shell settings put system accelerometer_rotation 0
    "$adb_bin" shell wm user-rotation lock 1
}

clone_source_avd() {
    local source_ini="$avd_home/$source_avd_id.ini"
    local source_dir="$avd_home/Medium_Phone.avd"
    local target_ini="$avd_home/$avd_name.ini"
    local target_dir="$avd_home/$avd_name.avd"

    if [[ ! -f "$source_ini" || ! -d "$source_dir" ]]; then
        echo "Fallback source AVD $source_avd_id is not available" >&2
        return 1
    fi

    rm -rf "$target_dir"
    cp -a "$source_dir" "$target_dir"
    rm -f "$target_dir/multiinstance.lock"
    rm -rf "$target_dir/snapshots"

    cat > "$target_ini" <<EOF
avd.ini.encoding=UTF-8
path=$target_dir
path.rel=avd/$avd_name.avd
target=android-36.1
EOF

    sed -i \
        -e "s/^AvdId=.*/AvdId=$avd_name/" \
        -e "s/^avd.ini.displayname=.*/avd.ini.displayname=WSC 10in Tablet API 36.1/" \
        -e "s/^hw.device.manufacturer=.*/hw.device.manufacturer=Google/" \
        -e "s/^hw.device.name=.*/hw.device.name=Nexus 10/" \
        -e "s/^hw.initialOrientation=.*/hw.initialOrientation=landscape/" \
        -e "s/^hw.lcd.density=.*/hw.lcd.density=300/" \
        -e "s/^hw.lcd.height=.*/hw.lcd.height=2560/" \
        -e "s/^hw.lcd.width=.*/hw.lcd.width=1600/" \
        -e "s/^hw.ramSize=.*/hw.ramSize=4096/" \
        -e "s/^skin.name=.*/skin.name=1600x2560/" \
        -e "s/^skin.path=.*/skin.path=1600x2560/" \
        "$target_dir/config.ini"

    sed -i \
        -e "s|Medium_Phone\.avd|$avd_name.avd|g" \
        -e "s/^hw.device.name =.*/hw.device.name = Nexus 10/" \
        -e "s/^hw.initialOrientation =.*/hw.initialOrientation = landscape/" \
        -e "s/^hw.lcd.density =.*/hw.lcd.density = 300/" \
        -e "s/^hw.lcd.height =.*/hw.lcd.height = 2560/" \
        -e "s/^hw.lcd.width =.*/hw.lcd.width = 1600/" \
        -e "s/^hw.ramSize =.*/hw.ramSize = 4096/" \
        -e "s/^avd.name =.*/avd.name = $avd_name/" \
        -e "s/^avd.id =.*/avd.id = $avd_name/" \
        "$target_dir/hardware-qemu.ini"
}

if [[ ! -x "$emulator_bin" ]]; then
    echo "Android emulator not found at $emulator_bin" >&2
    exit 1
fi

if [[ ! -x "$avdmanager_bin" ]]; then
    echo "avdmanager not found at $avdmanager_bin" >&2
    exit 1
fi

if [[ ! -x "$adb_bin" ]]; then
    echo "adb not found at $adb_bin" >&2
    exit 1
fi

export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"

if ! "$emulator_bin" -list-avds | grep -Fxq "$avd_name"; then
    package_xml="$sdk_root/${system_image//;/\/}/package.xml"
    if [[ -f "$package_xml" ]]; then
        if ! printf 'no\n' | "$avdmanager_bin" create avd --force --name "$avd_name" --package "$system_image" --device "$device_name"; then
            clone_source_avd
        fi
    else
        clone_source_avd
    fi
fi

"$emulator_bin" \
    -avd "$avd_name" \
    -netdelay none \
    -netspeed full \
    -gpu auto \
    -no-snapshot-save &

emulator_pid=$!
force_landscape
wait "$emulator_pid"