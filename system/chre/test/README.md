### CHRE Unit Test Framework

#### Background

The framework aims to provide an environment to test CHRE (and its users) code
on-device, using [Pigweed's][PW_URL] Unit Test [Framework][PW_UT_URL]. Test
instantiations are syntactically identical to [Googletest][GT_URL], so
modifications to on-host unit tests to run on-device are easier.

CHRE recommends running the same host-side gtests on-device using this
framework, to catch subtle bugs. For example, the target CPU may raise an
exception on unaligned access, when the same code would run without any
problems on a local x86-based machine.

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

The framework has been tested with Pigweed's git revision ee460238b8a7ec0a6b4f61fe7e67a12231db6d3e

[PW_URL]: https://pigweed.dev
[PW_UT_URL]: https://pigweed.googlesource.com/pigweed/pigweed/+/refs/heads/master/pw_unit_test
[GT_URL]: https://github.com/google/googletest
