# Custom Input Event

## Building
```bash
make SampleCustomInputService -j64
```

### Installing
```bash
adb install $OUT/target/product/emulator_car_x86/system/app/SampleCustomInputService/SampleCustomInputService.apk
```

## Start SampleCustomInputService
```bash
adb shell am start-foreground-service com.android.car.custominput.sample/.SampleCustomInputService
```

### Running tests

Steps to run unit tests:

1. Build and install SampleCustomInputService.apk (see above sections).
1. Then run:

```bash
atest SampleCustomInputServiceTest
```

## Inject events (test scripts)

These are the test scripts to demonstrate how CustomInputEvent can be used to implement OEM
partners non-standard events. They all represent hypothetical features for the sake of documentation
 only.

*Note*: Make sure SampleCustomInputService is installed and started. Especially if you've just
        ran tests. Depending on the configuration you use, running SampleCustomInputServiceTest may
        uninstall SampleCustomInputService.

### Inject Maps event from steering wheel control

For this example, press home first, then inject the event to start Maps activity by running:

```shell script
adb shell cmd car_service inject-custom-input -d 0 f1
```

Parameters are:
* `-d 0`: sets target display type to main display;
* `f1`: sets the OEM partner function `f1` to execute. In this implementation, `f1` argument
    represents the action used to launch Google maps app;

*Note*: For this command to run, ensure that Google Maps app is installed first.

### Inject events to accept or reject incoming calls

In this example, CustomInputEvents are send to accept or reject incoming calls. First cell phone
must be connected to Android via Bluetooth. Then, to accept incoming calls, run:

```shell script
adb shell cmd car_service inject-custom-input f2
```

Alternatively, to reject incoming calls, run:

```shell script
adb shell cmd car_service inject-custom-input f3
```

### Inject events to increase or decrease media volume

To increase media volume, run:

```shell script
adb shell cmd car_service inject-custom-input f4
```

To decrease media volume, run:

```shell script
adb shell cmd car_service inject-custom-input f5
```

### Inject events to increase or decrease alarm volume

To increase alarm volume, run:

```shell script
adb shell cmd car_service inject-custom-input f6
```

To decrease alarm volume, run:

```shell script
adb shell cmd car_service inject-custom-input f7
```

### Inject event to simulate pressing BACK HOME button

To simulate pressing BACK HOME button, run:

```shell script
adb shell cmd car_service inject-custom-input f8
```

To notify driver UI to move navigation view, run:

```shell script
adb shell cmd car_service inject-custom-input 1011
```
