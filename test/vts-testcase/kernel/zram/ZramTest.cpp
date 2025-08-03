/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless requied by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <fstream>

using android::base::ParseInt;
using android::base::Split;

namespace android {

enum swap_fields {
  SWAP_FILENAME = 0,
  SWAP_TYPE = 1,
  SWAP_SIZE = 2,
  SWAP_USED = 3,
  SWAP_PRIORITY = 4,
  SWAP_NUM = 5,
};

TEST(ZramTest, hasZramSwap) {
  const char* procSwapsPath = "/proc/swaps";
  const char* swapFilename = "/dev/block/zram0";
  int64_t swapSize;
  std::string delimiters = "\t ";
  std::ifstream ifs(procSwapsPath);
  std::string line;

  // Discard the header (first line)
  if (!std::getline(ifs, line)) {
    FAIL() << "Failed to read /proc/swaps.";
  }

  if (!std::getline(ifs, line)) {
    FAIL() << "No swaps found.";
  }

  std::vector<std::string> data = Split(line, delimiters);
  // Remove empty strings
  data.erase(std::remove_if(data.begin(), data.end(),
                            [](const std::string& x) { return x.empty(); }),
             data.end());

  ASSERT_EQ(SWAP_NUM, data.size()) << "Unexpected format in /proc/swaps.";
  ASSERT_STREQ(swapFilename, data[SWAP_FILENAME].c_str())
      << "No zram device found.";
  ParseInt(data[SWAP_SIZE], &swapSize);
  ASSERT_GE(swapSize, 0) << "No swap space on zram0.";
}
}  // namespace android
