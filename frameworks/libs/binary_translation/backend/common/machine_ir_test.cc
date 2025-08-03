/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "berberis/backend/common/machine_ir.h"

namespace berberis {

namespace {

TEST(MachineReg, DefaultConstructedRegIsInvalid) {
  MachineReg reg;
  EXPECT_EQ(reg, kInvalidMachineReg);
}

TEST(MachineReg, Compare) {
  MachineReg reg1{10};
  MachineReg reg2{12};
  MachineReg reg3{10};
  EXPECT_NE(reg1, reg2);
  EXPECT_EQ(reg1, reg3);
}

TEST(MachineReg, InvalidRegIsNotVRegsNorSpilledRegNorHardReg) {
  MachineReg reg;
  EXPECT_FALSE(reg.IsVReg());
  EXPECT_FALSE(reg.IsSpilledReg());
  EXPECT_FALSE(reg.IsHardReg());
}

TEST(MachineReg, CreateAndCheckVRegByIndex) {
  MachineReg reg = MachineReg::CreateVRegFromIndex(43);
  ASSERT_TRUE(reg.IsVReg());
  EXPECT_EQ(reg.GetVRegIndex(), 43U);
  EXPECT_FALSE(reg.IsSpilledReg());
  EXPECT_FALSE(reg.IsHardReg());
  EXPECT_DEATH((void)reg.GetSpilledRegIndex(), "");
}

TEST(MachineReg, CreateAndCheckSpilledRegByIndex) {
  MachineReg reg = MachineReg::CreateSpilledRegFromIndex(43);
  ASSERT_TRUE(reg.IsSpilledReg());
  EXPECT_EQ(reg.GetSpilledRegIndex(), 43U);
  EXPECT_FALSE(reg.IsVReg());
  EXPECT_FALSE(reg.IsHardReg());
  EXPECT_DEATH((void)reg.GetVRegIndex(), "");
}

TEST(MachineReg, CreateAndCheckHardReg) {
  MachineReg reg{10};
  ASSERT_TRUE(reg.IsHardReg());
  EXPECT_EQ(reg.reg(), 10);
  EXPECT_FALSE(reg.IsVReg());
  EXPECT_FALSE(reg.IsSpilledReg());
  EXPECT_DEATH((void)reg.GetVRegIndex(), "");
  EXPECT_DEATH((void)reg.GetVRegIndex(), "");
}

TEST(MachineReg, CreateRegByIndexOutOfBounds) {
  EXPECT_DEATH((void)MachineReg::CreateVRegFromIndex(std::numeric_limits<int>::max()), "");
  EXPECT_DEATH((void)MachineReg::CreateSpilledRegFromIndex(std::numeric_limits<int>::max()), "");
}

TEST(MachineReg, CreateVRegByIndexOnTheBound) {
  // Note - we use knowledge about internal representation of MachineReg here.
  constexpr const uint32_t kVRegMaxIndex =
      std::numeric_limits<int>::max() - MachineReg::GetFirstVRegNumberForTesting();

  auto reg = MachineReg::CreateVRegFromIndex(kVRegMaxIndex);
  ASSERT_TRUE(reg.IsVReg());
  EXPECT_EQ(reg.GetVRegIndex(), kVRegMaxIndex);
  EXPECT_FALSE(reg.IsSpilledReg());
  EXPECT_FALSE(reg.IsHardReg());
  EXPECT_DEATH((void)MachineReg::CreateVRegFromIndex(kVRegMaxIndex + 1), "");
}

TEST(MachineReg, CreateSpilledRegByIndexOnTheBound) {
  // Note - we use knowledge about internal representation of MachineReg here.
  constexpr const uint32_t kSpilledRegMaxIndex =
      -(std::numeric_limits<int>::min() - MachineReg::GetLastSpilledRegNumberForTesting());

  auto reg = MachineReg::CreateSpilledRegFromIndex(kSpilledRegMaxIndex);
  ASSERT_TRUE(reg.IsSpilledReg());
  EXPECT_EQ(reg.GetSpilledRegIndex(), kSpilledRegMaxIndex);
  EXPECT_FALSE(reg.IsVReg());
  EXPECT_FALSE(reg.IsHardReg());
  EXPECT_DEATH((void)MachineReg::CreateSpilledRegFromIndex(kSpilledRegMaxIndex + 1), "");
}

}  // namespace

}  // namespace berberis
