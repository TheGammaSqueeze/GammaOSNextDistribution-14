#! /bin/bash

source $ANDROID_BUILD_TOP/packages/modules/Bluetooth/system/cert/run \
  --test_config=$ANDROID_BUILD_TOP/packages/modules/Bluetooth/system/gd/cert/pts.json \
  --test_file=$ANDROID_BUILD_TOP/packages/modules/Bluetooth/system/gd/cert/pts_l2cap_testcase