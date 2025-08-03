/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <cinttypes>
#include <vector>

#include "buffer.h"

namespace nogrod {

TEST(buffer, smoke) {
  std::vector<uint8_t> v{30, 31, 32, 33, 34};
  // 1. Create a buffer not backed by the vector
  Buffer b{v.data(), v.size()};

  EXPECT_EQ(b.data(), v.data());
  EXPECT_EQ(b.size(), v.size());

  // 2. Move vector to buffer
  Buffer b1(std::move(v));
  EXPECT_EQ(b1.size(), 5U);
  EXPECT_EQ(b1.data()[0], 30);
  EXPECT_EQ(b1.data()[1], 31);
  EXPECT_EQ(b1.data()[2], 32);
  EXPECT_EQ(b1.data()[3], 33);
  EXPECT_EQ(b1.data()[4], 34);
}

TEST(buffer, move) {
  std::vector<uint8_t> v{30, 31, 32, 33, 34};
  // 1. Create a buffer not backed by the vector
  Buffer b_to_move{v.data(), v.size()};

  Buffer b = std::move(b_to_move);

  EXPECT_EQ(b.data(), v.data());
  EXPECT_EQ(b.size(), v.size());

  // 2. Move vector to buffer
  Buffer b1_to_move(std::move(v));
  Buffer b1 = std::move(b1_to_move);
  EXPECT_EQ(b1.size(), 5U);
  EXPECT_EQ(b1.data()[0], 30);
  EXPECT_EQ(b1.data()[1], 31);
  EXPECT_EQ(b1.data()[2], 32);
  EXPECT_EQ(b1.data()[3], 33);
  EXPECT_EQ(b1.data()[4], 34);
}

}  // namespace nogrod