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

#include "managed_register_riscv64.h"

#include "base/globals.h"
#include "gtest/gtest.h"

namespace art HIDDEN {
namespace riscv64 {

TEST(Riscv64ManagedRegister, NoRegister) {
  Riscv64ManagedRegister reg = ManagedRegister::NoRegister().AsRiscv64();
  EXPECT_TRUE(reg.IsNoRegister());
}

TEST(Riscv64ManagedRegister, XRegister) {
  Riscv64ManagedRegister reg = Riscv64ManagedRegister::FromXRegister(Zero);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(Zero, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(RA);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(RA, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(SP);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(SP, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(GP);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(GP, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(T0);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(T0, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(T2);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(T2, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(S0);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(S0, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(A0);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(A0, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(A7);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(A7, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(S2);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(S2, reg.AsXRegister());

  reg = Riscv64ManagedRegister::FromXRegister(T3);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_TRUE(reg.IsXRegister());
  EXPECT_FALSE(reg.IsFRegister());
  EXPECT_EQ(T3, reg.AsXRegister());
}

TEST(Riscv64ManagedRegister, FRegister) {
  Riscv64ManagedRegister reg = Riscv64ManagedRegister::FromFRegister(FT0);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FT0, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));

  reg = Riscv64ManagedRegister::FromFRegister(FT1);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FT1, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FT1)));

  reg = Riscv64ManagedRegister::FromFRegister(FS0);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FS0, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FS0)));

  reg = Riscv64ManagedRegister::FromFRegister(FA0);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FA0, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FA0)));

  reg = Riscv64ManagedRegister::FromFRegister(FA7);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FA7, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FA7)));

  reg = Riscv64ManagedRegister::FromFRegister(FS4);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FS4, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FS4)));

  reg = Riscv64ManagedRegister::FromFRegister(FT11);
  EXPECT_FALSE(reg.IsNoRegister());
  EXPECT_FALSE(reg.IsXRegister());
  EXPECT_TRUE(reg.IsFRegister());
  EXPECT_EQ(FT11, reg.AsFRegister());
  EXPECT_TRUE(reg.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));
}

TEST(Riscv64ManagedRegister, Equals) {
  ManagedRegister no_reg = ManagedRegister::NoRegister();
  EXPECT_TRUE(no_reg.Equals(Riscv64ManagedRegister::NoRegister()));
  EXPECT_FALSE(no_reg.Equals(Riscv64ManagedRegister::FromXRegister(Zero)));
  EXPECT_FALSE(no_reg.Equals(Riscv64ManagedRegister::FromXRegister(A1)));
  EXPECT_FALSE(no_reg.Equals(Riscv64ManagedRegister::FromXRegister(S2)));
  EXPECT_FALSE(no_reg.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));
  EXPECT_FALSE(no_reg.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));

  Riscv64ManagedRegister reg_Zero = Riscv64ManagedRegister::FromXRegister(Zero);
  EXPECT_FALSE(reg_Zero.Equals(Riscv64ManagedRegister::NoRegister()));
  EXPECT_TRUE(reg_Zero.Equals(Riscv64ManagedRegister::FromXRegister(Zero)));
  EXPECT_FALSE(reg_Zero.Equals(Riscv64ManagedRegister::FromXRegister(A1)));
  EXPECT_FALSE(reg_Zero.Equals(Riscv64ManagedRegister::FromXRegister(S2)));
  EXPECT_FALSE(reg_Zero.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));
  EXPECT_FALSE(reg_Zero.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));

  Riscv64ManagedRegister reg_A1 = Riscv64ManagedRegister::FromXRegister(A1);
  EXPECT_FALSE(reg_A1.Equals(Riscv64ManagedRegister::NoRegister()));
  EXPECT_FALSE(reg_A1.Equals(Riscv64ManagedRegister::FromXRegister(Zero)));
  EXPECT_FALSE(reg_A1.Equals(Riscv64ManagedRegister::FromXRegister(A0)));
  EXPECT_TRUE(reg_A1.Equals(Riscv64ManagedRegister::FromXRegister(A1)));
  EXPECT_FALSE(reg_A1.Equals(Riscv64ManagedRegister::FromXRegister(S2)));
  EXPECT_FALSE(reg_A1.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));
  EXPECT_FALSE(reg_A1.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));

  Riscv64ManagedRegister reg_S2 = Riscv64ManagedRegister::FromXRegister(S2);
  EXPECT_FALSE(reg_S2.Equals(Riscv64ManagedRegister::NoRegister()));
  EXPECT_FALSE(reg_S2.Equals(Riscv64ManagedRegister::FromXRegister(Zero)));
  EXPECT_FALSE(reg_S2.Equals(Riscv64ManagedRegister::FromXRegister(A1)));
  EXPECT_FALSE(reg_S2.Equals(Riscv64ManagedRegister::FromXRegister(S1)));
  EXPECT_TRUE(reg_S2.Equals(Riscv64ManagedRegister::FromXRegister(S2)));
  EXPECT_FALSE(reg_S2.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));
  EXPECT_FALSE(reg_S2.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));

  Riscv64ManagedRegister reg_F0 = Riscv64ManagedRegister::FromFRegister(FT0);
  EXPECT_FALSE(reg_F0.Equals(Riscv64ManagedRegister::NoRegister()));
  EXPECT_FALSE(reg_F0.Equals(Riscv64ManagedRegister::FromXRegister(Zero)));
  EXPECT_FALSE(reg_F0.Equals(Riscv64ManagedRegister::FromXRegister(A1)));
  EXPECT_FALSE(reg_F0.Equals(Riscv64ManagedRegister::FromXRegister(S2)));
  EXPECT_TRUE(reg_F0.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));
  EXPECT_FALSE(reg_F0.Equals(Riscv64ManagedRegister::FromFRegister(FT1)));
  EXPECT_FALSE(reg_F0.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));

  Riscv64ManagedRegister reg_F31 = Riscv64ManagedRegister::FromFRegister(FT11);
  EXPECT_FALSE(reg_F31.Equals(Riscv64ManagedRegister::NoRegister()));
  EXPECT_FALSE(reg_F31.Equals(Riscv64ManagedRegister::FromXRegister(Zero)));
  EXPECT_FALSE(reg_F31.Equals(Riscv64ManagedRegister::FromXRegister(A1)));
  EXPECT_FALSE(reg_F31.Equals(Riscv64ManagedRegister::FromXRegister(S2)));
  EXPECT_FALSE(reg_F31.Equals(Riscv64ManagedRegister::FromFRegister(FT0)));
  EXPECT_FALSE(reg_F31.Equals(Riscv64ManagedRegister::FromFRegister(FT1)));
  EXPECT_TRUE(reg_F31.Equals(Riscv64ManagedRegister::FromFRegister(FT11)));
}

}  // namespace riscv64
}  // namespace art
