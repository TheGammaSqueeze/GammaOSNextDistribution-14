# Custom Input Event

## Building
```bash
make SampleVoiceAssistInputService -j64
```

### Installing
```bash
adb install $OUT/target/product/emulator_car_x86/system/app/SampleVoiceAssistInputService/SampleVoiceAssistInputService.apk
```

## Start SampleVoiceAssistInputService
```bash
adb shell am start-foreground-service com.android.car.voiceassistinput.sample/.SampleVoiceAssistInputService
```

### Running tests

Steps to run unit tests:

1. Build and install SampleVoiceAssistInputService.apk (see above sections).
1. Then run:

```bash
atest SampleVoiceAssistInputServiceTest
```

## Inject events (test script)

This is a test script to demonstrate how VoiceAssistInputEvent can be used to implement OEM
partners non-standard handling of the voice assist key.

*Note*: Make sure SampleVoiceAssistInputService is installed and started. Especially if you've just
        ran tests. Depending on the configuration you use, running SampleVoiceAssistInputServiceTest may
        uninstall SampleVoiceAssistInputService.

### Inject Voice Assist event from push to talk button

For this example, press home first, then inject the event to start Assistant activity:

```shell script
adb shell cmd car_service inject-key 231
```
