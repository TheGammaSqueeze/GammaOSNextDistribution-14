// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_TESTING_LOGGING_EVENT_LISTENER_H_
#define ICING_TESTING_LOGGING_EVENT_LISTENER_H_

#include "gtest/gtest.h"

namespace icing {
namespace lib {

// TestEventListener that writes test results to the log so that they will be
// visible in the logcat output in Sponge.
// The formatting of the output is patterned after the output produced by the
// standard PrettyUnitTestResultPrinter.
class LoggingEventListener : public ::testing::TestEventListener {
 public:
  void OnTestProgramStart(const testing::UnitTest& unit_test) override;

  void OnTestIterationStart(const testing::UnitTest& unit_test,
                            int iteration) override;

  void OnEnvironmentsSetUpStart(const testing::UnitTest& unit_test) override;

  void OnEnvironmentsSetUpEnd(const testing::UnitTest& unit_test) override;

  void OnTestCaseStart(const testing::TestCase& test_case) override;

  void OnTestStart(const testing::TestInfo& test_info) override;

  void OnTestPartResult(
      const testing::TestPartResult& test_part_result) override;

  void OnTestEnd(const testing::TestInfo& test_info) override;

  void OnTestCaseEnd(const testing::TestCase& test_case) override;

  void OnEnvironmentsTearDownStart(const testing::UnitTest& unit_test) override;

  void OnEnvironmentsTearDownEnd(const testing::UnitTest& unit_test) override;

  void OnTestIterationEnd(const testing::UnitTest& unit_test,
                          int iteration) override;

  void OnTestProgramEnd(const testing::UnitTest& unit_test) override;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_LOGGING_EVENT_LISTENER_H_
