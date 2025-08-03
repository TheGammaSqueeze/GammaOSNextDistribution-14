# RemoteAuth Mainline Module

This directory contains code for the RemoteAuth module.

## Directory Structure

`framework`
 - Contains client side APIs and AIDL files.

`jni`
 - JNI wrapper for invoking Android APIs from native code.

`native`
 - Native code implementation for RemoteAuth module services.

`service`
 - Server side implementation for RemoteAuth module services.

`tests`
 - Unit/Multi devices tests for RemoteAuth module (both Java and native code).

## IDE setup

### AIDEGen

```sh
$ source build/envsetup.sh && lunch <TARGET>
$ cd packages/modules/Connectivity
$ aidegen .
# This will launch Intellij project for RemoteAuth module.
```

## Build and Install

```sh
$ source build/envsetup.sh && lunch <TARGET>
$ m com.android.tethering deapexer
$ $ANDROID_BUILD_TOP/out/host/linux-x86/bin/deapexer decompress --input \
    ${ANDROID_PRODUCT_OUT}/system/apex/com.android.tethering.capex \
    --output /tmp/tethering.apex
$ adb install -r /tmp/tethering.apex
```
