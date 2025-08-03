/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "chre/platform/log.h"
#include "chre/test/common/unit_test.h"
#include "chre/util/macros.h"
#include "gtest/gtest.h"

namespace chre {
namespace {

using pw::unit_test::EventHandler;
using pw::unit_test::RunTestsSummary;
using pw::unit_test::TestCase;
using pw::unit_test::TestExpectation;
using pw::unit_test::TestResult;

class PwEventHandler : public EventHandler {
  void RunAllTestsStart() override {
    LOGD("[==========] Running all tests.");
  }

  void RunAllTestsEnd(const RunTestsSummary &run_tests_summary) override {
    LOGD("[==========] Done running all tests.");
    LOGD("[  PASSED  ] %d test(s).", run_tests_summary.passed_tests);
    if (run_tests_summary.failed_tests) {
      LOGD("[  FAILED  ] %d test(s).", run_tests_summary.failed_tests);
    }
  }

  void TestCaseStart(const TestCase &test_case) override {
    LOGD("[ RUN      ] %s.%s", test_case.suite_name, test_case.test_name);
  }

  void TestCaseEnd(const TestCase &test_case, TestResult result) override {
    switch (result) {
      case TestResult::kSuccess:
        LOGD("[       OK ] %s.%s", test_case.suite_name, test_case.test_name);
        break;
      case TestResult::kFailure:
        LOGD("[  FAILED  ] %s.%s", test_case.suite_name, test_case.test_name);
        break;
    }
  }

  void TestCaseExpect(const TestCase &test_case,
                      const TestExpectation &expectation) override {
    if (!expectation.success) {
      LOGD("%s:%d: Failure", test_case.file_name, expectation.line_number);
      LOGD("      Expected: %s", expectation.expression);
      LOGD("        Actual: %s", expectation.evaluated_expression);
    }
  }

  void TestCaseDisabled(const TestCase &test_case) override {
    LOGD("Skipping disabled test %s.%s", test_case.suite_name,
         test_case.test_name);
  }
};

}  // namespace

DLL_EXPORT void runAllTests() {
  PwEventHandler eventHandler;
  pw::unit_test::RegisterEventHandler(&eventHandler);
  int rc = RUN_ALL_TESTS();
  LOGD("Ran all tests with rc: %d", rc);
}

}  // namespace chre
