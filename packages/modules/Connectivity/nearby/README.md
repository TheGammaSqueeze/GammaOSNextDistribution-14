# Nearby Mainline Module
This directory contains code for the AOSP Nearby mainline module.

##Directory Structure

`apex`
 - Files associated with the Nearby mainline module APEX.

`framework`
 - Contains client side APIs and AIDL files.

`jni`
 - JNI wrapper for invoking Android APIs from native code.

`native`
 - Native code implementation for nearby module services.

`service`
 - Server side implementation for nearby module services.

`tests`
 - Unit/Multi devices tests for Nearby module (both Java and native code).

## IDE setup

```sh
$ source build/envsetup.sh && lunch <TARGET>
$ cd packages/modules/Nearby
$ aidegen .
# This will launch Intellij project for Nearby module.
```
Note, the setup above may fail to index classes defined in proto, such
that all classes defined in proto shows red in IDE and cannot be auto-completed.
To fix, you can mannually add jar files generated from proto to the class path
as below.  First, find the jar file of presence proto with
```sh
ls $ANDROID_BUILD_TOP/out/soong/.intermediates/packages/modules/Connectivity/nearby/service/proto/presence-lite-protos/android_common/combined/presence-lite-protos.jar
```
Then, add the jar in IDE as below.
1. Menu: File > Project Structure
2. Select Modules at the left panel and select the Dependencies tab.
3. Select the + icon and select 1 JARs or Directories option.
4. Select the JAR file found above, make sure it is checked in the beginning square.
5. Click the OK button.
6. Restart the IDE to re-index.

## Build and Install

```sh
For master on AOSP (Android) host
$ source build/envsetup.sh
$ lunch aosp_oriole-trunk_staging-userdebug
$ m com.android.tethering
$ $ANDROID_BUILD_TOP/out/host/linux-x86/bin/deapexer decompress --input $ANDROID_PRODUCT_OUT/system/apex/com.android.tethering.capex --output /tmp/tethering.apex
$ adb install /tmp/tethering.apex
$ adb reboot

NOTE: Developers should use AOSP by default, udc-mainline-prod should not be used unless for Google internal features.
For udc-mainline-prod on Google internal host
Build unbundled module using banchan
$ source build/envsetup.sh
$ banchan com.google.android.tethering mainline_modules_arm64
$ m apps_only dist
$ adb install out/dist/com.google.android.tethering.apex
$ adb reboot
Ensure that the module you are installing is compatible with the module currently preloaded on the phone (in /system/apex/com.google.android.tethering.apex). Compatible means:

1. Same package name
2. Same keys used to sign the apex and the payload
3. Higher version

See go/mainline-local-build#build-install-local-module for more information
```

## Build and Install from tm-mainline-prod branch
When build and flash the APEX from tm-mainline-prod, you may see the error below.
```
[INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade of APEX package com.google.android.tethering is not allowed. Active version: 990090000 attempted: 339990000])
```
This is because the device is flashed with AOSP built from master or other branches, which has
prebuilt APEX with higher version. We can use root access to replace the prebuilt APEX with the APEX
built from tm-mainline-prod as below.
1. adb root && adb remount -R
2. cp tethering.next.apex com.google.android.tethering.apex
3. adb push  com.google.android.tethering.apex  /system/apex/
4. adb reboot
After the steps above, the APEX can be reinstalled with adb install -r.
(More APEX background can be found in https://source.android.com/docs/core/ota/apex#using-an-apex.)

## Build APEX to support multiple platforms
If you need to flash the APEX to different devices, Pixel 6, Pixel 7, or even devices from OEM, you
can share the APEX by ```source build/envsetup.sh && lunch aosp_arm64-userdebug```. This can avoid
 re-compiling for different targets.
