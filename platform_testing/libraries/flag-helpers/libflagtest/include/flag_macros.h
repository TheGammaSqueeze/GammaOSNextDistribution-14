/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0dd
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <utility>
#include <sstream>
#include <string>
#include <vector>

#include <flag_checker.h>
#include <gtest/gtest.h>

#define _FLAG_GTEST_CLASS_NAME(test_fixture, test_name)                      \
  test_fixture##_##test_name##_FLAG_GTest

#define _FLAG_STRINGFY(...) #__VA_ARGS__

#define _FLAG_NAME(namespace, flag) namespace::flag

// Defines a class inherit from the original test fixture.
//
// The class is defined for each test case. The class constructor calls
// SkipIfFlagRequirementsNotMet to decide whether the test should be
// skipped.

#define _FLAG_GTEST_CLASS(test_fixture, test_name, parent_class, flags...)   \
  class _FLAG_GTEST_CLASS_NAME(test_fixture, test_name)                      \
                            : public parent_class {                          \
    public:                                                                  \
      void SkipTest(                                                         \
        std::vector<std::pair<bool, std::string>> unsatisfied_flags) {       \
        std::ostringstream skip_message;                                     \
        for (const std::pair<bool, std::string> flag : unsatisfied_flags) {  \
          skip_message << " flag("                                           \
              << flag.second << ")="                                         \
              << (flag.first ? "true":"false");                              \
        }                                                                    \
        GTEST_SKIP() << "Skipping test: not meet feature flag conditions:"   \
          << skip_message.str();                                             \
      }                                                                      \
                                                                             \
      _FLAG_GTEST_CLASS_NAME(test_fixture, test_name)() {                    \
        std::vector<std::pair<bool, std::string>> unsatisfied_flags =        \
          android::test::flag::GetFlagsNotMetRequirements({flags});          \
        if (unsatisfied_flags.size() != 0) {                                 \
          SkipTest(unsatisfied_flags);                                       \
        }                                                                    \
      }                                                                      \
  };

// Defines an aconfig feature flag.
//
// The first parameter is the package (with cpp namespace format) of the
// feature flag. The second parameter is the name of the feature flag.
//
// For example: ACONFIG_FLAG(android::cts::test, flag_rw)

#if !TEST_WITH_FLAGS_DONT_DEFINE
#define ACONFIG_FLAG(package, flag)                                            \
  std::make_pair<std::function<bool()>, std::string>(                          \
      static_cast<bool (*)()>(_FLAG_NAME(package, flag)),                      \
      _FLAG_STRINGFY(package, flag))
#endif

// Defines a legacy feature flag.
//
// The first parameter is the namespace of the feature flag. The second
// parameter (with cpp namespace format) is the package of the feature
// flag. The third parameter is the name of the feature flag.
//
// For example: LEGACY_FLAG(cts, android::cts::test, flag_rw)

#if !TEST_WITH_FLAGS_DONT_DEFINE
#define LEGACY_FLAG(namespace, package, flag)                               \
  std::make_pair<std::function<bool()>, std::string>(                       \
    nullptr, _FLAG_STRINGFY(namespace, package, flag))
#endif

// Defines a set of feature flags that must meet "enabled" condition.
//
// The input parameters of REQUIRES_FLAGS_ENABLED is a set of flags
// warpped by ACONFIG_FLAG or LEGACY_FLAG macros, indicating that the
// expected values of these flags are true.
//
// For example:
//   REQUIRES_FLAGS_ENABLED(LEGACY_FLAG(...), ACONFIG_FLAG(...))

#if !TEST_WITH_FLAGS_DONT_DEFINE
#define REQUIRES_FLAGS_ENABLED(flags...)                                    \
  std::make_pair<bool, std::vector<                                         \
    std::pair<std::function<bool()>, std::string>>>(true, {flags})
#endif

// Defines a set of feature flags that must meet "disabled" condition.
//
// The input parameters of REQUIRES_FLAGS_DISABLED is a set of flags
// warpped by ACONFIG_FLAG or LEGACY_FLAG macros, indicating that the
// expected values of these flags are false.
//
// For example:
//   REQUIRES_FLAGS_DISABLED(LEGACY_FLAG(...), ACONFIG_FLAG(...))

#if !TEST_WITH_FLAGS_DONT_DEFINE
#define REQUIRES_FLAGS_DISABLED(flags...)                                   \
  std::make_pair<bool, std::vector<                                         \
    std::pair<std::function<bool()>, std::string>>>(false, {flags})
#endif

// TEST_F_WITH_FLAGS is an extension to the TEST_F macro in the GoogleTest
// framework. It supports adding feature flag conditions wrapped by
// REQUIRES_FLAGS_ENABLED or REQUIRES_FLAGS_DISABLED macros at the end of
// input parameters.
//
// For example:
//
// TEST_F_WITH_FLAGS(
//   MyTestFixture,
//   myTest,
//   REQUIRES_FLAGS_ENABLED(...),
//   REQUIRES_FLAGS_DISABLED(...)) {...}
//
// If any feature flag condition cannot be satisfied, the test will be
// skipped.

#if !TEST_WITH_FLAGS_DONT_DEFINE
#define TEST_F_WITH_FLAGS(test_fixture, test_name, flags...)                 \
  _FLAG_GTEST_CLASS(test_fixture, test_name, test_fixture, flags)            \
  GTEST_TEST_(test_fixture, test_name,                                       \
              _FLAG_GTEST_CLASS_NAME(test_fixture, test_name),               \
              ::testing::internal::GetTypeId<test_fixture>())
#endif

// TEST_WITH_FLAGS is an extension to the TEST macro in the GoogleTest
// framework. It supports adding feature flag conditions wrapped by
// REQUIRES_FLAGS_ENABLED or REQUIRES_FLAGS_DISABLED macros at the end of
// input parameters.
//
// For example:
//
// TEST_WITH_FLAGS(
//   MyTestFixture,
//   myTest,
//   REQUIRES_FLAGS_ENABLED(...),
//   REQUIRES_FLAGS_DISABLED(...)) {...}
//
// If any feature flag condition cannot be satisfied, the test will be
// skipped.

#if !TEST_WITH_FLAGS_DONT_DEFINE
#define TEST_WITH_FLAGS(test_fixture, test_name, flags...)                   \
  _FLAG_GTEST_CLASS(test_fixture, test_name, ::testing::Test, flags)         \
  GTEST_TEST_(test_fixture, test_name,                                       \
              _FLAG_GTEST_CLASS_NAME(test_fixture, test_name),               \
              ::testing::internal::GetTestTypeId())
#endif