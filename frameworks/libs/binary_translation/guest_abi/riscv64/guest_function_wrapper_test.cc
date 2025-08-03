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

#include "berberis/base/bit_util.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/test_utils/guest_exec_region.h"
#include "berberis/test_utils/translation_test.h"

namespace berberis {

namespace {

class GuestFunctionWrapperTest : public TranslationTest {};

TEST_F(GuestFunctionWrapperTest, WrapNull) {
  using FooPtr = int (*)(int, int);
  EXPECT_EQ(WrapGuestFunction(bit_cast<GuestType<FooPtr>>(0L), "foo"), nullptr);

  using BarPtr = void (*)(void*);
  EXPECT_EQ(WrapGuestFunction(bit_cast<GuestType<BarPtr>>(0L), "bar"), nullptr);
}

TEST_F(GuestFunctionWrapperTest, Wrap2Sub) {
  // int sub(int x, int y) {
  //   return x - y;
  // }
  GuestAddr pc = MakeGuestExecRegion<uint32_t>({
      0x40b5053b,  // subw a0, a0, a1
      0x00008067,  // ret
  });

  using TwoArgFunction = int (*)(int, int);
  TwoArgFunction sub = WrapGuestFunction(bit_cast<GuestType<TwoArgFunction>>(pc), "sub");

  int x = sub(239, 11);
  EXPECT_EQ(x, 228);
}

TEST_F(GuestFunctionWrapperTest, Wrap2SubLong) {
  // int64_t sub_long(int64_t x, int64_t y) {
  //   return x - y;
  // }
  GuestAddr pc = MakeGuestExecRegion<uint32_t>({
      0x40b50533,  // sub a0, a0, a1
      0x00008067,  // ret
  });

  using TwoArgFunction = int64_t (*)(int64_t, int64_t);
  TwoArgFunction sub = WrapGuestFunction(bit_cast<GuestType<TwoArgFunction>>(pc), "sub_long");

  uint64_t x = sub(0xffff0000ffff0001ULL, 0x7fff0000ffff0000ULL);
  EXPECT_EQ(x, 0x8000000000000001ULL);
}

TEST_F(GuestFunctionWrapperTest, Wrap2SubFloat) {
  // float sub_float(float x, float y) {
  //   return x - y;
  // }
  GuestAddr pc = MakeGuestExecRegion<uint32_t>({
      0x08b57553,  // fsub.s fa0, fa0, fa1
      0x00008067,  // ret
  });

  using TwoArgFunction = float (*)(float, float);
  TwoArgFunction sub = WrapGuestFunction(bit_cast<GuestType<TwoArgFunction>>(pc), "sub_float");

  float x = sub(2.71f, 3.14f);
  EXPECT_FLOAT_EQ(x, -0.43f);
}

TEST_F(GuestFunctionWrapperTest, Wrap2SubDouble) {
  // double sub_double(double x, double y) {
  //   return x - y;
  // }
  GuestAddr pc = MakeGuestExecRegion<uint32_t>({
      0x0ab57553,  // fsub.d fa0, fa0, fa1
      0x00008067,  // ret
  });

  using TwoArgFunction = double (*)(double, double);
  TwoArgFunction sub = WrapGuestFunction(bit_cast<GuestType<TwoArgFunction>>(pc), "sub_double");

  double x = sub(2.71, 3.14);
  EXPECT_DOUBLE_EQ(x, -0.43);
}

}  // namespace

}  // namespace berberis
