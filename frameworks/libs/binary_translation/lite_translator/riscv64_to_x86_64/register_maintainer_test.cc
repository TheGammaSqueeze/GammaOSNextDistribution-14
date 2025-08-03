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

#include "berberis/assembler/x86_64.h"

#include "register_maintainer.h"

namespace berberis {

namespace {

TEST(RegisterMaintainerTest, Maintainer) {
  RegMaintainer<x86_64::Assembler::Register> maintainer =
      RegMaintainer<x86_64::Assembler::Register>();

  EXPECT_FALSE(maintainer.IsMapped());
  maintainer.Map(x86_64::Assembler::rbx);
  EXPECT_TRUE(maintainer.IsMapped());
  EXPECT_EQ(maintainer.GetMapped(), x86_64::Assembler::rbx);
  EXPECT_FALSE(maintainer.IsModified());
  maintainer.NoticeModified();
  EXPECT_TRUE(maintainer.IsModified());
}

TEST(RegisterMaintainerTest, GPMaintainer) {
  auto maintainer = RegisterFileMaintainer<x86_64::Assembler::Register, 16>();

  EXPECT_FALSE(maintainer.IsMapped(15));
  maintainer.Map(15, x86_64::Assembler::rbp);
  EXPECT_TRUE(maintainer.IsMapped(15));
  EXPECT_EQ(maintainer.GetMapped(15), x86_64::Assembler::rbp);
  EXPECT_FALSE(maintainer.IsModified(15));
  maintainer.NoticeModified(15);
  EXPECT_TRUE(maintainer.IsModified(15));
}

TEST(RegisterMaintainerTest, SimdMaintainer) {
  auto maintainer = RegisterFileMaintainer<x86_64::Assembler::XMMRegister, 16>();

  EXPECT_FALSE(maintainer.IsMapped(15));
  maintainer.Map(15, x86_64::Assembler::xmm11);
  EXPECT_TRUE(maintainer.IsMapped(15));
  EXPECT_EQ(maintainer.GetMapped(15), x86_64::Assembler::xmm11);
  EXPECT_FALSE(maintainer.IsModified(15));
  maintainer.NoticeModified(15);
  EXPECT_TRUE(maintainer.IsModified(15));
}

}  // namespace

}  // namespace berberis