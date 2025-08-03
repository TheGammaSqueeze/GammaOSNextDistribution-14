/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <inttypes.h>
#include <limits.h>

#include <vector>

#include "leb128.h"

namespace nogrod {

TEST(sleb128, smoke) {
  uint8_t array_0[] = {0x00};
  uint8_t array_neg1[] = {0x7f};
  uint8_t array_63[] = {0x3f};
  uint8_t array_64[] = {0xc0, 0x00};
  uint8_t array_neg64[] = {0xc0, 0x7f};
  uint8_t array_9223372036854775807[] = {
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00};
  uint8_t array_neg9223372036854775808[] = {
      0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x7f};

  int64_t result = 42;

  ASSERT_EQ(1U, DecodeSleb128(array_0, sizeof(array_0), &result));
  ASSERT_EQ(0, result);
  ASSERT_EQ(1U, DecodeSleb128(array_neg1, sizeof(array_neg1), &result));
  ASSERT_EQ(-1, result);
  ASSERT_EQ(1U, DecodeSleb128(array_63, sizeof(array_63), &result));
  ASSERT_EQ(63, result);
  ASSERT_EQ(2U, DecodeSleb128(array_64, sizeof(array_64), &result));
  ASSERT_EQ(64, result);
  ASSERT_EQ(2U, DecodeSleb128(array_neg64, sizeof(array_neg64), &result));
  ASSERT_EQ(-64, result);
  ASSERT_EQ(10U,
            DecodeSleb128(array_9223372036854775807, sizeof(array_9223372036854775807), &result));
  ASSERT_EQ(9223372036854775807LL, result);
  ASSERT_EQ(
      10U,
      DecodeSleb128(array_neg9223372036854775808, sizeof(array_neg9223372036854775808), &result));
  ASSERT_EQ(-9223372036854775807LL - 1, result);
}

TEST(sleb128, overflow) {
  uint8_t array_overflow[] = {0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x0a};
  int64_t result = 42;
  EXPECT_DEATH(DecodeSleb128(array_overflow, sizeof(array_overflow), &result), "");
}

TEST(sleb128, out_of_bounds) {
  uint8_t array_out_of_bounds[] = {0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88};
  int64_t result = 42;
  EXPECT_DEATH(DecodeSleb128(array_out_of_bounds, sizeof(array_out_of_bounds), &result), "");
}

TEST(leb128, smoke) {
  uint8_t array_0[] = {0x00};
  uint8_t array_63[] = {0x3f};
  uint8_t array_64[] = {0xc0, 0x00};
  uint8_t array_UINT64_MAX1[] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01};
  uint8_t array_UINT64_MAX2[] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x05};

  uint64_t result = 42;

  ASSERT_EQ(1U, DecodeLeb128(array_0, sizeof(array_0), &result));
  ASSERT_EQ(0U, result);
  ASSERT_EQ(1U, DecodeLeb128(array_63, sizeof(array_63), &result));
  ASSERT_EQ(63U, result);
  ASSERT_EQ(2U, DecodeLeb128(array_64, sizeof(array_64), &result));
  ASSERT_EQ(64U, result);
  ASSERT_EQ(10U, DecodeLeb128(array_UINT64_MAX1, sizeof(array_UINT64_MAX1), &result));
  ASSERT_EQ(UINT64_MAX, result);
  ASSERT_EQ(10U, DecodeLeb128(array_UINT64_MAX2, sizeof(array_UINT64_MAX2), &result));
  ASSERT_EQ(UINT64_MAX, result);
}

TEST(leb128, overflow) {
  uint8_t array_overflow[] = {0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x0a};
  uint64_t result = 42;
  EXPECT_DEATH(DecodeLeb128(array_overflow, sizeof(array_overflow), &result), "");
}

TEST(leb128, out_of_bounds) {
  uint8_t array_out_of_bounds[] = {0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88};
  uint64_t result = 42;
  EXPECT_DEATH(DecodeLeb128(array_out_of_bounds, sizeof(array_out_of_bounds), &result), "");
}

}  // namespace nogrod
