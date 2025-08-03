#!/usr/bin/env bash

set -e

wait_boot_complete() {
  while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    sleep 3
  done
}

# make /vendor wriable
adb root; adb wait-for-device
wait_boot_complete
adb remount -R
adb wait-for-device
adb root; adb wait-for-device
wait_boot_complete
adb remount

# push the apex
adb push com.android.libapexsupport.tests.apex /vendor/apex
adb reboot
adb wait-for-device

# run the test binary
adb shell /apex/com.android.libapexsupport.tests/bin/libapexsupport-tests

# clean up
adb root; adb wait-for-device
wait_boot_complete
adb remount
adb shell rm /vendor/apex/com.android.libapexsupport.tests.apex
adb reboot