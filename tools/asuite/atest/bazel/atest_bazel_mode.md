# Atest Bazel Mode
Atest is a command line tool that allows users to run Android tests locally
without requiring knowledge of Trade Federation test harness command line
options. It wraps the logic and calls Trade Federation under the hood. This is
what we call Atest Standard Mode in this document.

Atest Bazel Mode creates a synthetic Bazel workspace and executes tests using
Bazel instead of calling Trade Federation directly. This mode opens up Bazel
features such as parallelized execution, caching, and remote execution.
Currently it is able to run all host unit tests only. Capability to run tests
that requires a device is still work in progress.

##### Table of Contents
1. [Basic Usage](#basic-usage)
2. [Advanced Usage](#advanced-usage)
3. [How It Works](#how-it-works)
4. [Difference from Atest Standard Mode](#difference-from-atest-standard-mode)
5. [Frequently Asked Questions](#faq)

## <a name="basic-usage">Basic Usage</a>

Atest Bazel Mode commands take the following form:

>```$ atest --bazel-mode --host HelloWorldHostTest```
<p>Note: "--host" is needed to run the test completely on the host without a device.

To run multiple tests, separate test references with spaces. For example:

>```$ atest --bazel-mode --host HelloWorldHostTest fastdeploy_test aapt2_tests```

To run all host unit tests from the current directory:

>```$ atest --bazel-mode --host --host-unit-test-only```

## <a name="advanced-usage">Advanced Usage</a>

Use `--bazel-arg` to forward arguments to Bazel. For example, the following
command increases the test timeout:

>```$ atest --bazel-mode --host CtsNNAPITestCases --bazel-arg=--test_timeout=600```

## <a name="how-it-works">How It Works</a>
Bazel needs a Bazel workspace to execute tests.
In Atest Bazel Mode, we construct a synthetic workspace using module-info.json.
The workspace contains required directory structure, symlinks and Bazel BUILD
files to correctly invoke ```bazel test``` command. The Bazel BUILD files are
written with customized Bazel rules. An example Build file is as follows:

```
package(default_visibility = ["//visibility:public"])

load("//bazel/rules:soong_prebuilt.bzl", "soong_prebuilt")
load("//bazel/rules:tradefed_test.bzl", "tradefed_deviceless_test")

tradefed_deviceless_test(
    name = "HelloWorldHostTest_host",
    test = "//platform_testing/tests/example/jarhosttest:HelloWorldHostTest",
)

soong_prebuilt(
    name = "HelloWorldHostTest",
    module_name = "HelloWorldHostTest",
    files = select({
        "//bazel/rules:host": glob(["HelloWorldHostTest/host/**/*"]),
    }),
)
```

Atest bazel Mode will create the Bazel workspace on first run, or upon detecting
a change to module-info.json.

It will then use Bazel query to find out dependencies for the build step.

In the build step, it will use Soong to build those dependencies returned by
Bazel query.

At last, ```bazel test``` command is executed for the test targets.

## <a name="difference-from-atest-standard-mode">Difference from Atest Standard Mode</a>

Here is a list of major differences from the Atest Standard Mode:
* In Atest Standard Mode, user can view detailed test case result in the
terminal, while in Bazel Mode only test target result is showing. For test case
detail, user would need to look at test logs. The reason Bazel Mode only shows
the summary result is that atest invokes Bazel command with default parameters.
Bazel command option "--test_output" is defaulted to be "summary". User has the
option to view "all" output when we later implement command option passing from
Atest to Bazel.
More details about Bazel [--test_output flag](https://docs.bazel.build/versions/main/command-line-reference.html#flag--test_output)
* In Atest Standard Mode, user can identify tests by module name, class name,
file path or package name, while in Bazel Mode, we only support module name
currently. Supporting flexible test finder is work in progress.
* In Atest Standard Mode, test logs are saved under ```/tmp/atest_result```, while in
Bazel Mode, test logs are saved under ```$ANDROID_BUILD_TOP/out/atest_bazel_workspace/bazel-testlogs```


## <a name="faq">Frequently Asked Questions</a>

### 1. Why my test failed with "error: Read-only file system" in the test log?

Bazel execution is done within a sandbox. The purpose is to create a hermetic
environment for the test. This could sometimes cause issues if the test writer
is not careful when reading and writting test data.

For reading, there is not much restriction as the new Bazel sandbox design
allows all read access to "/" since it mounted "/" as readable in the sandbox.

For writting, Bazel only allows write access to the target's private execroot
directory and a private $TMPDIR.

More details about [Bazel sandbox.](https://bazel.build/designs/2016/06/02/sandboxing.html)


### 2. Why I got "Too many levels of symbolic links" while reading files in Bazel Mode?

Some tests try to read the test data using relative path. This some times does
not work in Bazel Mode.

In Bazel Mode, Bazel creates symbolic links for all the test artifacts in the
Bazel private execution root directory in the sandbox. The symbolic links are
eventially resolved to the physical file in Android source tree.
Reading the symlink as file without following symlinks may fail with the above
error message.

One example is C++ android::base::ReadFileToString function. The solution is to
enable following symbolic link when calling the function.
More details can be find [here.](https://cs.android.com/android/platform/superproject/+/master:external/googletest/googletest/include/gtest/gtest.h;drc=master;l=2353)
