# Script to install SystemUI apk in system partition
APK_FILE="$1"
if [ -n "$2" ]; then
  export ANDROID_SERIAL="$2"
fi

# TODO(b/234033515): Device list info does not yet contain adb server port
# You might need to manually set this environment variable if you changed the adb server port in
# the Android Studio settings:
#export ANDROID_ADB_SERVER_PORT=

if [ -z "$APK_FILE" ]; then
    echo "Apk file not specified. Using default SystemUI-google-debug.apk"
    SCRIPT_DIR="$(cd $(dirname $0) && pwd)"
    BUILD_DIR="$SCRIPT_DIR/../../../../../../out/gradle/build/SysUIGradleProject/SystemUI/build"
    APK_FILE="$BUILD_DIR/intermediates/apk/google/debug/SystemUI-google-debug.apk"
fi

echo "ANDROID_SERIAL=$ANDROID_SERIAL"
echo "APK_FILE=$APK_FILE"

if [ ! -f "$APK_FILE" ]; then
    echo "Compiled APK not found $APK_FILE" > /dev/stderr
    exit 1
fi

adb root || exit 1
adb wait-for-device

VERITY_ENABLED="$(adb shell getprop | grep 'partition.*verified')"
if [ -n "$VERITY_ENABLED" ]; then
    echo "Disabling verity and rebooting"
    adb disable-verity
    adb reboot

    echo "Waiting for device"
    adb wait-for-device root
    adb wait-for-device
fi

adb remount

TARGET_PATH="$(adb shell pm path com.android.systemui | cut -d ':' -f 2)"
if [ -z "$TARGET_PATH" ]; then
    echo "Unable to get apk path: $TARGET_PATH]" > /dev/stderr
    exit 1
fi

echo "Pushing apk to device at $TARGET_PATH"
adb push "$APK_FILE" "$TARGET_PATH"
adb shell fsync "$TARGET_PATH"

# Restart the system, then wait up to 60 seconds for 'adb shell dumpsys package' to become available
echo "Restarting the system..."
adb shell 'stop ; start'
sleep 2
MAX_TRIES=29
N=0
while [[ "$N" -lt "$MAX_TRIES" && -z "$(adb shell dumpsys package com.android.systemui 2>&1 | grep versionName)" ]]; do
    sleep 2
    N="$((N+1))"
done

if [[ "$N" -ge "$MAX_TRIES" ]]; then
    echo "Timed out waiting for package service. Failed to run 'adb shell dumpsys package'."
    exit 1
fi

VERSION="$(adb shell dumpsys package com.android.systemui 2>&1 | grep versionName)"
if [[ "$VERSION" == *"BuildFromAndroidStudio"* ]]; then
    echo "Install complete"
else
    echo "Installation verification failed. Package versionName does not contain \"BuildFromAndroidStudio\" as expected."
    exit 1
fi
