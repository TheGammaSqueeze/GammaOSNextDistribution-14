/**
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

#include "gtest/gtest.h"
#include <android-base/file.h>
#include <android-base/properties.h>
#include "bpf/BpfUtils.h"

class VtsBootconfigTest : public testing::Test {};

TEST_F(VtsBootconfigTest, ProcCmdlineAndroidbootTest) {
  // This test only applies to devices launching with S(or greater) AND with
  // kernel version 5.10(or greater)
  bool kernel_support = android::bpf::isAtLeastKernelVersion(5, 10, 0);
  if (std::stoi(android::base::GetProperty("ro.product.first_api_level", "0"))
    < __ANDROID_API_S__ || !kernel_support) {
    GTEST_SKIP() << "Bootconfig requirements do not apply";
  }

  std::string cmdline;
  ASSERT_TRUE(android::base::ReadFileToString("/proc/cmdline", &cmdline));
  EXPECT_TRUE(cmdline.size() > 0);
  EXPECT_EQ(cmdline.find("androidboot"), cmdline.npos)
    << "\"androidboot\" parameters are not allowed in the kernel cmdline for "
    << "devices using kernel version 5.10 or greater with Android S and beyond. "
    << "These parameters are to be placed in bootconfig."
    << "\n/proc/cmdline contents:\n" << cmdline;
}
