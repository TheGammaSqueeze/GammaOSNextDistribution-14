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

#include <array>
#include <cstring>

#include "berberis/base/bit_util.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"

namespace berberis {

namespace {

TEST(GuestParams_riscv64_lp64d, PtrIntArgs) {
  ThreadState state{};

  static int x;

  SetXReg<A0>(state.cpu, ToGuestAddr(&x));
  SetXReg<A1>(state.cpu, 1234);
  SetXReg<A2>(state.cpu, 0xffff'ffff'ffff'fff9U);

  auto [param1, param2, param3] =
      GuestParamsValues<void(int*, unsigned int, int), GuestAbi::kLp64d>(&state);
  auto [param1f, param2f, param3f] =
      GuestParamsValues<void (*)(int*, unsigned int, int), GuestAbi::kLp64d>(&state);
  auto [param1v, param2v, param3v] =
      GuestParamsValues<void(int*, unsigned int, int, ...), GuestAbi::kLp64d>(&state);
  auto [param1fv, param2fv, param3fv] =
      GuestParamsValues<void (*)(int*, unsigned int, int, ...), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(param1, &x);
  EXPECT_EQ(param2, 1234U);
  EXPECT_EQ(param3, -7);

  EXPECT_EQ(param1f, &x);
  EXPECT_EQ(param2f, 1234U);
  EXPECT_EQ(param3f, -7);

  EXPECT_EQ(param1v, &x);
  EXPECT_EQ(param2v, 1234U);
  EXPECT_EQ(param3v, -7);

  EXPECT_EQ(param1fv, &x);
  EXPECT_EQ(param2fv, 1234U);
  EXPECT_EQ(param3fv, -7);
}

TEST(GuestParams_riscv64_lp64d, IntRes) {
  ThreadState state{};

  auto&& [ret] = GuestReturnReference<int(), GuestAbi::kLp64d>(&state);
  auto&& [retf] = GuestReturnReference<int (*)(), GuestAbi::kLp64d>(&state);
  auto&& [retv] = GuestReturnReference<int(...), GuestAbi::kLp64d>(&state);
  auto&& [retfv] = GuestReturnReference<int (*)(...), GuestAbi::kLp64d>(&state);

  ret = 123;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 123U);

  ret = -123;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0xffff'ffff'ffff'ff85U);

  retf = 234;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 234U);

  retv = 345;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 345U);

  retv = -345;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0xffff'ffff'ffff'fea7U);

  retfv = 456;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 456U);
}

TEST(GuestParams_riscv64_lp64d, SignedCharRes) {
  ThreadState state{};

  SetXReg<A0>(state.cpu, 0);

  auto&& [ret] = GuestReturnReference<signed char(), GuestAbi::kLp64d>(&state);
  auto&& [retf] = GuestReturnReference<signed char (*)(), GuestAbi::kLp64d>(&state);
  auto&& [retv] = GuestReturnReference<signed char(...), GuestAbi::kLp64d>(&state);
  auto&& [retfv] = GuestReturnReference<signed char (*)(...), GuestAbi::kLp64d>(&state);

  ret = -1;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0xffff'ffff'ffff'ffffU);

  retf = -2;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0xffff'ffff'ffff'fffeU);

  retv = -3;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0xffff'ffff'ffff'fffdU);

  retfv = -4;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0xffff'ffff'ffff'fffcU);
}

TEST(GuestParams_riscv64_lp64d, PtrRes) {
  ThreadState state{};

  SetXReg<A0>(state.cpu, static_cast<uint64_t>(42));

  auto&& [ret] = GuestReturnReference<void*(), GuestAbi::kLp64d>(&state);

  ret = nullptr;
  EXPECT_EQ(GetXReg<A0>(state.cpu), 0U);
}

TEST(GuestParams_riscv64_lp64d, SignedCharArg) {
  ThreadState state{};

  SetXReg<A0>(state.cpu, 0xf0f0f0f0f0f0f0f0ULL);

  auto [arg] = GuestParamsValues<void(signed char), GuestAbi::kLp64d>(&state);
  auto [argf] = GuestParamsValues<void (*)(signed char), GuestAbi::kLp64d>(&state);
  auto [argv] = GuestParamsValues<void(signed char, ...), GuestAbi::kLp64d>(&state);
  auto [argfv] = GuestParamsValues<void (*)(signed char, ...), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(arg, -16);

  EXPECT_EQ(argf, -16);

  EXPECT_EQ(argv, -16);

  EXPECT_EQ(argfv, -16);
}

TEST(GuestParams_riscv64_lp64d, IntFloatIntDoubleArgs) {
  ThreadState state{};

  SetXReg<A0>(state.cpu, 1234);
  SetXReg<A1>(state.cpu, static_cast<uint64_t>(-7));
  SetFReg<FA0>(state.cpu, bit_cast<uint32_t>(2.71f));
  SetFReg<FA1>(state.cpu, bit_cast<uint64_t>(3.14));

  auto [param1, param2, param3, param4] =
      GuestParamsValues<void(unsigned int, float, int, double), GuestAbi::kLp64d>(&state);
  auto [param1f, param2f, param3f, param4f] =
      GuestParamsValues<void (*)(unsigned int, float, int, double), GuestAbi::kLp64d>(&state);
  auto [param1v, param2v, param3v, param4v] =
      GuestParamsValues<void(unsigned int, float, int, double, ...), GuestAbi::kLp64d>(&state);
  auto [param1fv, param2fv, param3fv, param4fv] =
      GuestParamsValues<void (*)(unsigned int, float, int, double, ...), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(param1, 1234U);
  EXPECT_FLOAT_EQ(param2, 2.71f);
  EXPECT_EQ(param3, -7);
  EXPECT_DOUBLE_EQ(param4, 3.14);

  EXPECT_EQ(param1f, 1234U);
  EXPECT_FLOAT_EQ(param2f, 2.71f);
  EXPECT_EQ(param3f, -7);
  EXPECT_DOUBLE_EQ(param4f, 3.14);

  EXPECT_EQ(param1v, 1234U);
  EXPECT_FLOAT_EQ(param2v, 2.71f);
  EXPECT_EQ(param3v, -7);
  EXPECT_DOUBLE_EQ(param4v, 3.14);

  EXPECT_EQ(param1fv, 1234U);
  EXPECT_FLOAT_EQ(param2fv, 2.71f);
  EXPECT_EQ(param3fv, -7);
  EXPECT_DOUBLE_EQ(param4fv, 3.14);
}

TEST(GuestParams_riscv64_lp64d, DoubleRes) {
  ThreadState state{};

  auto&& [ret] = GuestReturnReference<double(), GuestAbi::kLp64d>(&state);
  auto&& [retf] = GuestReturnReference<double (*)(), GuestAbi::kLp64d>(&state);
  auto&& [retv] = GuestReturnReference<double(...), GuestAbi::kLp64d>(&state);
  auto&& [retfv] = GuestReturnReference<double (*)(...), GuestAbi::kLp64d>(&state);

  ret = 3.14;
  EXPECT_DOUBLE_EQ(bit_cast<double>(GetFReg<FA0>(state.cpu)), 3.14);

  retf = 3.15;
  EXPECT_DOUBLE_EQ(bit_cast<double>(GetFReg<FA0>(state.cpu)), 3.15);

  retv = 3.15;
  EXPECT_DOUBLE_EQ(bit_cast<double>(GetFReg<FA0>(state.cpu)), 3.15);

  retfv = 3.16;
  EXPECT_DOUBLE_EQ(bit_cast<double>(GetFReg<FA0>(state.cpu)), 3.16);
}

TEST(GuestParams_riscv64_lp64d, StackArgs) {
  std::array<uint64_t, 8> stack;
  ThreadState state{};
  SetXReg<SP>(state.cpu, ToGuestAddr(stack.data()));

  SetXReg<A0>(state.cpu, 0);
  SetXReg<A1>(state.cpu, 1);
  SetXReg<A2>(state.cpu, 2);
  SetXReg<A3>(state.cpu, 3);
  SetXReg<A4>(state.cpu, 4);
  SetXReg<A5>(state.cpu, 5);
  SetXReg<A6>(state.cpu, 6);
  SetXReg<A7>(state.cpu, 7);
  stack[0] = 8;
  stack[1] = 9;

  SetFReg<FA0>(state.cpu, bit_cast<uint64_t>(0.0));
  SetFReg<FA1>(state.cpu, bit_cast<uint64_t>(1.1));
  SetFReg<FA2>(state.cpu, bit_cast<uint64_t>(2.2));
  SetFReg<FA3>(state.cpu, bit_cast<uint64_t>(3.3));
  SetFReg<FA4>(state.cpu, bit_cast<uint64_t>(4.4));
  SetFReg<FA5>(state.cpu, bit_cast<uint64_t>(5.5));
  SetFReg<FA6>(state.cpu, bit_cast<uint64_t>(6.6));
  SetFReg<FA7>(state.cpu, bit_cast<uint64_t>(7.7));
  stack[2] = bit_cast<uint64_t>(8.8);
  stack[3] = bit_cast<uint64_t>(9.9);

  auto [param1,
        param2,
        param3,
        param4,
        param5,
        param6,
        param7,
        param8,
        param9,
        param10,
        param11,
        param12,
        param13,
        param14,
        param15,
        param16,
        param17,
        param18,
        param19,
        param20] = GuestParamsValues<void(int,
                                          int,
                                          int,
                                          int,
                                          int,
                                          int,
                                          int,
                                          int,
                                          int,
                                          int,
                                          double,
                                          double,
                                          double,
                                          double,
                                          double,
                                          double,
                                          double,
                                          double,
                                          double,
                                          double),
                                     GuestAbi::kLp64d>(&state);
  auto [param1f,
        param2f,
        param3f,
        param4f,
        param5f,
        param6f,
        param7f,
        param8f,
        param9f,
        param10f,
        param11f,
        param12f,
        param13f,
        param14f,
        param15f,
        param16f,
        param17f,
        param18f,
        param19f,
        param20f] = GuestParamsValues<void (*)(int,
                                               int,
                                               int,
                                               int,
                                               int,
                                               int,
                                               int,
                                               int,
                                               int,
                                               int,
                                               double,
                                               double,
                                               double,
                                               double,
                                               double,
                                               double,
                                               double,
                                               double,
                                               double,
                                               double),
                                      GuestAbi::kLp64d>(&state);
  auto [param1v,
        param2v,
        param3v,
        param4v,
        param5v,
        param6v,
        param7v,
        param8v,
        param9v,
        param10v,
        param11v,
        param12v,
        param13v,
        param14v,
        param15v,
        param16v,
        param17v,
        param18v,
        param19v,
        param20v] = GuestParamsValues<void(int,
                                           int,
                                           int,
                                           int,
                                           int,
                                           int,
                                           int,
                                           int,
                                           int,
                                           int,
                                           double,
                                           double,
                                           double,
                                           double,
                                           double,
                                           double,
                                           double,
                                           double,
                                           double,
                                           double,
                                           ...),
                                      GuestAbi::kLp64d>(&state);
  auto [param1fv,
        param2fv,
        param3fv,
        param4fv,
        param5fv,
        param6fv,
        param7fv,
        param8fv,
        param9fv,
        param10fv,
        param11fv,
        param12fv,
        param13fv,
        param14fv,
        param15fv,
        param16fv,
        param17fv,
        param18fv,
        param19fv,
        param20fv] = GuestParamsValues<void (*)(int,
                                                int,
                                                int,
                                                int,
                                                int,
                                                int,
                                                int,
                                                int,
                                                int,
                                                int,
                                                double,
                                                double,
                                                double,
                                                double,
                                                double,
                                                double,
                                                double,
                                                double,
                                                double,
                                                double,
                                                ...),
                                       GuestAbi::kLp64d>(&state);

  EXPECT_EQ(param1, 0);
  EXPECT_EQ(param2, 1);
  EXPECT_EQ(param3, 2);
  EXPECT_EQ(param4, 3);
  EXPECT_EQ(param5, 4);
  EXPECT_EQ(param6, 5);
  EXPECT_EQ(param7, 6);
  EXPECT_EQ(param8, 7);
  EXPECT_EQ(param9, 8);
  EXPECT_EQ(param10, 9);

  EXPECT_DOUBLE_EQ(param11, 0.0);
  EXPECT_DOUBLE_EQ(param12, 1.1);
  EXPECT_DOUBLE_EQ(param13, 2.2);
  EXPECT_DOUBLE_EQ(param14, 3.3);
  EXPECT_DOUBLE_EQ(param15, 4.4);
  EXPECT_DOUBLE_EQ(param16, 5.5);
  EXPECT_DOUBLE_EQ(param17, 6.6);
  EXPECT_DOUBLE_EQ(param18, 7.7);
  EXPECT_DOUBLE_EQ(param19, 8.8);
  EXPECT_DOUBLE_EQ(param20, 9.9);

  EXPECT_EQ(param1f, 0);
  EXPECT_EQ(param2f, 1);
  EXPECT_EQ(param3f, 2);
  EXPECT_EQ(param4f, 3);
  EXPECT_EQ(param5f, 4);
  EXPECT_EQ(param6f, 5);
  EXPECT_EQ(param7f, 6);
  EXPECT_EQ(param8f, 7);
  EXPECT_EQ(param9f, 8);
  EXPECT_EQ(param10f, 9);

  EXPECT_DOUBLE_EQ(param11f, 0.0);
  EXPECT_DOUBLE_EQ(param12f, 1.1);
  EXPECT_DOUBLE_EQ(param13f, 2.2);
  EXPECT_DOUBLE_EQ(param14f, 3.3);
  EXPECT_DOUBLE_EQ(param15f, 4.4);
  EXPECT_DOUBLE_EQ(param16f, 5.5);
  EXPECT_DOUBLE_EQ(param17f, 6.6);
  EXPECT_DOUBLE_EQ(param18f, 7.7);
  EXPECT_DOUBLE_EQ(param19f, 8.8);
  EXPECT_DOUBLE_EQ(param20f, 9.9);

  EXPECT_EQ(param1v, 0);
  EXPECT_EQ(param2v, 1);
  EXPECT_EQ(param3v, 2);
  EXPECT_EQ(param4v, 3);
  EXPECT_EQ(param5v, 4);
  EXPECT_EQ(param6v, 5);
  EXPECT_EQ(param7v, 6);
  EXPECT_EQ(param8v, 7);
  EXPECT_EQ(param9v, 8);
  EXPECT_EQ(param10v, 9);

  EXPECT_DOUBLE_EQ(param11v, 0.0);
  EXPECT_DOUBLE_EQ(param12v, 1.1);
  EXPECT_DOUBLE_EQ(param13v, 2.2);
  EXPECT_DOUBLE_EQ(param14v, 3.3);
  EXPECT_DOUBLE_EQ(param15v, 4.4);
  EXPECT_DOUBLE_EQ(param16v, 5.5);
  EXPECT_DOUBLE_EQ(param17v, 6.6);
  EXPECT_DOUBLE_EQ(param18v, 7.7);
  EXPECT_DOUBLE_EQ(param19v, 8.8);
  EXPECT_DOUBLE_EQ(param20v, 9.9);

  EXPECT_EQ(param1fv, 0);
  EXPECT_EQ(param2fv, 1);
  EXPECT_EQ(param3fv, 2);
  EXPECT_EQ(param4fv, 3);
  EXPECT_EQ(param5fv, 4);
  EXPECT_EQ(param6fv, 5);
  EXPECT_EQ(param7fv, 6);
  EXPECT_EQ(param8fv, 7);
  EXPECT_EQ(param9fv, 8);
  EXPECT_EQ(param10fv, 9);

  EXPECT_DOUBLE_EQ(param11fv, 0.0);
  EXPECT_DOUBLE_EQ(param12fv, 1.1);
  EXPECT_DOUBLE_EQ(param13fv, 2.2);
  EXPECT_DOUBLE_EQ(param14fv, 3.3);
  EXPECT_DOUBLE_EQ(param15fv, 4.4);
  EXPECT_DOUBLE_EQ(param16fv, 5.5);
  EXPECT_DOUBLE_EQ(param17fv, 6.6);
  EXPECT_DOUBLE_EQ(param18fv, 7.7);
  EXPECT_DOUBLE_EQ(param19fv, 8.8);
  EXPECT_DOUBLE_EQ(param20fv, 9.9);
}

TEST(GuestParams_riscv64_lp64d, LongArgLargeStructRes) {
  ThreadState state{};

  struct Result {
    std::array<uint64_t, 10> values;
  } result{};

  SetXReg<A0>(state.cpu, ToGuestAddr(&result));
  SetXReg<A1>(state.cpu, 0xdead0000beef);

  auto [arg] = GuestParamsValues<Result(uint64_t), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(arg, 0xdead0000beefU);

  auto&& [ret] = GuestReturnReference<Result(uint64_t), GuestAbi::kLp64d>(&state);

  ret = Result{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

  EXPECT_EQ(result.values[0], 1U);
  EXPECT_EQ(result.values[1], 2U);
  EXPECT_EQ(result.values[2], 3U);
  EXPECT_EQ(result.values[3], 4U);
  EXPECT_EQ(result.values[4], 5U);
  EXPECT_EQ(result.values[5], 6U);
  EXPECT_EQ(result.values[6], 7U);
  EXPECT_EQ(result.values[7], 8U);
  EXPECT_EQ(result.values[8], 9U);
  EXPECT_EQ(result.values[9], 10U);
}

TEST(GuestVAListParams_riscv64_lp64d, PtrIntArgs) {
  ThreadState state{};

  static int x;

  SetXReg<A0>(state.cpu, ToGuestAddr(&x));
  SetXReg<A1>(state.cpu, 1234);
  SetXReg<A2>(state.cpu, static_cast<uint64_t>(-7));

  GuestVAListParams params = GuestParamsValues<void(...), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(params.GetPointerParam<int>(), &x);
  EXPECT_EQ(params.GetParam<unsigned int>(), 1234U);
  EXPECT_EQ(params.GetParam<int>(), -7);
}

TEST(GuestVAListParams_riscv64_lp64d, IntFloatIntDoubleArgs) {
  ThreadState state{};

  SetXReg<A0>(state.cpu, 1234);
  SetXReg<A1>(state.cpu, bit_cast<uint32_t>(2.71f));
  SetXReg<A2>(state.cpu, static_cast<uint64_t>(-7));
  SetXReg<A3>(state.cpu, bit_cast<uint64_t>(3.14));

  GuestVAListParams params = GuestParamsValues<void(...), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(params.GetParam<unsigned int>(), 1234U);
  EXPECT_FLOAT_EQ(params.GetParam<float>(), 2.71f);
  EXPECT_EQ(params.GetParam<int>(), -7);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 3.14);
}

TEST(GuestVAListParams_riscv64_lp64d, StackArgs) {
  std::array<uint64_t, 4> stack;
  ThreadState state{};
  SetXReg<SP>(state.cpu, ToGuestAddr(stack.data()));

  SetXReg<A0>(state.cpu, 0);
  SetXReg<A1>(state.cpu, bit_cast<uint64_t>(1.1));
  SetXReg<A2>(state.cpu, 2);
  SetXReg<A3>(state.cpu, bit_cast<uint64_t>(3.3));
  SetXReg<A4>(state.cpu, 4);
  SetXReg<A5>(state.cpu, bit_cast<uint64_t>(5.5));
  SetXReg<A6>(state.cpu, 6);
  SetXReg<A7>(state.cpu, bit_cast<uint64_t>(7.7));
  stack[0] = 8;
  stack[1] = bit_cast<uint64_t>(9.9);
  stack[2] = 10;
  stack[3] = bit_cast<uint64_t>(11.11);

  GuestVAListParams params = GuestParamsValues<void(...), GuestAbi::kLp64d>(&state);

  EXPECT_EQ(params.GetParam<int>(), 0);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 1.1);
  EXPECT_EQ(params.GetParam<int>(), 2);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 3.3);
  EXPECT_EQ(params.GetParam<int>(), 4);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 5.5);
  EXPECT_EQ(params.GetParam<int>(), 6);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 7.7);
  EXPECT_EQ(params.GetParam<int>(), 8);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 9.9);
  EXPECT_EQ(params.GetParam<int>(), 10);
  EXPECT_DOUBLE_EQ(params.GetParam<double>(), 11.11);
}

}  // namespace

}  // namespace berberis
