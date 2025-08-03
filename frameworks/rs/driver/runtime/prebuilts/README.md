# RenderScript prebuilt runtime libraries (32-bit and 64-bit)

## Prebuilts Description

These prebuilts are for the RenderScript runtime libraries. They are
target-device-agnostic, and only differ or 32-bit vs. 64-bit usage. They are
generated and copied from the below listed Build ID, although local builds are
bit-identical. To regenerate these files, one would need to check out a tree
based on that manifest and make further changes to it in order to produce
different bitcode files.

http://b/301495450 tracks the switchover to prebuilts, although it is only
visible to Googlers.


## Build Information

* Build ID: 10858594
* Manifest: https://ci.android.com/builds/submitted/10858594/aosp_arm64-trunk_staging-userdebug/latest/manifest_10858594.xml
* Manifest: https://ci.android.com/builds/submitted/10858594/aosp_x86_64-trunk_staging-userdebug/latest/manifest_10858594.xml
* https://ci.android.com/builds/branches/aosp-main/grid?head=10858594&tail=10858594
* https://ci.android.com/builds/submitted/10858594/aosp_arm64-trunk_staging-userdebug/latest
  * Fetch aosp_arm64-target_files-10858594.zip
  * Unzip and extract the libclcore* files from `system/lib` and `system/lib64`.
* https://ci.android.com/builds/submitted/10858594/aosp_x86_64-trunk_staging-userdebug/latest
  * Fetch aosp_x86_64-target_files-10858594.zip
  * Unzip and extract the libclcore* files from `system/lib` and `system/lib64`.

Bug: http://b/301495450
Test: mm in frameworks/rs/driver/runtime with no Clang
