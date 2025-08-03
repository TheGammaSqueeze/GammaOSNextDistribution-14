# Fuzzer for libhidlbase

## Table of contents
+ [libHidlBase_parcel_fuzzer](#libHidlBaseParcel)

# <a name="libHidlBaseParcel"></a> Fuzzer for libHidlBaseParcel

libHidlBaseParcel supports the following parameters:

1. ParentHandle (parameter name: "parentHandle")
2. ParentOffset (parameter name: "parentOffset")
3. HidlString (parameter name: "hidlString")

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
|`parentHandle`| `Integer` |Value obtained from FuzzedDataProvider|
|`parentOffset`| `Integer` |Value obtained from FuzzedDataProvider|
|`hidlString`| `Structure` |Value obtained from FuzzedDataProvider|

#### Steps to run
1. Build the fuzzer
```
  $ make libHidlBase_parcel_fuzzer
```
2. To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/libHidlBase_parcel_fuzzer/libHidlBase_parcel_fuzzer
```
