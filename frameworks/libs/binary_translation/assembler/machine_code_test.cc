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

#include "gtest/gtest.h"

#include <cstdint>

#include "berberis/assembler/machine_code.h"

namespace berberis {

namespace {

TEST(MachineCodeTest, Add) {
  MachineCode mc;

  mc.AddU8(0x00);
  mc.Add<uint16_t>(0x0201);
  mc.Add<uint32_t>(0x06050403);
  mc.AddU8(0x07);

  uint8_t out[128]{};

  ASSERT_GT(sizeof(out), mc.install_size());
  mc.InstallUnsafe(out, nullptr);

  EXPECT_EQ(0x00, out[0]);
  EXPECT_EQ(0x01, out[1]);
  EXPECT_EQ(0x02, out[2]);
  EXPECT_EQ(0x03, out[3]);
  EXPECT_EQ(0x04, out[4]);
  EXPECT_EQ(0x05, out[5]);
  EXPECT_EQ(0x06, out[6]);
  EXPECT_EQ(0x07, out[7]);
}

TEST(MachineCodeTest, RelocRecoveryPoint) {
  MachineCode mc;

  mc.AddU8(0xde);
  mc.AddU8(0xad);
  mc.AddU8(0xbe);
  mc.AddU8(0xef);

  mc.AddRelocation(0, RelocationType::RelocRecoveryPoint, 1, 3);

  uint8_t out[128]{};
  RecoveryMap rec;

  ASSERT_GT(sizeof(out), mc.install_size());
  mc.InstallUnsafe(out, &rec);

  EXPECT_EQ(0xad, out[1]);
  EXPECT_EQ(0xef, out[3]);

  auto fault_addr = reinterpret_cast<uintptr_t>(&out[1]);
  auto recovery_addr = reinterpret_cast<uintptr_t>(&out[3]);

  EXPECT_EQ(recovery_addr, rec[fault_addr]);
}

TEST(MachineCodeTest, RelocAbsToDisp32) {
  MachineCode mc;

  mc.AddU8(0xf1);
  mc.Add<uint32_t>(0xcccccccc);
  mc.AddU8(0xf6);
  mc.Add<uint32_t>(0xcccccccc);
  mc.AddU8(0xfb);

  // Relocate absolute addresses from 'out' to ensure displacement limit.
  uint8_t out[128]{};

  mc.AddRelocation(
      1, RelocationType::RelocAbsToDisp32, 0, reinterpret_cast<intptr_t>(out) + 128);  // 128 (0x80)
  mc.AddRelocation(
      6, RelocationType::RelocAbsToDisp32, 6, reinterpret_cast<intptr_t>(out));  //  -6 (0xfa)

  ASSERT_GT(sizeof(out), mc.install_size());
  mc.InstallUnsafe(out, nullptr);

  EXPECT_EQ(0xf1, out[0]);
  EXPECT_EQ(0x80, out[1]);
  EXPECT_EQ(0x00, out[2]);
  EXPECT_EQ(0x00, out[3]);
  EXPECT_EQ(0x00, out[4]);
  EXPECT_EQ(0xf6, out[5]);
  EXPECT_EQ(0xfa, out[6]);
  EXPECT_EQ(0xff, out[7]);
  EXPECT_EQ(0xff, out[8]);
  EXPECT_EQ(0xff, out[9]);
  EXPECT_EQ(0xfb, out[10]);
}

}  // namespace

}  // namespace berberis
