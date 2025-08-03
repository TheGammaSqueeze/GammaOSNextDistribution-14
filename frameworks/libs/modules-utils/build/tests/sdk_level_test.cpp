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
 */

#include <android-base/properties.h>
#include <gtest/gtest.h>

namespace android {
namespace modules {
namespace sdklevel {

namespace nostl {
bool IsAtLeastR();
bool IsAtLeastS();
bool IsAtLeastT();
} // namespace nostl

class SdkLevelTest : public ::testing::Test {
protected:
  SdkLevelTest() {
    device_codename_ =
        android::base::GetProperty("ro.build.version.codename", "");
  }
  std::string device_codename_;
};

TEST_F(SdkLevelTest, NoStlTest) {
  if ("REL" == device_codename_) {
    GTEST_SKIP();
  }

  EXPECT_TRUE(nostl::IsAtLeastR());
  EXPECT_TRUE(nostl::IsAtLeastS());
  EXPECT_TRUE(nostl::IsAtLeastT());
}

} // namespace sdklevel
} // namespace modules
} // namespace android
