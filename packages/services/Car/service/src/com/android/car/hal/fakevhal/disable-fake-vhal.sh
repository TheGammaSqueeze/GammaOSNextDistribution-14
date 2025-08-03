#!/bin/bash
# Disable VHAL mode on userdebug and eng build.
echo "Turning off Fake VHAL mode" \
  && echo "restarting adb shell as root" \
  && adb root \
  && adb wait-for-device \
  && adb shell rm /data/system/car/fake_vhal_config/ENABLE \
  && echo "restarting adb shell" \
  && adb shell stop \
  && adb shell start \
  && echo "waiting 10s for car service to start" \
  && adb wait-for-device \
  && sleep 10s \
  && adb shell cmd car_service check-fake-vhal