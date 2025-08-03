/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */
#pragma once

#include <android-base/format.h>
#include <android-base/logging.h>
#include "gtest/gtest.h"

using ::testing::TestInfo;
using ::testing::UnitTest;

#define DBG 1

/*
 * Test base class for net native tests to support common usage.
 */
class NetNativeTestBase : public ::testing::Test {
  public:
    // TODO: update the logging when gtest supports logging the life cycle on each test.
    NetNativeTestBase() {
        if (DBG) LOG(INFO) << getTestCaseLog(true);
    }
    ~NetNativeTestBase() {
        if (DBG) LOG(INFO) << getTestCaseLog(false);
    }

    std::string getTestCaseLog(bool running) {
        const TestInfo* const test_info = UnitTest::GetInstance()->current_test_info();
        return fmt::format("{}: {}#{}", (running ? "started" : "finished"),
                           test_info->test_suite_name(), test_info->name());
    }
};
