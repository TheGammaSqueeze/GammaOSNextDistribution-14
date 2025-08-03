/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifdef __ARM_NEON__

#include "gtest/gtest.h"

#include <arm_neon.h>

TEST(Neon, VSRI) {
  uint32x2_t d = {0x11223344, 0x55667788};
  uint32x2_t m = {0x99AABBCC, 0xDDEEFF00};
  uint32x2_t r = vsri_n_u32(d, m, 8);
  EXPECT_EQ(reinterpret_cast<uint32_t*>(&r)[0], 0x1199AABBu);
  EXPECT_EQ(reinterpret_cast<uint32_t*>(&r)[1], 0x55DDEEFFu);

  uint64x2_t a = {0x1122334455667788, 0x8877665544332211};
  uint64x2_t b = {0x99AABBCCDDEEFF00, 0x00FFEEDDCCBBAA99};
  uint64x2_t c = vsriq_n_u64(a, b, 40);
  EXPECT_EQ(reinterpret_cast<uint64_t*>(&c)[0], 0x112233445599AABBu);
  EXPECT_EQ(reinterpret_cast<uint64_t*>(&c)[1], 0x887766554400FFEEu);
}

TEST(Neon, VTBL) {
  int8x8x4_t table;
  for (size_t i = 0; i < sizeof(table); i++) reinterpret_cast<uint8_t*>(&table)[i] = i * 2;

  int8x8_t control = {10, 0, 31, 32, -1, 127, 1, 2};
  int8x8_t r = vtbl4_s8(table, control);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[0], 20);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[1], 0);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[2], 62);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[3], 0);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[4], 0);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[5], 0);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[6], 2);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[7], 4);

  int8x8_t a = {100, 101, 102, 103, 104, 105, 106, 107};
  r = vtbx4_s8(a, table, control);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[0], 20);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[1], 0);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[2], 62);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[3], 103);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[4], 104);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[5], 105);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[6], 2);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[7], 4);
}

TEST(Neon, VTRN) {
  uint8x8_t d = {11, 22, 33, 44, 55, 66, 77, 88};
  uint8x8_t m = {1, 2, 3, 4, 5, 6, 7, 8};
  uint8x8x2_t r = vtrn_u8(d, m);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[0], 11);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[1], 1);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[2], 33);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[3], 3);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[4], 55);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[5], 5);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[6], 77);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[7], 7);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[8], 22);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[9], 2);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[10], 44);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[11], 4);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[12], 66);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[13], 6);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[14], 88);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[15], 8);
}

TEST(Neon, VZIP) {
  uint8x8_t d = {11, 22, 33, 44, 55, 66, 77, 88};
  uint8x8_t m = {1, 2, 3, 4, 5, 6, 7, 8};
  uint8x8x2_t r = vzip_u8(d, m);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[0], 11);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[1], 1);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[2], 22);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[3], 2);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[4], 33);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[5], 3);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[6], 44);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[7], 4);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[8], 55);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[9], 5);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[10], 66);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[11], 6);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[12], 77);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[13], 7);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[14], 88);
  EXPECT_EQ(reinterpret_cast<uint8_t*>(&r)[15], 8);
}

#endif  // __ARM_NEON__
