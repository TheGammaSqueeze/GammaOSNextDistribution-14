## Build and install tests for riscv64 guest

m TARGET_BUILD_VARIANT=userdebug TARGET_PRODUCT=aosp_riscv64 berberis_jni_tests

adb install out/target/product/generic_riscv64/testcases/berberis_jni_tests/riscv64/berberis_jni_tests.apk


## Run tests

adb shell am instrument -w com.berberis.jnitests/androidx.test.runner.AndroidJUnitRunner


## Uninstall tests apk

adb uninstall com.berberis.jnitests
