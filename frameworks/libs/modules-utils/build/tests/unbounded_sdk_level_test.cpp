/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "android-modules-utils/sdk_level.h"
#include "android-modules-utils/unbounded_sdk_level.h"
#include <android-base/properties.h>
#include <gtest/gtest.h>

namespace android {
namespace modules {
namespace sdklevel {

namespace nostl {
bool IsAtLeast(const char *);
bool IsAtMost(const char *);
} // namespace nostl

namespace unbounded {

class UnboundedSdkLevelTest : public ::testing::Test {
protected:
  UnboundedSdkLevelTest() {
    device_codename_ =
        android::base::GetProperty("ro.build.version.codename", "");
    device_api_level_ = android_get_device_api_level();
  }

  std::string device_codename_;
  int device_api_level_;
};

using UnboundedSdkLevelDeathTest = UnboundedSdkLevelTest;

TEST_F(UnboundedSdkLevelTest, IntegerVersionsTest) {
  EXPECT_TRUE(IsAtLeast("1"));
  EXPECT_TRUE(IsAtLeast("31"));
  EXPECT_TRUE(IsAtLeast(std::to_string(device_api_level_).c_str()));

  EXPECT_FALSE(IsAtLeast(std::to_string(device_api_level_ + 1).c_str()));
  EXPECT_FALSE(IsAtLeast(std::to_string(__ANDROID_API_FUTURE__).c_str()));

  EXPECT_FALSE(IsAtMost("1"));
  EXPECT_FALSE(IsAtMost("30"));
  if ("REL" == device_codename_) {
    EXPECT_TRUE(IsAtMost(std::to_string(device_api_level_).c_str()));
  } else {
    EXPECT_FALSE(IsAtMost(std::to_string(device_api_level_).c_str()));
  }

  EXPECT_TRUE(IsAtMost(std::to_string(device_api_level_ + 1).c_str()));
  EXPECT_TRUE(IsAtMost(std::to_string(__ANDROID_API_FUTURE__).c_str()));
}

TEST_F(UnboundedSdkLevelTest, CodenameVersionsTest) {
  if ("REL" == device_codename_) {
    EXPECT_FALSE(IsAtLeast("Aaa"));
    EXPECT_TRUE(IsAtMost("Aaa"));
  } else {
    EXPECT_TRUE(IsAtLeast("R"));
    EXPECT_TRUE(IsAtLeast("S"));
    EXPECT_TRUE(IsAtLeast("Sv2"));
    EXPECT_TRUE(IsAtLeast("Tiramisu"));

    EXPECT_FALSE(IsAtLeast("Aaa"));
    EXPECT_FALSE(IsAtLeast("Zzz"));

    EXPECT_FALSE(IsAtMost("R"));
    EXPECT_FALSE(IsAtMost("S"));
    EXPECT_FALSE(IsAtMost("Sv2"));
    EXPECT_TRUE(IsAtMost("Tiramisu"));

    EXPECT_TRUE(IsAtMost("Aaa"));
    EXPECT_TRUE(IsAtMost("Zzz"));
  }
}

TEST_F(UnboundedSdkLevelTest, NoStlTest) {
  EXPECT_TRUE(android::modules::sdklevel::nostl::IsAtLeast(
      std::to_string(device_api_level_).c_str()));
  EXPECT_TRUE(android::modules::sdklevel::nostl::IsAtMost(
      std::to_string(device_api_level_ + 1).c_str()));
}

TEST_F(UnboundedSdkLevelDeathTest, IsAtLeast_EmptyVersionDeathTest) {
  EXPECT_DEATH(IsAtLeast(""), "");
}

TEST_F(UnboundedSdkLevelDeathTest, IsAtMost_EmptyVersionDeathTest) {
  EXPECT_DEATH(IsAtMost(""), "");
}

TEST_F(UnboundedSdkLevelDeathTest, IsAtLeast_CurrentVersionDeathTest) {
  EXPECT_DEATH(IsAtLeast("current"), "");
}

TEST_F(UnboundedSdkLevelDeathTest, IsAtMost_CurrentVersionDeathTest) {
  EXPECT_DEATH(IsAtMost("current"), "");
}

TEST_F(UnboundedSdkLevelDeathTest, IsAtLeast_ReleaseVersionDeathTest) {
  if ("REL" == device_codename_) {
    EXPECT_DEATH(IsAtLeast("Q"), "");
  }
}

TEST_F(UnboundedSdkLevelDeathTest, IsAtMost_ReleaseVersionDeathTest) {
  if ("REL" == device_codename_) {
    EXPECT_DEATH(IsAtMost("Q"), "");
  }
}

} // namespace unbounded
} // namespace sdklevel
} // namespace modules
} // namespace android
