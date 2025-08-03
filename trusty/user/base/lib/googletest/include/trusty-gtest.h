/*
 * Copyright (C) 2019 The Android Open Source Project
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

#pragma once

#include <gtest/gtest.h>
#include <lib/unittest/unittest.h>
#include <lk/compiler.h>
#include <trusty_unittest.h>

class TrustyLogTestResultPrinter : public testing::EmptyTestEventListener {
public:
    void OnTestPartResult(const testing::TestPartResult& result) override {
        const char* type_string = nullptr;
        switch (result.type()) {
        case testing::TestPartResult::kSuccess:
            return;
        case testing::TestPartResult::kNonFatalFailure:
        case testing::TestPartResult::kFatalFailure:
            type_string = "Error";
            break;
        case testing::TestPartResult::kSkip:
            type_string = "Skipped";
            break;
        }

        trusty_unittest_printf("  %s: %s:%d\n  %s\n", type_string,
                               result.file_name(), result.line_number(),
                               result.message());
    }

    void OnTestStart(const testing::TestInfo& test_info) override {
        if (!test_info.should_run()) {
            PrintTestName(test_info, "DISABLED");
            return;
        }

        PrintTestName(test_info, "RUN     ");
    }

    void OnTestEnd(const testing::TestInfo& test_info) override {
        if (!test_info.should_run()) {
            return;
        }

        const testing::TestResult* result = test_info.result();
        if (result->Passed()) {
            PrintTestName(test_info, "      OK");
        } else if (result->Failed()) {
            PrintTestName(test_info, " FAILED ");
        } else {
            assert(result->Skipped());
            PrintTestName(test_info, " SKIPPED");
        }
    }

    void OnTestProgramEnd(const testing::UnitTest& unit_test) override {
        trusty_unittest_printf("[==========] %d tests ran.\n",
                               unit_test.test_to_run_count());
        if (unit_test.successful_test_count() !=
            unit_test.test_to_run_count()) {
            trusty_unittest_printf("[  PASSED  ] %d tests.\n",
                                   unit_test.successful_test_count());
        }
        if (unit_test.disabled_test_count()) {
            trusty_unittest_printf("[ DISABLED ] %d tests.\n",
                                   unit_test.disabled_test_count());
        }
        if (unit_test.failed_test_count()) {
            trusty_unittest_printf("[  FAILED  ] %d tests.\n",
                                   unit_test.failed_test_count());
        }
    }

private:
    static void PrintTestName(const testing::TestInfo& test_info,
                              const char* state) {
        trusty_unittest_printf("[ %s ] %s.%s", state,
                               test_info.test_suite_name(), test_info.name());
        if (test_info.type_param() != nullptr) {
            trusty_unittest_printf("/%s", test_info.type_param());
        }
        trusty_unittest_printf("\n");
    }
};

#define PORT_GTEST(suite_name, port_name_string)              \
    __BEGIN_CDECLS                                            \
    static bool run_##suite_name(struct unittest* test) {     \
        return RUN_ALL_TESTS() == 0;                          \
    }                                                         \
                                                              \
    int main(int argc, char** argv) {                         \
        static struct unittest test = {                       \
                .port_name = port_name_string,                \
                .run_test = run_##suite_name,                 \
        };                                                    \
        struct unittest* tests = &test;                       \
        /* gtest requires argc > 1 */                         \
        int fake_argc = 1;                                    \
        char* fake_argv[] = {(char*)"test", NULL};            \
        testing::InitGoogleTest(&fake_argc, fake_argv);       \
        testing::UnitTest::GetInstance()->listeners().Append( \
                new TrustyLogTestResultPrinter);              \
        return unittest_main(&tests, 1);                      \
    }                                                         \
    __END_CDECLS
