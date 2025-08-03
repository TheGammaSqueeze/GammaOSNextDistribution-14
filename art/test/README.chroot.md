# ART Chroot-Based On-Device Testing

This file documents the use of a chroot environment in on-device testing of the
Android Runtime (ART). Using a chroot allows tests to run a standalone ART from
a locally built source tree on a device running (almost any) system image and
does not interfere with the Runtime installed in the device's system partition.

## Introduction

The Android Runtime (ART) supports testing in a chroot-based environment, by
setting up a chroot directory in a `ART_TEST_CHROOT` directory located under
`/data/local` (e.g. `ART_TEST_CHROOT=/data/local/art-test-chroot`) on a device,
installing ART and all other required artifacts there, and having tests use `adb
shell chroot $ART_TEST_CHROOT <command>` to execute commands on the device
within this environment.

This way to run tests using a "standalone ART" ("guest system") only affects
files in the data partition (the system partition and other partitions are left
untouched) and is as independent as possible from the Android system ("host
system") running on the device. This has some benefits:

* no need to build and flash a whole device to do ART testing (or "overwriting"
  an existing ART by syncing the system partition);
* the possibility to use a smaller AOSP Android manifest
  ([`master-art`](https://android.googlesource.com/platform/manifest/+/refs/heads/master-art/default.xml))
  to build ART and the required dependencies for testing;
* no instability due to updating/replacing ART on the system partition (a
  functional Android Runtime is necessary to properly boot a device);
* the possibility to have several standalone ART instances (one per directory,
  e.g. `/data/local/art-test-chroot1`, `/data/local/art-test-chroot2`, etc.).

Note that using this chroot-based approach requires root access to the device
(i.e. be able to run `adb root` successfully).

## Quick User Guide

0. Unset variables which are not used with the chroot-based approach (if they
   were set previously):
   ```bash
   unset ART_TEST_ANDROID_ROOT
   unset CUSTOM_TARGET_LINKER
   unset ART_TEST_ANDROID_ART_ROOT
   unset ART_TEST_ANDROID_RUNTIME_ROOT
   unset ART_TEST_ANDROID_I18N_ROOT
   unset ART_TEST_ANDROID_TZDATA_ROOT
   ```
1. Set the chroot directory in `ART_TEST_CHROOT`:
    ```bash
    export ART_TEST_CHROOT=/data/local/art-test-chroot
    ```
2. Set lunch target and ADB:
    * With a minimal `aosp/master-art` tree:
        1. Initialize the environment:
            ```bash
            export SOONG_ALLOW_MISSING_DEPENDENCIES=true
            . ./build/envsetup.sh
            ```
        2. Select a lunch target corresponding to the architecture you want to
           build and test:
            * For (32-bit) Arm:
                ```bash
                lunch arm_krait-trunk_staging-eng
                ```
            * For (64-bit only) Arm64:
                ```bash
                lunch armv8-trunk_staging-eng
                ```
            * For (32- and 64-bit) Arm64:
                ```bash
                lunch arm_v7_v8-trunk_staging-eng
                ```
            * For (32-bit) Intel x86:
                ```bash
                lunch silvermont-trunk_staging-eng
                ```
            * For (64-bit) RISC-V:
                ```bash
                lunch aosp_riscv64-trunk_staging-eng
                ```
        3. Set up the environment to use a pre-built ADB:
            ```bash
            export PATH="$(pwd)/prebuilts/runtime:$PATH"
            export ADB="$ANDROID_BUILD_TOP/prebuilts/runtime/adb"
            ```
    * With a full Android (AOSP) `aosp/main` tree:
        1. Initialize the environment:
            ```bash
            . ./build/envsetup.sh
            ```
        2. Select a lunch target corresponding to the architecture you want to
           build and test:
            * For (32-bit) Arm:
                ```bash
                lunch aosp_arm-trunk_staging-eng
                ```
            * For (32- and 64-bit) Arm64:
                ```bash
                lunch aosp_arm64-trunk_staging-eng
                ```
            * For (32-bit) Intel x86:
                ```bash
                lunch aosp_x86-trunk_staging-eng
                ```
            * For (32- and 64-bit) Intel x86-64:
                ```bash
                lunch aosp_x86_64-trunk_staging-eng
                ```
            * For (64-bit) RISC-V:
                ```bash
                lunch aosp_riscv64-trunk_staging-eng
                ```
        3. Build ADB:
            ```bash
            m adb
            ```
3. Build ART and required dependencies:
    ```bash
    art/tools/buildbot-build.sh --target
    ```
    After building it is fine to see it finish with an error like:
    ```
    linkerconfig E [...] variableloader.cc:83] Unable to access VNDK APEX at path: <path>: No such file or directory
    ```
4. Clean up the device:
    ```bash
    art/tools/buildbot-cleanup-device.sh
    ```
5. Setup the device (including setting up mount points and files in the chroot
   directory):
    ```bash
    art/tools/buildbot-setup-device.sh
    ```
6. Populate the chroot tree on the device (including "activating" APEX packages
   in the chroot environment):
    ```bash
    art/tools/buildbot-sync.sh
    ```
7. Run ART gtests:
    ```bash
    art/tools/run-gtests.sh -j4
    ```
    * Specific tests to run can be passed on the command line, specified by
      their absolute paths beginning with `/apex/`:
        ```bash
        art/tools/run-gtests.sh \
          /apex/com.android.art/bin/art/arm64/art_cmdline_tests \
          /apex/com.android.art/bin/art/arm64/art_dexdump_tests
        ```
    * Gtest options can be passed to each gtest by passing them after `--`; see
      the following examples.
        * To print the list of all test cases of a given gtest, use option
          `--gtest_list_tests`:
            ```bash
            art/tools/run-gtests.sh \
              /apex/com.android.art/bin/art/arm64/art_cmdline_tests \
              -- --gtest_list_tests
            ```
        * To filter the test cases to execute, use option `--gtest_filter`:
            ```bash
            art/tools/run-gtests.sh \
              /apex/com.android.art/bin/art/arm64/art_cmdline_tests \
              -- --gtest_filter="*TestJdwp*"
            ```
        * To see all the options supported by a gtest, use option `--help`:
            ```bash
            art/tools/run-gtests.sh \
              /apex/com.android.art/bin/art/arm64/art_cmdline_tests \
              -- --help
            ```
    * Note: Some test cases of `art_runtime_tests` defined in
    `art/runtime/gc/space/image_space_test.cc` may fail when using the full AOSP
    tree (b/119815008).
        * Workaround: Run `m clean-oat-host` before the build step
        (`art/tools/buildbot-build.sh --target`) above.
    * Note: The `-j` option of script `art/tools/run-gtests.sh` is not honored
      yet (b/129930445). However, gtests themselves support parallel execution,
      which can be specified via the gtest option `-j`:
        ```bash
        art/tools/run-gtests.sh -- -j4
        ```
8. Run ART run-tests:
    * On a 64-bit target:
        ```bash
        art/test/testrunner/testrunner.py --target --64
        ```
    * On a 32-bit target:
        ```bash
        art/test/testrunner/testrunner.py --target --32
        ```
9. Run Libcore tests:
    * On a 64-bit target:
        ```bash
        art/tools/run-libcore-tests.sh --mode=device --variant=X64
        ```
    * On a 32-bit target:
        ```bash
        art/tools/run-libcore-tests.sh --mode=device --variant=X32
        ```
10. Run JDWP tests:
    * On a 64-bit target:
        ```bash
        art/tools/run-libjdwp-tests.sh --mode=device --variant=X64
        ```
    * On a 32-bit target:
        ```bash
        art/tools/run-libjdwp-tests.sh --mode=device --variant=X32
        ```
11. Tear down device setup:
    ```bash
    art/tools/buildbot-teardown-device.sh
    ```
12. Clean up the device:
    ```bash
    art/tools/buildbot-cleanup-device.sh
    ```
