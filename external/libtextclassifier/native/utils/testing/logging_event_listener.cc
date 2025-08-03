/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/testing/logging_event_listener.h"

#include "utils/base/logging.h"

namespace libtextclassifier3 {

void LoggingEventListener::OnTestProgramStart(
    const testing::UnitTest& /* unit_test */) {}

void LoggingEventListener::OnTestIterationStart(
    const testing::UnitTest& unit_test, int iteration) {
  TC3_LOG(INFO) << "[==========] Running " << unit_test.test_to_run_count()
                << " test(s) from " << unit_test.test_case_to_run_count()
                << " test case(s)";
}

void LoggingEventListener::OnEnvironmentsSetUpStart(
    const testing::UnitTest& unit_test) {
  TC3_LOG(INFO) << "[----------] Global test environment set-up.";
}

void LoggingEventListener::OnEnvironmentsSetUpEnd(
    const testing::UnitTest& /* unit_test */) {}

void LoggingEventListener::OnTestCaseStart(const testing::TestCase& test_case) {
  std::string param_text;
  if (test_case.type_param()) {
    param_text.append(", where TypeParam = ").append(test_case.type_param());
  }
  TC3_LOG(INFO) << "[----------] " << test_case.test_to_run_count()
                << " test(s) from " << test_case.name() << param_text;
}

void LoggingEventListener::OnTestStart(const testing::TestInfo& test_info) {
  TC3_LOG(INFO) << "[ RUN      ] " << test_info.test_case_name() << "."
                << test_info.name();
}

void LoggingEventListener::OnTestPartResult(
    const testing::TestPartResult& test_part_result) {
  if (test_part_result.type() != testing::TestPartResult::kSuccess) {
    TC3_LOG(ERROR) << test_part_result.file_name() << ":"
                   << test_part_result.line_number() << ": Failure "
                   << test_part_result.message();
  }
}

void LoggingEventListener::OnTestEnd(const testing::TestInfo& test_info) {
  if (test_info.result()->Passed()) {
    TC3_LOG(INFO) << "[       OK ] " << test_info.test_case_name() << "."
                  << test_info.name();
  } else {
    TC3_LOG(ERROR) << "[  FAILED  ] " << test_info.test_case_name() << "."
                   << test_info.name();
  }
}

void LoggingEventListener::OnTestCaseEnd(const testing::TestCase& test_case) {
  TC3_LOG(INFO) << "[----------] " << test_case.test_to_run_count()
                << " test(s) from " << test_case.name() << " ("
                << test_case.elapsed_time() << " ms total)";
}

void LoggingEventListener::OnEnvironmentsTearDownStart(
    const testing::UnitTest& unit_test) {
  TC3_LOG(INFO) << "[----------] Global test environment tear-down.";
}

void LoggingEventListener::OnEnvironmentsTearDownEnd(
    const testing::UnitTest& /* unit_test */) {}

void LoggingEventListener::OnTestIterationEnd(
    const testing::UnitTest& unit_test, int iteration) {
  TC3_LOG(INFO) << "[==========] " << unit_test.test_to_run_count()
                << " test(s) from " << unit_test.test_case_to_run_count()
                << " test case(s) ran. (" << unit_test.elapsed_time()
                << " ms total)";
  TC3_LOG(INFO) << "[  PASSED  ] " << unit_test.successful_test_count()
                << " test(s)";
  if (!unit_test.Passed()) {
    TC3_LOG(ERROR) << "[  FAILED  ] " << unit_test.failed_test_count()
                   << " test(s), listed below:";
    for (int i = 0; i < unit_test.total_test_case_count(); ++i) {
      const testing::TestCase& test_case = *unit_test.GetTestCase(i);
      if (!test_case.should_run() || (test_case.failed_test_count() == 0)) {
        continue;
      }
      for (int j = 0; j < test_case.total_test_count(); ++j) {
        const testing::TestInfo& test_info = *test_case.GetTestInfo(j);
        if (!test_info.should_run() || test_info.result()->Passed()) {
          continue;
        }
        TC3_LOG(ERROR) << "[  FAILED  ] " << test_case.name() << "."
                       << test_info.name();
      }
    }
  }
}

void LoggingEventListener::OnTestProgramEnd(
    const testing::UnitTest& /* unit_test */) {}

}  // namespace libtextclassifier3
