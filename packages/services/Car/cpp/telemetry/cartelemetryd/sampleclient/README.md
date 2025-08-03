# ICarTelemetry Sample Client

This is a sample vendor service that sends `CarData` to car telemetry service.

## Running

**1. Quick mode - under root**

```
m -j android.automotive.telemetryd-sampleclient

adb root
adb remount  # make sure run "adb disable-verity" before remounting
adb push $ANDROID_PRODUCT_OUT/vendor/bin/android.automotive.telemetryd-sampleclient /system/bin/

adb shell /system/bin/android.automotive.telemetryd-sampleclient --batch-size 1000 --cardata-size 1000 --interval-micros 10

# Then check logcat and dumpsys to verify the results. The following command enables VERBOSE logs.
adb shell setprop log.tag.android.automotive.telemetryd@1.0 V
adb logcat -v color -b all -T 1000
```

**2. Under vendor**

To include it in the final image, add
`PRODUCT_PACKAGES += android.automotive.telemetryd-sampleclient` to
`//packages/services/Car/cpp/telemetry/cartelemetryd/products/telemetry.mk` (or other suitable mk file).

```
# this goes to products/telemetry.mk

PRODUCT_PACKAGES += android.automotive.telemetryd-sampleclient
```

The sampleclient doesn't automatically start during boot, to start manually, run:
`adb shell /vendor/bin/android.automotive.telemetryd-sampleclient`.

If you want to test it by running `init`, add these SELinux rules:

```
# this goes to sepolicy/private/cartelemetryd.te

type cartelemetryd_sample, domain;
type cartelemetryd_sample_exec, vendor_file_type, exec_type, file_type;
init_daemon_domain(cartelemetryd_sample)
```

```
# this goes to sepolicy/private/file_contexts

/vendor/bin/android\.automotive\.telemetryd-sampleclient  u:object_r:cartelemetryd_sample_exec:s0
```

And create an `.rc` file:

```
# File: cartelemetryd-sampleclient.rc
# Don't forget to add `init_rc: ["cartelemetryd-sampleclient.rc"],` to the Android.bp

service cartelemetryd_sample /vendor/bin/android.automotive.telemetryd-sampleclient
    class hal
    user system
    group system
    oneshot  # run once, otherwise init keeps restarting it
    disabled  # do not start automatically
```
