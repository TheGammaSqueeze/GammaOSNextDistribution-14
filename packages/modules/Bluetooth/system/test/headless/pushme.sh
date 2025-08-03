#!/bin/bash

## Cuttlefish 64 bit bt_headless installation
PRODUCT=vsoc_x86_64

## Ensure that the device storage has been remounted
adb root
adb remount -R
adb wait-for-device

## Push various shared libraries where the executable expects to find them
adb push ${ANDROID_BUILD_TOP}/out/target/product/${PRODUCT}/symbols/system/bin/bt_headless /system/bin/bt_headless
