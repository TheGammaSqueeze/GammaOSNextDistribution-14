# dEQP for Android

## Notes on `deqp-binary`

`deqp-binary` (defined in `Android.bp`) allows running dEQP offscreen
from a single binary via adb shell. Not all tests will work,
but most should be fine.

Example usage:

```sh
cd $ANDROID_PRODUCT_OUT/testcases/deqp-binary/arm64
adb push ./ /data/local/tmp/
adb shell "cd /data/local/tmp && ./deqp-binary64 --deqp-caselist-file=/data/local/tmp/vk-incremental-deqp.txt --deqp-log-images=disable --deqp-log-filename=/sdcard/vk-incremental-deqp-results.qpa --deqp-surface-type=fbo --deqp-surface-width=2048 --deqp-surface-height=2048"
adb shell "cd /data/local/tmp && ./deqp-binary64 --deqp-caselist-file=/data/local/tmp/gles3-incremental-deqp.txt --deqp-log-images=disable --deqp-log-filename=/sdcard/gles3-incremental-deqp-results.qpa --deqp-surface-type=fbo --deqp-surface-width=2048 --deqp-surface-height=2048"
# Check results.
adb logcat -s dEQP
```
