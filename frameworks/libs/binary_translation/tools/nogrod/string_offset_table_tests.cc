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

#include "gtest/gtest.h"

#include <array>

#include "berberis/base/bit_util.h"
#include "string_offset_table.h"

namespace nogrod {

namespace {

template <typename T>
void RunSmokeTest() {
  using berberis::bit_cast;
  std::array<T, 10> data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

  if constexpr (sizeof(T) == 8) {
    data[0] = uint32_t{0xFFFF'FFFFu};
  }

  StringOffsetTable table(Buffer{bit_cast<const uint8_t*>(data.data()), data.size() * sizeof(T)});

  EXPECT_EQ(table.GetStringOffset(sizeof(T) * 2, 1), 3U);
  EXPECT_EQ(table.GetStringOffset(sizeof(T) * 2, 5), 7U);
  EXPECT_EQ(table.GetStringOffset(sizeof(T) * (data.size() - 1), 0), 9U);

  EXPECT_DEATH((void)table.GetStringOffset(sizeof(T) * 0, 2), "");
  EXPECT_DEATH((void)table.GetStringOffset(sizeof(T) * (data.size()), 0), "");
  EXPECT_DEATH((void)table.GetStringOffset(sizeof(T) * 2, data.size() - 2), "");

  // Unaligned access
  EXPECT_DEATH((void)table.GetStringOffset(sizeof(T) * 2 + 1, 0), "");
  EXPECT_DEATH((void)table.GetStringOffset(sizeof(T) * 2 + sizeof(T) / 2, 0), "");
}

TEST(StringOffsetTalbe, Smoke32) {
  RunSmokeTest<uint32_t>();
}

TEST(StringOffsetTalbe, Smoke64) {
  RunSmokeTest<uint64_t>();
}

}  // namespace

}  // namespace nogrod
