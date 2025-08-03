# ImsMedia UnitTests Suite

## 1. Introduction
- The `tests/` directory in _ImsMedia_ contains all the _ImsMedia_'s Unit Tests.
- As _ImsMedia_ consists of both java and native code, Unit Tests are divided into two major testmodules.
    * `ImsMediaJavaTests`   - _consists all junit tests._
    * `ImsMediaNativeTests` - _consists all gtest tests._
- A testgroup is a collection of multiple testmodules.
    * `ImsMedia-alltests`  - _consists two testmodules explained above._


## 2. Procedure to run tests with atest

Create and connect Cuttlefish before executing atest commands.

```
gcert
acloud create --local-image
acloud reconnect
```
#### 2.1 To run all tests in ImsMedia
```
atest -c --rebuild-module-info
```

#### 2.2 To run all tests in a testgroup
A testgroup is a collection of multiple testmodules.

```
atest --test-mapping :<TESTGROUP>
```
Example: To run all testmodules in `ImsMedia-alltests` testgroup.
```
atest --test-mapping :ImsMedia-alltests
```

#### 2.3 To run all tests in a testmodule
```
atest <TESTMODULE>
```
Example: To run all tests in `ImsMediaNativeTests` testmodule
```
atest ImsMediaNativeTests
```

#### 2.4 To run all tests in a test class
```
atest <TESTMODULE>:<TESTCLASS>
```
Example: To run all tests in _RtpBufferTest_ test class in `ImsMediaNativeTests` testmodule
```
atest ImsMediaNativeTests:RtpBufferTest
```

## 2.5 To build and run tests if new test is added

- Add `--rebuild-module-info` as last argument for any atest command to build and run.
- Add `-c` as first argument for any atest command to clean old cache.

Example: To build and run a test module along with clear cache:
```
atest -c <TESTMODULE> --rebuild-module-info
```
Example: To build and run `ImsMediaNativeTests` testmodule if new test is added
```
atest -c ImsMediaNativeTests --rebuild-module-info
```


## 3. Procedure to run tests without atest

After building _ImsMedia_ with `mm` command, connect Cuttlefish.

#### 3.1 Sync tests to Cuttlefish/device
```
$ adb root
$ adb remount
$ adb sync
```
#### 3.2.1 Run native tests on Cuttlefish/device
```
adb shell ./data/nativetest64/<TESTMODULE>/<TESTMODULE>
```
Example: To run `ImsMediaNativeTests` testmodule on CVD/device
```
adb shell ./data/nativetest64/ImsMediaNativeTests/ImsMediaNativeTests
```
#### 3.2.2 Run Java tests on Cuttlefish/device
Testapk should be installed on device
```
adb shell am instrument -w <test_package_name>/<runner_class>
```
Example: To run `ImsStackJavaTests` testmodule on CVD/device
```
adb shell am instrument -w com.android.telephony.imsmedia.tests.java.imsapp/androidx.test.runner.AndroidJUnitRunner
```