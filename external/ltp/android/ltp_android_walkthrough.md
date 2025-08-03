Building LTP for Android (complete walkthrough)
===============================================
This tutorial will walk you through building LTP for Android, starting from scratch.

Install build tools
-------------------
Follow the instructions here based on your operating system:

https://source.android.com/docs/setup/start/initializing?hl=en

Install repo
------------
```
sudo apt-get update
sudo apt-get install repo
```
(https://source.android.com/docs/setup/download?hl=en)

Checkout AOSP
-------------
**WARNING**: this step downloads ~300GB of files from the AOSP repository.

```
mkdir aosp-master
cd aosp-master
repo init -u https://android.googlesource.com/platform/manifest -b master --partial-clone --clone-filter=blob:limit=10M
repo sync -c -j8
```
(https://source.android.com/docs/setup/download/downloading?hl=en)

Set up environment
------------------
This configures the tooling to target cuttlefish arm64:
```
source build/envsetup.sh
lunch aosp_cf_arm64_phone-userdebug
```
Alternatively, this will target 32-bit arm:
```
source build/envsetup.sh
lunch aosp_cf_arm_only_phone-userdebug
```
If you are targeting something else, you can follow the link below for further instructions on how to adjust the target accordingly:

https://source.android.com/docs/setup/build/building?hl=en#choose-a-target

Build ADB and atest
-------------------
```
m -j adb atest
adb version
```
(https://source.android.com/docs/setup/build/adb?hl=en)

Connect the device
------------------
Connect the device to your system and verify that ADB can see it.
```
adb devices
```

Two ways to run LTP:
--------------------
### Use atest tool to build and run tests
https://android.googlesource.com/platform/external/ltp/+/master/android/README.md#running-ltp-through-atest
```
atest -a vts_ltp_test_arm     # 32-bit arm tests
atest -a vts_ltp_test_arm_64  # 64-bit arm tests
```

### Manually build and run tests
This is faster to build but requires more manual steps.

Setup temporary directories:
```
adb root
adb shell "mkdir -p /data/local/tmp/ltp/tmp/ltptemp; mkdir -p /data/local/tmp/ltp/tmp/tmpbase; mkdir -p /data/local/tmp/ltp/tmp/tmpdir; restorecon -F -R /data/local/tmp/ltp"
```

This example builds ltp and runs the `pcrypt_aead01` binary:
```
cd external/ltp
mma
adb sync data
adb shell "TMP=/data/local/tmp/ltp/tmp LTPTMP=/data/local/tmp/ltp/tmp/ltptemp LTP_DEV_FS_TYPE=ext4 TMPBASE=/data/local/tmp/ltp/tmp/tmpbase TMPDIR=/data/local/tmp/ltp/tmp/tmpdir LTPROOT=/data/local/tmp/ltp PATH=/data/nativetest64/ltp/testcases/bin:$PATH pcrypt_aead01"
```
(https://android.googlesource.com/platform/external/ltp/+/master/android/README.md#running-ltp-directly)

Modify an existing test and rerun it
------------------------------------
After making code changes to an existing test, following either of the previous steps will rebuild and run it.

If you are applying a patch file, you may do the following, filling in the patch file and binary:
```
cd external/ltp
git apply <patch filename>.patch
mma
adb sync data
adb shell "TMP=/data/local/tmp/ltp/tmp LTPTMP=/data/local/tmp/ltp/tmp/ltptemp LTP_DEV_FS_TYPE=ext4 TMPBASE=/data/local/tmp/ltp/tmp/tmpbase TMPDIR=/data/local/tmp/ltp/tmp/tmpdir LTPROOT=/data/local/tmp/ltp PATH=/data/nativetest64/ltp/testcases/bin:$PATH <test binary>"
```
