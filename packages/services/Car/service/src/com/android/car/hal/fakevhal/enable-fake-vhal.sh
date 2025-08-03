#!/bin/bash
# Enable VHAL mode on userdebug and eng build.
echo "Turning on Fake VHAL mode" \
  && touch /tmp/ENABLE \
  && echo "restarting adb shell as root" \
  && adb root \
  && adb wait-for-device \
  && adb push /tmp/ENABLE /data/system/car/fake_vhal_config/ENABLE \
  && echo "restarting adb shell" \
  && adb shell stop \
  && adb shell start \
  && echo "waiting 10s for car service to start" \
  && adb wait-for-device \
  && sleep 10s \
  && adb shell cmd car_service check-fake-vhal