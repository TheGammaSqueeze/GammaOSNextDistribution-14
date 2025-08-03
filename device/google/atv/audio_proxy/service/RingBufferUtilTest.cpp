// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gtest/gtest.h>

#include "RingBufferUtil.h"

using namespace audio_proxy::service;

using Buffer = std::vector<int8_t>;

class RingBufferUtilTest : public testing::TestWithParam<
                               std::tuple<Buffer, Buffer, Buffer, Buffer>> {};

TEST_P(RingBufferUtilTest, DifferentBufferSize) {
  auto [src1, src2, expectedDst1, expectedDst2] = GetParam();

  Buffer dst1(expectedDst1.size());
  Buffer dst2(expectedDst2.size());

  copyRingBuffer(dst1.data(), dst1.size(), dst2.data(), dst2.size(),
                 src1.data(), src1.size(), src2.data(), src2.size());

  EXPECT_EQ(dst1, expectedDst1);
  EXPECT_EQ(dst2, expectedDst2);
}

// clang-format off
const std::vector<std::tuple<Buffer, Buffer, Buffer, Buffer>> testParams = {
  // The layout are the same for src and dst.
  {
    {0, 1, 2, 3, 4},
    {5, 6, 7, 8, 9},
    {0, 1, 2, 3, 4},
    {5, 6, 7, 8, 9}
  },
  // src1 size is samller than dst1 size.
  {
    {0, 1, 2, 3},
    {4, 5, 6, 7, 8, 9},
    {0, 1, 2, 3, 4},
    {5, 6, 7, 8, 9}
  },
  // src2 size is larger than dst1 size.
  {
    {0, 1, 2, 3, 4, 5},
    {6, 7, 8, 9},
    {0, 1, 2, 3, 4},
    {5, 6, 7, 8, 9}
  },
  // dst1 size is larger enough to hold all the src data.
  {
    {0, 1, 2, 3, 4},
    {5, 6, 7, 8, 9},
    {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 0},
    {0, 0, 0, 0, 0}
  },
  // Empty src
  {{}, {}, {}, {}}
};
// clang-format off

INSTANTIATE_TEST_SUITE_P(RingBufferUtilTestSuite, RingBufferUtilTest,
                         testing::ValuesIn(testParams));

TEST(RingBufferUtilTest, CopyNullptr) {
  // Test should not crash.
  copyRingBuffer(nullptr, 0, nullptr, 0, nullptr, 0, nullptr, 0);
}
