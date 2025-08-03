# Fuzzers for libbtdevice

## Plugin Design Considerations
The fuzzer plugin for `libbtdevice` is designed based on the understanding of the
source code and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hard-coded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzers.

Fuzzer assigns values to the following parameters to pass on to libbtdevice:
1. Bluetooth Interop Feature (parameter name: `interopFeature`)
2. Bluetooth Esco Codec (parameter name: `escoCodec`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `interopFeature` | 0.`INTEROP_DISABLE_LE_SECURE_CONNECTIONS` 1.`INTEROP_AUTO_RETRY_PAIRING` 2.`INTEROP_DISABLE_ABSOLUTE_VOLUME` 3.`INTEROP_DISABLE_AUTO_PAIRING` 4.`INTEROP_KEYBOARD_REQUIRES_FIXED_PIN` 5.`INTEROP_2MBPS_LINK_ONLY` 6.`INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S` 7.`INTEROP_GATTC_NO_SERVICE_CHANGED_IND` 8.`INTEROP_DISABLE_AVDTP_RECONFIGURE` 9.`INTEROP_DYNAMIC_ROLE_SWITCH` 10.`INTEROP_DISABLE_ROLE_SWITCH` 11.`INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL` 12.`INTEROP_DISABLE_NAME_REQUEST` 13.`INTEROP_AVRCP_1_4_ONLY` 14.`INTEROP_DISABLE_SNIFF` 15.`INTEROP_DISABLE_AVDTP_SUSPEND` 16.`INTEROP_SLC_SKIP_BIND_COMMAND` 17.`INTEROP_AVRCP_1_3_ONLY`| Value obtained from FuzzedDataProvider |
| `escoCodec` | 0.`SCO_CODEC_CVSD_D1` 1.`ESCO_CODEC_CVSD_S3` 2.`ESCO_CODEC_CVSD_S4` 3.`ESCO_CODEC_MSBC_T1` 4.`ESCO_CODEC_MSBC_T2`| Value obtained from FuzzedDataProvider |
This also ensures that the plugins are always deterministic for any given input.

##### Maximize utilization of input data
The plugin feed the entire input data to the module.
This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesn't `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build btdevice_esco_fuzzer binary.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) btdevice_esco_fuzzer
```
### Steps to run

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/btdevice_esco_fuzzer/btdevice_esco_fuzzer
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
