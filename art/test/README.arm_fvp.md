# Testing ART on a model (QEMU or Arm FVP)

This document describes how to test ART on a model - QEMU or the ARM Fixed Virtual Platform.

It covers steps on how to build and run an Android system image targeting a model
and to use it as a target platform for running ART tests via ADB in chroot mode. The guide
covers both QEMU and the ARM Fixed Virtual Platform; the setup is very similar.

More information on QEMU and Arm FVP could be found in
{AOSP}/device/generic/goldfish/fvpbase/README.md.

One would need two AOSP trees for this setup:
 - a full stable (tagged) tree - to be used to build AOSP image for the model.
   - android-13.0.0_r12 was tested successfully to run QEMU:
     ```repo init  -u https://android.googlesource.com/platform/manifest -b android-13.0.0_r12```
 - a full or minimal tree - the one to be tested as part of ART test run.

## Setting up the QEMU/Arm FVP

Once a full AOSP tree is downloaded, please follow the instructions in
${AOSP}/device/generic/goldfish/fvpbase/README.md; they should cover:
 - fetching, configuring and building the model.
 - building AOSP image for it.
 - launching the model.

Once the model is started and reachable via adb, ART tests could be run.

Notes:
 - fvp_mini lunch target should be used as we don't need graphics to run ART tests.
 - 'Running the image in QEMU' mentions that a special commit should be checked out for QEMU
   for GUI runs. Actually it is recommended to use it even for non-GUI runs (fvp_mini).

### Running the Arm FVP with SVE enabled

To test SVE on Arm FVP, one extra step is needed when following the instructions above;
for QEMU run this is not needed. When launching the model some extra cmdline options should
be provided for 'run_model':

```
export SVE_PLUGIN=${MODEL_PATH}/plugins/<os_and_toolchain>/ScalableVectorExtension.so
$ ./device/generic/goldfish/fvpbase/run_model --plugin ${SVE_PLUGIN} -C SVE.ScalableVectorExtension.veclen=2
```

Note: SVE vector length is passed in units of 64-bit blocks. So "2" would stand
for 128-bit vector length.

## Running ART test

QEMU/FVP behaves as a regular adb device so running ART tests is possible using
the standard chroot method described in test/README.chroot.md with an additional step,
described below. A separate AOSP tree (not the one used for the model itself), should
be used - full or minimal.

Then the regular ART testing routine should be performed; the regular "lunch"
target ("armv8" and other targets, not "fvp-eng").

```
# Config the test run for QEMU/FVP.
export ART_TEST_RUN_ON_ARM_FVP=true

# Build, sync ART tests to the model and run, see test/README.chroot.md.
```

Note: ART scripts only support one adb device at a time. If you have other adb devices
connected, use `export ANDROID_SERIAL=localhost:5555` to run scripts on QEMU/FVP."
