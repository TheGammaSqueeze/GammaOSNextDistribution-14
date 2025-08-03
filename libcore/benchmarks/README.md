
# Run Caliper benchmark tests using vogar on a rooted device

- It uses the [Caliper library](https://github.com/google/caliper) developed by Google.
- Vogar source codes can be found at `external/vogar`.

1. Preparation

```shell
# vogar requires com.android.art.testing
m vogar com.android.art.testing
# remount if you haven't done so.
adb root && adb remount && adb reboot && adb wait-for-device root
cd libcore/benchmarks/src
```

Extra options to reduce noise:
```shell
adb shell stop # to kill frameworks and zygote
```

2. Run an individual test

```shell
vogar --benchmark benchmarks/regression/ScannerBenchmark.java
```

The source code of the tests can be found at `src/benchmarks/`

# Run Jetpack benchmark tests
Docs about Jetpack Benchmark can be found at
[https://developer.android.com/studio/profile/benchmarking-overview]()

1. Preparation

To lock CPU clocks on a rooted device,
run the script provided at [https://developer.android.com/studio/profile/run-benchmarks-in-ci#clock-locking]().

2. Run an individual test
```shell
atest LibcoreBenchmarkTests:libcore.benchmark.FormatterTest#stringFormatNumber_allLocales
```

The source code of the tests can be found at `src_androidx/libcore/benchmark/`

## Outdated documentation / Not working

###VM Options


The VM's configuration will have a substantial impact on performance.
Use Caliper's -J<name> <value 1>,<value 2>,<value 3> syntax to compare different VM options. For example:
```shell
vogar --benchmark ~/svn/dalvik/benchmarks/regression/CrespoFileIoRegressionBenchmark.java \
-- -Jgc -Xgc:noconcurrent,-Xgc:concurrent -Jint -Xint:fast,-Xint:jit,-Xint:portable
```

