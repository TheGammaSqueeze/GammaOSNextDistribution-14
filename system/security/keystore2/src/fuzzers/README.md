# Fuzzers for libkeystore2
## Table of contents
+ [keystore2_unsafe_fuzzer](#Keystore2Unsafe)

# <a name="Keystore2Unsafe"></a> Fuzzer for Keystore2Unsafe
All the parameters of Keystore2Unsafe are populated randomly from libfuzzer. You can find the possible values in the fuzzer's source code.

#### Steps to run
1. Build the fuzzer
```
$ m -j$(nproc) keystore2_unsafe_fuzzer
```

2. Run on device
```
$ adb sync data
$ adb shell /data/fuzz/${TARGET_ARCH}/keystore2_unsafe_fuzzer/keystore2_unsafe_fuzzer
```
