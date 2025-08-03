#!/bin/bash

if [[ -z $ANDROID_BUILD_TOP ]]; then
    echo "ANDROID_BUILD_TOP not defined, lunch?"
    exit 1
fi

set -ex

$ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode hidl2aidl

hidl2aidl -o $ANDROID_BUILD_TOP/system/tools/hidl/hidl2aidl/test/build_test_delete_me \
  -rhidl2aidl.test:system/tools/hidl/hidl2aidl/test hidl2aidl.test@3.0

$ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode hidl2aidl.test3-update-api

$ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode \
      MODULES-IN-system-tools-hidl-hidl2aidl
