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

#include <cstdint>

#include "berberis/guest_abi/guest_abi.h"

namespace berberis {

namespace {

TEST(GuestAbi_riscv64, GuestArgumentInt8) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<int8_t>*>(&value);

  value = 0xffff'ffff'ffff'fff9U;
  EXPECT_EQ(param, -7);

  value = 7;
  EXPECT_EQ(param, 7);

  param = -123;
  EXPECT_EQ(value, 0xffff'ffff'ffff'ff85U);

  param = 127;
  EXPECT_EQ(value, 0x0000'0000'0000'007fU);
}

TEST(GuestAbi_riscv64, GuestArgumentUInt8) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<uint8_t>*>(&value);

  value = 0x0000'0000'0000'00f9U;
  EXPECT_EQ(param, 249);

  value = 7;
  EXPECT_EQ(param, 7);

  param = 123;
  EXPECT_EQ(value, 0x0000'0000'0000'007bU);

  param = 255;
  EXPECT_EQ(value, 0x0000'0000'0000'00ffU);
}

TEST(GuestAbi_riscv64, GuestArgumentUInt32) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<uint32_t>*>(&value);

  value = 0xffff'ffff'ffff'ffffU;
  EXPECT_EQ(param, 0xffff'ffffU);

  value = 7;
  EXPECT_EQ(param, 7U);

  param = 0xf123'4567U;
  EXPECT_EQ(value, 0xffff'ffff'f123'4567U);

  param += 1;
  EXPECT_EQ(value, 0xffff'ffff'f123'4568U);
}

TEST(GuestAbi_riscv64, GuestArgumentEnumUInt32) {
  enum class Enum : uint32_t {
    kA = 0xffff'ffffU,
    kB = 7,
    kC = 0xf123'4567U,
  };

  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<Enum>*>(&value);

  value = 0xffff'ffff'ffff'ffffU;
  EXPECT_EQ(static_cast<Enum>(param), Enum::kA);

  value = 7;
  EXPECT_EQ(static_cast<Enum>(param), Enum::kB);

  param = Enum::kC;
  EXPECT_EQ(value, 0xffff'ffff'f123'4567U);
}

TEST(GuestAbi_riscv64_lp64, GuestArgumentFloat32) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<float, GuestAbi::kLp64>*>(&value);

  value = 0x0000'0000'3f00'0000;
  EXPECT_FLOAT_EQ(param, 0.5f);

  param = 7.125f;
  EXPECT_EQ(value, 0x0000'0000'40e4'0000U);
}

TEST(GuestAbi_riscv64_lp64, GuestArgumentFloat64) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<double, GuestAbi::kLp64>*>(&value);

  value = 0x3fd5'c28f'5c28'f5c3;
  EXPECT_DOUBLE_EQ(param, 0.34);

  param = 0.125f;
  EXPECT_EQ(value, 0x3fc0'0000'0000'0000U);
}

TEST(GuestAbi_riscv64_lp64d, GuestArgumentFloat32) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<float, GuestAbi::kLp64d>*>(&value);

  value = 0xffff'ffff'3f00'0000;
  EXPECT_FLOAT_EQ(param, 0.5f);

  param = 7.125f;
  EXPECT_EQ(value, 0xffff'ffff'40e4'0000U);
}

TEST(GuestAbi_riscv64_lp64d, GuestArgumentFloat64) {
  uint64_t value = 0;
  auto& param = *reinterpret_cast<GuestAbi::GuestArgument<double, GuestAbi::kLp64d>*>(&value);

  value = 0x3fd5'c28f'5c28'f5c3;
  EXPECT_DOUBLE_EQ(param, 0.34);

  param = 0.125f;
  EXPECT_EQ(value, 0x3fc0'0000'0000'0000U);
}

}  // namespace

}  // namespace berberis
