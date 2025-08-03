/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <flag_macros.h>
#include <gtest/gtest.h>
#include "android_cts_flags_tests.h"

#define TEST_NS android::cts::flags::tests

class TestFWithFlagsTest : public ::testing::Test {
public:
    static std::set<std::string> executed_tests;
protected:
    void TestFail() {
        FAIL();
    }
};

std::set<std::string> TestFWithFlagsTest::executed_tests = {};

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    requies_disabled_flag_enabled_skip,
    REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(TEST_NS, readwrite_enabled_flag))
) {
    TestFail();
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    requies_disabled_flag_disabled_execute,
    REQUIRES_FLAGS_DISABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag))
) {
    executed_tests.insert("requies_disabled_flag_disabled_execute");
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    requies_enabled_flag_disabled_skip,
    REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag))
) {
    TestFail();
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    requies_enabled_flag_enabled_executed,
    REQUIRES_FLAGS_ENABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_enabled_flag))
) {
    executed_tests.insert("requies_enabled_flag_enabled_executed");
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    multi_flags_skip,
    REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag)),
    REQUIRES_FLAGS_DISABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_enabled_flag))
) {
    TestFail();
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    multi_flags_executed,
    REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_NS, readwrite_enabled_flag)),
    REQUIRES_FLAGS_DISABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag))
) {
    executed_tests.insert("multi_flags_executed");
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    multi_flags_for_same_state_skip,
    REQUIRES_FLAGS_ENABLED(
        ACONFIG_FLAG(TEST_NS, readwrite_enabled_flag),
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag)
    )
) {
    TestFail();
}

TEST_F_WITH_FLAGS(
    TestFWithFlagsTest,
    multi_flags_for_same_state_executed,
    REQUIRES_FLAGS_DISABLED(
        ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag),
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag_2)
    )
) {
    executed_tests.insert("multi_flags_for_same_state_executed");
}

TEST_F(TestFWithFlagsTest, check_n_executed_tests) {
    std::set<std::string> expected_executed_tests = {
        "requies_disabled_flag_disabled_execute",
        "requies_enabled_flag_enabled_executed",
        "multi_flags_executed",
        "multi_flags_for_same_state_executed",
    };
    ASSERT_EQ(expected_executed_tests, executed_tests);
}

class TestWithFlagsTestHelper {
public:
    static std::set<std::string> executed_tests;
};

std::set<std::string> TestWithFlagsTestHelper::executed_tests = {};

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    requies_disabled_flag_enabled_skip,
    REQUIRES_FLAGS_DISABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_enabled_flag))
) {
    FAIL();
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    requies_disabled_flag_disabled_execute,
    REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag))
) {
    TestWithFlagsTestHelper::executed_tests.insert(
        "requies_disabled_flag_disabled_execute");
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    requies_enabled_flag_disabled_skip,
    REQUIRES_FLAGS_ENABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag))
) {
    FAIL();
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    requies_enabled_flag_enabled_executed,
    REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_NS, readwrite_enabled_flag))
) {
    TestWithFlagsTestHelper::executed_tests.insert(
        "requies_enabled_flag_enabled_executed");
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    multi_flags_skip,
    REQUIRES_FLAGS_ENABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag)),
    REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(TEST_NS, readwrite_enabled_flag))
) {
    FAIL();
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    multi_flags_executed,
    REQUIRES_FLAGS_ENABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_enabled_flag)),
    REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag))
) {
    TestWithFlagsTestHelper::executed_tests.insert(
        "multi_flags_executed");
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    multi_flags_for_same_state_skip,
    REQUIRES_FLAGS_ENABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_enabled_flag),
        ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag)
    )
) {
    FAIL();
}

TEST_WITH_FLAGS(
    TestWithFlagsTest,
    multi_flags_for_same_state_executed,
    REQUIRES_FLAGS_DISABLED(
        LEGACY_FLAG(aconfig_flags.cts, TEST_NS, readwrite_disabled_flag),
        ACONFIG_FLAG(TEST_NS, readwrite_disabled_flag_2)
    )
) {
    TestWithFlagsTestHelper::executed_tests.insert(
        "multi_flags_for_same_state_executed");
}

TEST(TestWithFlagsTest, check_n_executed_tests) {
    std::set<std::string> expected_executed_tests = {
        "requies_disabled_flag_disabled_execute",
        "requies_enabled_flag_enabled_executed",
        "multi_flags_executed",
        "multi_flags_for_same_state_executed",
    };
    ASSERT_EQ(
        expected_executed_tests,
        TestWithFlagsTestHelper::executed_tests);
}

int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
