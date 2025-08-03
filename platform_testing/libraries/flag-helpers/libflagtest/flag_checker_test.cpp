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

#include <string>
#include <functional>

#include <flag_checker.h>
#include <gtest/gtest.h>

#include "android_test_myflags.h"


using android::test::flag::CheckFlagCondition;
using android::test::flag::GetFlagsNotMetRequirements;

class CheckFlagConditionTest : public ::testing::Test {};


TEST_F(CheckFlagConditionTest, invalid_lagency_flag) {
  ASSERT_FALSE(
    CheckFlagCondition(
      true,
      {nullptr, "flagtest, android::test::myflags"}
    )
  );
}

TEST_F(CheckFlagConditionTest, lagency_flag_not_meet_condition) {
  ASSERT_FALSE(
    CheckFlagCondition(
      true,
      {nullptr, "flagtest, android::test::myflags, test_flag"}
    )
  );
}

TEST_F(CheckFlagConditionTest, lagency_flag_meet_true_condition) {
  ASSERT_TRUE(
    CheckFlagCondition(
      true,
      {nullptr, "flagtest, android::test::myflags, test_flag_true"}
    )
  );
}

TEST_F(CheckFlagConditionTest, lagency_flag_meet_false_condition) {
  ASSERT_TRUE(
    CheckFlagCondition(
      false,
      {nullptr, "flagtest, android::test::myflags, test_flag_false"}
    )
  );
}

TEST_F(CheckFlagConditionTest, aconfig_flag_not_meet_condition) {
  ASSERT_FALSE(
    CheckFlagCondition(
      false,
      {
        android::test::myflags::test_flag_true,
        "android::test::myflags, test_flag_true"
      }
    )
  );
}

TEST_F(CheckFlagConditionTest, aconfig_flag_meet_true_condition) {
  ASSERT_TRUE(
    CheckFlagCondition(
      true,
      {
        android::test::myflags::test_flag_true,
        "android::test::myflags, test_flag_true"
      }
    )
  );
}

TEST_F(CheckFlagConditionTest, aconfig_flag_meet_false_condition) {
  ASSERT_TRUE(
    CheckFlagCondition(
      false,
      {
        android::test::myflags::test_flag_false,
        "android::test::myflags, test_flag_false"
      }
    )
  );
}

class GetFlagsNotMetReqTest : public ::testing::Test {};

TEST_F(GetFlagsNotMetReqTest, empty_flags) {
  ASSERT_EQ(GetFlagsNotMetRequirements({}).size(), 0);
}

TEST_F(GetFlagsNotMetReqTest, flag_meet_condition) {
  auto unsatisfied_flags =
    GetFlagsNotMetRequirements({
      {
        true, {
          {
            android::test::myflags::test_flag_true,
            "android::test::myflags, test_flag_true"
          }
        }
      },
      {
        false, {
          {
            nullptr,
            "flagtest, android::test::myflags, test_flag_false"
          }
        }
      },
    });
  ASSERT_EQ(unsatisfied_flags.size(), 0);
}

TEST_F(GetFlagsNotMetReqTest, flag_not_meet_condition) {
  auto unsatisfied_flags =
    GetFlagsNotMetRequirements({
      {
        false, {
          {
            android::test::myflags::test_flag_true,
            "android::test::myflags, test_flag_true"
          }
        }
      },
      {
        true, {
          {
            nullptr,
            "flagtest, android::test::myflags, test_flag_false"
          }
        }
      },
    });
  ASSERT_EQ(unsatisfied_flags.size(), 2);
  ASSERT_FALSE(unsatisfied_flags[0].first);
  ASSERT_EQ(
    unsatisfied_flags[0].second,
    "android::test::myflags, test_flag_true"
  );
  ASSERT_TRUE(unsatisfied_flags[1].first);
  ASSERT_EQ(
    unsatisfied_flags[1].second,
    "flagtest, android::test::myflags, test_flag_false"
  );
}
