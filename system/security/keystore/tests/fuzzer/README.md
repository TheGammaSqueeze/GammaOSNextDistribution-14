# Fuzzer for libkeystore
## Table of contents
+ [libkeystore-get-wifi-hidl](#libkeystore-get-wifi-hidl)
+ [libkeystore_attestation_application_id](#libkeystore_attestation_application_id)

# <a name="libkeystore-get-wifi-hidl"></a> Fuzzer for libkeystore-get-wifi-hidl
## Plugin Design Considerations
The fuzzer plugin for libkeystore-get-wifi-hidl is designed based on the understanding of the library and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hardcoded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzer.

libkeystore-get-wifi-hidl supports the following parameters:
1. Key (parameter name: `key`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `key` | `String` | Value obtained from FuzzedDataProvider|

This also ensures that the plugin is always deterministic for any given input.

##### Maximize utilization of input data
The plugin feeds the entire input data to the libkeystore-get-wifi-hidl module.
This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesnt `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build keystoreGetWifiHidl_fuzzer binary.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) keystoreGetWifiHidl_fuzzer
```
#### Steps to run

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/${TARGET_ARCH}/keystoreGetWifiHidl_fuzzer/keystoreGetWifiHidl_fuzzer
```

# <a name="libkeystore_attestation_application_id"></a> Fuzzer for libkeystore_attestation_application_id
## Plugin Design Considerations
The fuzzer plugin for libkeystore-attestation-application-id are designed based on the understanding of the library and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hardcoded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzer.

libkeystore-attestation-application-id supports the following parameters:
1. Package Name (parameter name: `packageName`)
2. Version Code (parameter name: `versionCode`)
3. Uid (parameter name: `uid`)


| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `packageName` | `String` | Value obtained from FuzzedDataProvider|
| `versionCode` | `INT64_MIN` to `INT64_MAX` | Value obtained from FuzzedDataProvider|
| `uid` | `0` to `1000` | Value obtained from FuzzedDataProvider|

This also ensures that the plugin is always deterministic for any given input.

##### Maximize utilization of input data
The plugins feed the entire input data to the libkeystore_attestation_application_id module.
This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesnt `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build keystoreSignature_fuzzer, keystorePackageInfo_fuzzer, keystoreApplicationId_fuzzer and keystoreAttestationId_fuzzer binary.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) keystoreSignature_fuzzer
  $ mm -j$(nproc) keystorePackageInfo_fuzzer
  $ mm -j$(nproc) keystoreApplicationId_fuzzer
  $ mm -j$(nproc) keystoreAttestationId_fuzzer
```
#### Steps to run

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/${TARGET_ARCH}/keystoreSignature_fuzzer/keystoreSignature_fuzzer
  $ adb shell /data/fuzz/${TARGET_ARCH}/keystorePackageInfo_fuzzer/keystorePackageInfo_fuzzer
  $ adb shell /data/fuzz/${TARGET_ARCH}/keystoreApplicationId_fuzzer/keystoreApplicationId_fuzzer
  $ adb shell /data/fuzz/${TARGET_ARCH}/keystoreAttestationId_fuzzer/keystoreAttestationId_fuzzer
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
