#!/usr/bin/env bash

# Before running the script:
#
# * lunch with eng variant. For example:
#   lunch flame-eng
# * Build the full system image and flash your device
#   m; vendor/google/tools/flashall
# * Remount the device to allow system rw.
#   adb remount; adb reboot
# * Connect the device to host machine to allow atest to build properly

# Note: This script assumes the device runs with NEW_INSTALL_ONLY strategy.
# The script will have to be updated when switching to BEST_EFFORT strategy.

set -e

TEST_APP_PATH="$ANDROID_TARGET_OUT_TESTCASES/CtsSharedUserMigrationInstallTestApp"
PKGNAME='android.uidmigration.cts.InstallTestApp'

cleanup() {
  adb shell pm uninstall $PKGNAME >/dev/null 2>&1 || true
  adb shell pm uninstall-system-updates $PKGNAME >/dev/null 2>&1 || true
  adb shell stop || true
  adb shell rm -rf /system/app/InstallTestApp || true
  adb shell start || true
}

trap cleanup EXIT

wait_boot_complete() {
  while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    sleep 3
  done
}

export -f wait_boot_complete

adb_stop() {
  adb shell stop
  adb shell setprop sys.boot_completed 0
}

# Build our test APKs
atest -b CtsSharedUserMigrationTestCases

# APK references
#
# InstallTestApp : APK with sharedUserId
# InstallTestApp3: APK without sharedUserId
# InstallTestApp4: APK with sharedUserId + sharedUserMaxSdkVersion

# Make sure system is writable
adb root
adb remount

# Push the APK as a system app
adb shell mkdir /system/app/InstallTestApp
adb push $TEST_APP_PATH/*/*.apk /system/app/InstallTestApp/InstallTestApp.apk

# Restart PMS to load the package
adb_stop
adb shell start
timeout 60 bash -c wait_boot_complete

DUMPSYS_CMD="adb shell dumpsys package $PKGNAME | grep -o 'sharedUser=.*' | head -1"

# Make sure package is installed and is part of shared UID
SHARED_USER="$($DUMPSYS_CMD)"
if [ -z "$SHARED_USER" ]; then
  echo '! InstallTestApp is not installed properly'
  exit 1
fi

# Installing an upgrade with sharedUserMaxSdkVersion shall not change its UID
adb install -r ${TEST_APP_PATH}4/*/*.apk
SHARED_USER="$($DUMPSYS_CMD)"
if [ -z "$SHARED_USER" ]; then
  echo '! InstallTestApp should remain in shared UID after normal upgrade'
  exit 1
fi

echo '*****************'
echo '* Test 1 PASSED *'
echo '*****************'

# Uninstall the upgrade
# BUG? For some reason this command always return 1
adb shell pm uninstall-system-updates $PKGNAME || true

# Removing sharedUserId after an OTA should work
adb_stop
adb push ${TEST_APP_PATH}3/*/*.apk /system/app/InstallTestApp/InstallTestApp.apk
adb shell start
timeout 60 bash -c wait_boot_complete

if ! adb shell pm list packages | grep -q $PKGNAME; then
  echo '! InstallTestApp should still be installed after OTA'
  exit 1
fi

SHARED_USER="$($DUMPSYS_CMD)"
if [ -n "$SHARED_USER" ]; then
  echo '! InstallTestApp should not be in shared UID after removing sharedUserId'
  exit 1
fi

echo '*****************'
echo '* Test 2 PASSED *'
echo '*****************'

# Adding sharedUserId back after an OTA should work
adb_stop
adb push $TEST_APP_PATH/*/*.apk /system/app/InstallTestApp/InstallTestApp.apk
adb shell start
timeout 60 bash -c wait_boot_complete

SHARED_USER="$($DUMPSYS_CMD)"
if [ -z "$SHARED_USER" ]; then
  echo 'InstallTestApp should be in shared UID after adding sharedUserId!'
  exit 1
fi

echo '*****************'
echo '* Test 3 PASSED *'
echo '*****************'

# Adding sharedUserMaxSdkVersion in an OTA should not affect appId
adb_stop
adb push ${TEST_APP_PATH}4/*/*.apk /system/app/InstallTestApp/InstallTestApp.apk
adb shell start
timeout 60 bash -c wait_boot_complete

SHARED_USER="$($DUMPSYS_CMD)"
if [ -z "$SHARED_USER" ]; then
  echo '! InstallTestApp should be in shared UID even after adding sharedUserMaxSdkVersion after OTA'
  exit 1
fi

echo '*****************'
echo '* Test 4 PASSED *'
echo '*****************'

# Remove the app, restart, and reinstall APK with sharedUserMaxSdkVersion
# The new app should not be in shared UID (due to NEW_INSTALL_ONLY)
adb_stop
adb shell rm -rf /system/app/InstallTestApp
adb shell start
timeout 60 bash -c wait_boot_complete

adb_stop
adb shell mkdir /system/app/InstallTestApp
adb push ${TEST_APP_PATH}4/*/*.apk /system/app/InstallTestApp/InstallTestApp.apk
adb shell start
timeout 60 bash -c wait_boot_complete

if ! adb shell pm list packages | grep -q $PKGNAME; then
  echo '! InstallTestApp should be installed'
  exit 1
fi

SHARED_USER="$($DUMPSYS_CMD)"
if [ -n "$SHARED_USER" ]; then
  echo '! InstallTestApp should not be in shared UID when newly installed with sharedUserMaxSdkVersion'
  exit 1
fi

echo '*****************'
echo '* Test 5 PASSED *'
echo '*****************'
