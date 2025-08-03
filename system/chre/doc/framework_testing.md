# Testing the CHRE Framework

[TOC]

The CHRE framework can be tested at various levels to ensure that
components/modules components are working correctly and API contracts are being
met. Below are some examples of what the team currently does to verify new
changes.

## Unit tests

### Tests run on a host machine

Currently, unit tests exist for various core components and utilities capable
of running on a Linux host machine. Since
platform-specific components likely aren’t compilable/available on a host
machine, only components that are OS independent can be tested via this path.

In order to write new tests, add a test source file under the test directory in
the appropriate subdirectory. e.g. `util/tests`. Then, add the file to the
`GOOGLETEST_SRCS` variable in the appropriate .mk file for that subdir,
`util/util.mk` for example.

Unit tests can be built and executed using `run_tests.sh`.


### On-device unit tests

#### Background

The framework aims to provide an environment to test CHRE (and its users) code
on-device, using [Pigweed's][PW_URL] Unit Test [Framework][PW_UT_URL]. Test
instantiations are syntactically identical to [Googletest][GT_URL], so
modifications to on-host unit tests to run on-device are easier.

CHRE recommends running the same host-side gtests on-device using this
framework, to catch subtle bugs. For example, the target CPU may raise an
exception on unaligned access, when the same code would run without any
problems on a local x86-based machine.

#### Use Cases

Example use cases of the framework include:

* In continuous integration frameworks with device farms
* As a superior alternative to logging and/or flag based debugging to quickly test a feature
* As a modular set of tests to test feature or peripheral functioning (eg: a system timer implementation) during device bringup.

###### Note

One key difference is to run the tests via a call to `chre::runAllTests` in
_chre/test/common/unit_test.h_, which basically wraps the gtest `RUN_ALL_TESTS`
macro, and implements CHRE specific event handlers for Pigweed's UT Framework.

#### Running Tests

Under the current incarnation of the CHRE Unit Test Framework, the following
steps need to be taken to run the on-device tests:
* Set to true and export an environment variable called `CHRE_ON_DEVICE_TESTS_ENABLED`
from your Makefile invocation before CHRE is built.
  * Ensure that this flag is not always set to avoid codesize bloat.
* Append your test source file to `COMMON_SRCS` either in _test/test.mk_ or in
your own Makefile.
* Call `chre::runAllTests` from somewhere in your code.

##### Sample code

In _math_test.cc_
```cpp
#include <gtest/gtest.h>

TEST(MyMath, Add) {
  int x = 1, y = 2;
  int result = myAdd(x, y);
  EXPECT_EQ(result, 3);
}
```

In _some_source.cc_
```cpp
#include "chre/test/common/unit_test.h"

void utEntryFunc() {
  chre::runAllTests();
}
```

#### Caveats

Some advanced features of gtest (SCOPED_TRACE, etc.) are unsupported by Pigweed.

#### Compatibility

The framework has been tested with Pigweed's git revision ee460238b8a7ec0a6b4f61fe7e67a12231db6d3e.

## PAL implementation tests

PAL implementation tests verify implementations of PAL interfaces adhere to the
requirements of that interface, and are intended primarily to support
development of PAL implementations, typically done by a chip vendor partner.
Additional setup may be required to integrate with the PAL under test and supply
necessary dependencies. The source code is in the files under `pal/tests/src`
and follows the naming scheme `*_pal_impl_test.cc`.

In order to run PAL tests, run: `run_pal_impl_tests.sh`. Note that there are
also PAL unit tests in the same directory. The unit tests are added to the
`GOOGLETEST_SRCS` target while PAL tests are added to the
`GOOGLETEST_PAL_IMPL_SRCS` target.

## FeatureWorld nanoapps

Located under the `apps/` directory, FeatureWorld nanoapps interact with the set
of CHRE APIs that they are named after, and can be useful during framework
development for manual verification of a feature area. For example, SensorWorld
attempts to samples from sensors and outputs to the log. It also offers a
break-it mode that randomly changes which sensors are being sampled in an
attempt to point out stress points in the framework or platform implementation.

These apps are usually built into the CHRE framework binary as static nanoapps
to facilitate easy development. See the Deploying Nanoapps section for more
information on static nanoapps.

## CHQTS

The Context Hub Qualification Test Suite (CHQTS) tests perform end-to-end
validation of a CHRE implementation, by using the Java APIs in Android to load
and interact with test nanoapps which then exercise the CHRE API. While this
code is nominally integrated in another test suite, the source code is available
under `java/test/chqts/` for the Java side code and `apps/test/chqts/` for the
CHQTS-only nanoapp code and `apps/test/common/` for the nanoapp code shared by
CHQTS and other test suites.

[PW_URL]: https://pigweed.dev
[PW_UT_URL]: https://pigweed.googlesource.com/pigweed/pigweed/+/refs/heads/master/pw_unit_test
[GT_URL]: https://github.com/google/googletest
