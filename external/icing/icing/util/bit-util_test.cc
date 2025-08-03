// Copyright (C) 2021 Google LLC
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

#include "icing/util/bit-util.h"

#include <memory>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace icing {
namespace lib {
namespace {

using ::testing::Eq;

TEST(BitUtilTest, BitfieldMask) {
  // Check that we can handle up to uint8_t's
  EXPECT_THAT(bit_util::BitfieldMask(/*len=*/0), Eq(0b0));
  EXPECT_THAT(bit_util::BitfieldMask(/*len=*/1), Eq(0b01));

  // Check that we can handle up to uint32_t's
  EXPECT_THAT(bit_util::BitfieldMask(/*len=*/16), Eq(0b01111111111111111));

  // Check that we can handle up to uint64_t's
  EXPECT_THAT(
      bit_util::BitfieldMask(/*len=*/63),
      Eq(0b0111111111111111111111111111111111111111111111111111111111111111));
}

TEST(BitUtilTest, BitfieldClear) {
  // Check that we can handle up to uint8_t's
  uint8_t value_8 = 0b0;
  bit_util::BitfieldClear(/*lsb_offset=*/0, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b0));

  value_8 = 0b01;
  bit_util::BitfieldClear(/*lsb_offset=*/0, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b00));

  value_8 = 0b011;
  bit_util::BitfieldClear(/*lsb_offset=*/1, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b001));

  value_8 = 0b011;
  bit_util::BitfieldClear(/*lsb_offset=*/0, /*len=*/2, &value_8);
  EXPECT_THAT(value_8, Eq(0b000));

  value_8 = 0b0110;
  bit_util::BitfieldClear(/*lsb_offset=*/1, /*len=*/2, &value_8);
  EXPECT_THAT(value_8, Eq(0b0000));

  // Check that we can handle up to uint32_t's
  uint32_t value_32 = 0b010000000000000000000000;
  bit_util::BitfieldClear(/*lsb_offset=*/22, /*len=*/1, &value_32);
  EXPECT_THAT(value_32, Eq(0b0));

  // Check that we can handle up to uint64_t's
  uint64_t value_64 = 0b0100000000000000000000000000000000000;
  bit_util::BitfieldClear(/*lsb_offset=*/35, /*len=*/1, &value_64);
  EXPECT_THAT(value_64, Eq(0b0));
}

TEST(BitUtilTest, BitfieldGet) {
  // Get something in the uint8_t range
  EXPECT_THAT(bit_util::BitfieldGet(0b0, /*lsb_offset=*/0, /*len=*/1), Eq(0b0));
  EXPECT_THAT(bit_util::BitfieldGet(0b01, /*lsb_offset=*/0, /*len=*/1),
              Eq(0b01));
  EXPECT_THAT(bit_util::BitfieldGet(0b010, /*lsb_offset=*/1, /*len=*/1),
              Eq(0b01));
  EXPECT_THAT(bit_util::BitfieldGet(0b001, /*lsb_offset=*/1, /*len=*/1),
              Eq(0b0));
  EXPECT_THAT(bit_util::BitfieldGet(0b011, /*lsb_offset=*/0, /*len=*/2),
              Eq(0b011));
  EXPECT_THAT(bit_util::BitfieldGet(0b0110, /*lsb_offset=*/1, /*len=*/2),
              Eq(0b011));
  EXPECT_THAT(bit_util::BitfieldGet(0b0101, /*lsb_offset=*/0, /*len=*/3),
              Eq(0b0101));

  // Get something in the uint32_t range
  EXPECT_THAT(
      bit_util::BitfieldGet(0b01000000000000, /*lsb_offset=*/12, /*len=*/1),
      Eq(0b01));

  // Get something in the uint64_t range
  EXPECT_THAT(bit_util::BitfieldGet(0b010000000000000000000000000000000000,
                                    /*lsb_offset=*/34, /*len=*/1),
              Eq(0b01));
}

TEST(BitUtilTest, BitfieldSet) {
  // Set something in the uint8_t range
  uint8_t value_8 = 0b0;
  bit_util::BitfieldSet(0b0, /*lsb_offset=*/0, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b0));

  value_8 = 0b01;
  bit_util::BitfieldSet(0b01, /*lsb_offset=*/0, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b01));

  value_8 = 0b00;
  bit_util::BitfieldSet(0b01, /*lsb_offset=*/0, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b01));

  value_8 = 0b00;
  bit_util::BitfieldSet(0b011, /*lsb_offset=*/0, /*len=*/2, &value_8);
  EXPECT_THAT(value_8, Eq(0b011));

  value_8 = 0b01;
  bit_util::BitfieldSet(0b011, /*lsb_offset=*/0, /*len=*/2, &value_8);
  EXPECT_THAT(value_8, Eq(0b011));

  value_8 = 0b01;
  bit_util::BitfieldSet(0b01, /*lsb_offset=*/1, /*len=*/1, &value_8);
  EXPECT_THAT(value_8, Eq(0b011));

  value_8 = 0b0001;
  bit_util::BitfieldSet(0b011, /*lsb_offset=*/1, /*len=*/2, &value_8);
  EXPECT_THAT(value_8, Eq(0b0111));

  // Set something in the uint32_t range
  uint32_t value_32 = 0b0;
  bit_util::BitfieldSet(0b01, /*lsb_offset=*/16, /*len=*/1, &value_32);
  EXPECT_THAT(value_32, Eq(0b010000000000000000));

  // Set something in the uint64_t range
  uint64_t value_64 = 0b0;
  bit_util::BitfieldSet(0b01, /*lsb_offset=*/34, /*len=*/1, &value_64);
  EXPECT_THAT(value_64, Eq(0b010000000000000000000000000000000000));
}

}  // namespace
}  // namespace lib
}  // namespace icing
