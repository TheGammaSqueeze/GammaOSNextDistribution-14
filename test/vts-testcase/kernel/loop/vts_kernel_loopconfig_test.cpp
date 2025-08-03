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

#include <array>
#include <cstdio>
#include <fstream>
#include <string>

#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android/api-level.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <vintf/VintfObject.h>

using android::vintf::KernelVersion;
using android::vintf::VintfObject;
using android::base::ParseInt;

namespace android {
namespace kernel {

class KernelLoopConfigTest : public ::testing::Test {
 protected:
  const int first_api_level_;
  KernelLoopConfigTest()
      : first_api_level_(std::stoi(
            android::base::GetProperty("ro.product.first_api_level", "0"))) {}
  bool should_run() const {
    // TODO check for APEX support (for upgrading devices)
    return first_api_level_ >= __ANDROID_API_Q__;
  }
};

TEST_F(KernelLoopConfigTest, ValidLoopCountConfig) {
  if (!should_run()) return;

  static constexpr const char* kCmd =
      "zcat /proc/config.gz | grep CONFIG_BLK_DEV_LOOP_MIN_COUNT";
  std::array<char, 256> line;

  std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(kCmd, "r"), pclose);
  ASSERT_NE(pipe, nullptr);

  auto read = fgets(line.data(), line.size(), pipe.get());
  ASSERT_NE(read, nullptr);

  auto minCountStr = std::string(read);

  auto pos = minCountStr.find("=");
  ASSERT_NE(pos, std::string::npos);
  ASSERT_GE(minCountStr.length(), pos + 1);

  int minCountValue = std::stoi(minCountStr.substr(pos + 1));
  ASSERT_GE(minCountValue, 16);

  std::ifstream max_loop("/sys/module/loop/parameters/max_loop");

  std::string max_loop_str;

  std::getline(max_loop, max_loop_str);

  int max_loop_value;

  ParseInt(max_loop_str, &max_loop_value);

  auto runtime_info = VintfObject::GetRuntimeInfo();
  ASSERT_NE(nullptr, runtime_info);

  /*
   * Upstream commit 85c50197716c ("loop: Fix the max_loop commandline argument
   * treatment when it is set to 0") aligned max_loop to the kernel
   * documentation, which states that when it is not set, it should be
   * CONFIG_BLK_DEV_LOOP_MIN_COUNT instead of 0. This commit was applied to
   * kernels 5.15.86+.
   *
   * For kernels older than 5.15.86, ensure that max_loop is not set by ensuring
   * that it is 0. This ensures that CONFIG_BLK_DEV_LOOP_MIN_COUNT are being
   * pre-allocated.
   *
   * For kernels 5.15.86+ ensure that max_loop is either not set (i.e. it is
   * CONFIG_BLK_DEV_LOOP_MIN_COUNT), or if it is set, it s greater than
   * CONFIG_BLK_DEV_LOOP_MIN_COUNT to ensure that at least that many loop
   * devices are pre-allocated.
   */
  if (runtime_info->kernelVersion() < KernelVersion(5, 15, 86)) {
    EXPECT_EQ(0, max_loop_value);
  } else {
    EXPECT_GE(max_loop_value, minCountValue);
  }
}

TEST_F(KernelLoopConfigTest, ValidLoopPartParameter) {
  if (!should_run()) return;

  std::ifstream max_part("/sys/module/loop/parameters/max_part");

  std::string max_part_str;

  std::getline(max_part, max_part_str);

  int max_part_value = std::stoi(max_part_str);
  EXPECT_LE(max_part_value, 7);
}

}  // namespace kernel
}  // namespace android
