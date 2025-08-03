# Vogar

Vogar is a generic code/test/benchmark runner tool for Android. It is
primarily used to run libcore and art tests and benchmarks, however
this tool can also run arbitrary Java files either on host or target
device.

Vogar supports multiple testing frameworks and configurations:

 * Allows running JUnit tests, TestNG tests, jtreg tests, Caliper
   benchmarks or executable Java classes. It supports running
   fine-grained tests that can be specified with hash symbol, e.g.
   "com.android.Test#test".

 * Allows running tests and benchmarks using five available runtime
   modes: `activity`, `app_process`, `device`, `host` or `jvm`.

## Building and running

First build it:

* With a minimal `aosp/master-art` tree:
```bash
export SOONG_ALLOW_MISSING_DEPENDENCIES=true
${ANDROID_BUILD_TOP}/art/tools/buildbot-build.sh --target
```

* With a full Android (AOSP) `aosp/master` tree:
```bash
m vogar
```

## Features

Vogar supports running tests and/or benchmarks (called "actions" below in the document)
in five different modes (specified with `--mode` option). An "action" is a `.java` file,
directory or class names:

 1. Activity (`--mode=activity`)

    Vogar runs given action in the context of an [`android.app.Activity`](https://developer.android.com/reference/android/app/Activity) on a device.

 2. App (`--mode=app_process`)

    Vogar runs given action in an app_process runtime on a device or emulator.
    Used in conjunction with the `--benchmark` option for running Caliper benchmarks.
    This is required to benchmark any code relying on the android framework.

    ```bash
    Vogar --mode app_process --benchmark frameworks/base/core/tests/benchmarks/src/android/os/ParcelBenchmark.java
    ```

3. Device  (`--mode=device`)

   Vogar runs given action in an ART runtime on a device or emulator.

4. Host  (`--mode=host`)

   Vogar runs in an ART runtime on the local machine built with any lunch combo.
   Similar to "Device" mode but running local ART.

5. JVM  (`--mode=jvm`)

   Vogar runs given action in a Java VM on the local machine.

Most frequently you will use either `--mode=device` or `--mode=host` mode.

## Testing and debugging

Vogar has unit test coverage around basic functionality. Most of the coverage
is for [JUnit](https://junit.org/) and [TestNG](https://testng.org/) integration.

### Building and running

First, build tests with:
```bash
m vogar-tests
```

Run all tests using phony make target with:
```bash
m run-vogar-tests
```

Or run manually (if you want to specify a subset of all unit tests, for example):
```bash
java -cp ${ANDROID_BUILD_TOP}/out/host/linux-x86/framework/vogar-tests.jar \
      org.junit.runner.JUnitCore vogar.AllTests
```

## Architecture and implementation

High level model of each Vogar run is:

 1. Parsing input options.
 2. Creating a list of `Task` objects that encapsulate various steps required.
    These `Task` objects can depend on each other, and get executed only if all
    dependencies are already executed.
 3. Executing tasks. It include assembling the code, dexing it, packing in
    an activity/runnable dex jar, preparing the environment (host or device),
    pushing all artifacts, and running it.

### Classes overview

The basic building block of Vogar execution is the `Task` class. There are several
sub classes of `Task`, for example:

 * `MkdirTask`
 * `RmTask`
 * `PrepareTarget`

The `Target` class encapsulates the runtime environment, for example a
remote device or the local host. There are four available environments:

 * `AdbTarget` is used when `--mode=device` is set.

    It makes sure device is connected, required directories are mount
    properly, and all required files are synced to the device.

 * `AdbChrootTarget` is used when `--mode=device --chroot=/data/chroot/`
   are set.

   Same as `AdbTarget` but relatively to a specified chroot directory
   (instead of the whole system under the root directory on the device).

 * `LocalTarget` is used when `--mode=host` or `--mode=jvm` are set.

    Same as `AdbTarget` but runs on the host machine.

 * `SshTarget` is used when `--ssh <host:port>` is set.

    Same as `LocalTarget` but on a remote machine at the given address.

After parsing command line options, Vogar builds a list of tasks which
are put in a `TaskQueue`. They are executed using all available cores
except when "Activity" mode is enabled -- in that case it is always one
thread.

