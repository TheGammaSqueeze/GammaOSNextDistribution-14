Copyright 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
------------------------------------------------------------------

This directory contains files for the Android MLTS (Machine Learning
Test Suite). MLTS allows to evaluate NNAPI acceleration latency and accuracy
on an Android device, using few selected ML models and datesets.

Models and datasets used description and licensing can be found in
platform/test/mlts/models/README.txt file.

Usage:
* Connect a target device to your workstation, make sure it's
reachable through adb. Export target device ANDROID_SERIAL
environment variable if more than one device is connected.
* cd into android top-level source directory
> source build/envsetup.sh
> lunch aosp_arm-userdebug # Or aosp_arm64-userdebug if available.
> ./test/mlts/benchmark/build_and_run_benchmark.sh
* At the end of a benchmark run, its results will be
presented as html page, passed to xdg-open.

# Crash test

The MLTS suite contains a series of tests to validate the behaviour of the drivers under stress or
in corner case conditions.

To run the tests use the specific targets available in the build_and_run_benchmark.sh script.
By default, every test gets run on each available accelerator in isolation. It is possible to filter the
accelerators to test against by invoking the build_and_run_benchmark.sh script with the option
-f (--filter-driver) and specifying a regular expression to filter the acccelerator names with.
It is also possible to run additional tests without specified target accelerator to let NNAPI
partition the model and assign the best available  one(s) by using the
-r (--include-nnapi-reference) option.

Currently available tests are:

* parallel-inference-stress: to test the behaviour of drivers with different amount of inference
executed in parallel. Tests are running in a separate process so crashes can be detected and
notified as test failures.

* parallel-inference-stress-in-process: same as parallel-inference-stress but the tests are running
in the same process of the test so in case of crash the testing app will crash too

* client-early-termination-stress: to test the resilience of device drivers to failing clients.
It spawns a separate process each running a set of parallel threads compiling different models.
The process is then forcibly terminated. The test validates that the targeted driver is not
crashing or hanging

* multi-process-inference-stress: this extends the `parallel-inference-stress` running inference
on a single model in multiple processes and threads with different probabilities in client process
early termination

* multi-process-model-load-stress: this extends the `parallel-inference-stress` running inference
on a single model in multiple processes and threads with different probabilities in client process
early termination

* memory-mapped-model-load-stress: runs a series of parallel model compilation with memory mapped
TFLite models

* model-load-random-stress: test compiling a large set of randomly generated models

* inference-random-stress: test running a large set of randomly generated models

* performance-degradation-stress: verifies that accelerator inference speed is not degrading over
a certain threshold when running concurrent workload

# Testing a NNAPI Support Library implementation

All tests documented above can be run using a NNAPI Support Library implementation.
To do so you need to:

- copy all the shared objects part of the libraries under the `sl_prebuilt`
  folder
- Use the `-s` or `--use-nnapi-sl` option when running `build_and_run_benchmark.sh`.

By default the system will use the sl_prebuilt/Android.bp.template to map
every library under sl_prebuilt to a native library to include in the APK.
The file is already configured for the Qualcomm NNAPI SL binaries.
If you have different libraries that the ones defined under sl_prebuilt/Android.bp.template
you should

- configure a sl_prebuilt/Android.bp with the list of binaries you added.
  You can use the sl_prebuilt/Android.bp.template file as an example template.

- Set in in Android.mk the SL_LIBS variable with the list of drivers
