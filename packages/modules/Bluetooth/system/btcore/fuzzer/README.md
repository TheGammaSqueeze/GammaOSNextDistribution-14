# Fuzzers for libbtcore

## Plugin Design Considerations
The fuzzer plugins for `libbtcore` are designed based on the understanding of the
source code and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hard-coded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzers.

Fuzzers assigns values to the following parameters to pass on to libbtcore:
1. Bluetooth Device Type (parameter name: `deviceType`)
2. Bluetooth Adapter Visibility Mode (parameter name: `mode`)
3. Bluetooth Address (parameter name: `btAddress`)
4. Bluetooth Device Class parameter (parameter name: `deviceClassT`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `deviceType` | 0.`BT_DEVICE_DEVTYPE_BREDR` 1.`BT_DEVICE_DEVTYPE_BLE` 2.`BT_DEVICE_DEVTYPE_DUAL` | Value obtained from FuzzedDataProvider |
| `mode` | 0.`BT_SCAN_MODE_NONE` 1.`BT_SCAN_MODE_CONNECTABLE` 2.`BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE` | Value obtained from FuzzedDataProvider |
| `btAddress` | Values inside array ranges from `0x0` to `0xFF` | Value obtained from FuzzedDataProvider |
| `deviceClassT` | Values inside array ranges from `0x0` to `0xFF` | Value obtained from FuzzedDataProvider |
This also ensures that the plugins are always deterministic for any given input.

##### Maximize utilization of input data
The plugins feed the entire input data to the module.
This ensures that the plugins tolerates any kind of input (empty, huge,
malformed, etc) and doesn't `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build btcore_device_class_fuzzer, btcore_property_fuzzer and btcore_module_fuzzer binaries.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) btcore_device_class_fuzzer
  $ mm -j$(nproc) btcore_property_fuzzer
  $ mm -j$(nproc) btcore_module_fuzzer
```
### Steps to run

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/btcore_device_class_fuzzer/btcore_device_class_fuzzer
  $ adb shell /data/fuzz/arm64/btcore_property_fuzzer/btcore_property_fuzzer
  $ adb shell /data/fuzz/arm64/btcore_module_fuzzer/btcore_module_fuzzer
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
