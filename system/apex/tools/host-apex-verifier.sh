#!/bin/bash
set -x

echo "Pulling APEXes from the device factory APEX directories."
TEMP_DIR="`mktemp -d`"
adb pull /system/apex/ $TEMP_DIR/system
adb pull /system_ext/apex/ $TEMP_DIR/system_ext
adb pull /product/apex/ $TEMP_DIR/product
adb pull /vendor/apex/ $TEMP_DIR/vendor
adb pull /odm/apex/ $TEMP_DIR/odm

set -e

echo "Running host_apex_verifier."
SDK_VERSION="`adb shell getprop ro.build.version.sdk`"
TEST_DIR=$(dirname $0)
HOST_APEX_VERIFIER=$TEST_DIR/host_apex_verifier
DEBUGFS=$TEST_DIR/debugfs_static
DEAPEXER=$TEST_DIR/deapexer
$HOST_APEX_VERIFIER \
  --deapexer $DEAPEXER \
  --debugfs $DEBUGFS \
  --sdk_version $SDK_VERSION \
  --out_system $TEMP_DIR/system \
  --out_system_ext $TEMP_DIR/system_ext \
  --out_product $TEMP_DIR/product \
  --out_vendor $TEMP_DIR/vendor \
  --out_odm $TEMP_DIR/odm

rm -rf $TEMP_DIR
