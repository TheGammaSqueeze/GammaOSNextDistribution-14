#!/usr/bin/bash

# Builds, installs, then runs a small test binary on an Android device that is
# attached to your workstation. This tool checks to see if the KeyMint
# instances on this device have been registered with the RKP backend.
#
# Run the script by passing the desired lunch target on the command-line:
# ./packages/modules/RemoteKeyProvisioning/util/RkpRegistrationCheck.sh <aosp_arm64-userdebug>

if [ -z "$1" ]; then
  echo "Lunch target must be specified"
  exit 1
fi

. build/envsetup.sh
lunch $1
m RkpRegistrationCheck

adb push $ANDROID_PRODUCT_OUT/system/framework/RkpRegistrationCheck.jar \
  /data/local/tmp

adb shell "CLASSPATH=/data/local/tmp/RkpRegistrationCheck.jar \
  exec app_process /system/bin com.android.rkpdapp.RkpRegistrationCheck"

adb shell "rm /data/local/tmp/RkpRegistrationCheck.jar"
