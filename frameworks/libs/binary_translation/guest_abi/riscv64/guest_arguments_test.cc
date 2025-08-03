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

#include <cstddef>
#include <cstdint>

#include "berberis/base/bit_util.h"
#include "berberis/guest_abi/guest_arguments.h"

namespace berberis {

namespace {

TEST(GuestArguments_riscv64_lp64, Smoke) {
  constexpr std::size_t padding_size =
      AlignUp(offsetof(GuestArgumentBuffer, stack_argv), sizeof(uint64_t)) / sizeof(uint64_t) + 4;
  union {
    GuestArgumentBuffer buffer;
    uint64_t padding[padding_size];
  } arguments = {{.argv = {0x8000000000000000,
                           0x3ff0000000000000,
                           0x3fe0000000000000,
                           0x3fd5555555555555,
                           0x3fd0000000000000,
                           0x3fc999999999999a,
                           0x3fc5555555555555,
                           0x3fb5555555555555},
                  .stack_argv = {0x3fc0000000000000}}};
  arguments.buffer.stack_argv[1] = 0x3fbc71c71c71c71c;
  arguments.buffer.stack_argv[2] = 0x3fb999999999999a;
  arguments.buffer.stack_argv[3] = 0x3fb745d1745d1746;

  GuestArgumentsAndResult<
      double(int, double, int, double, int, double, int, double, int, double, int, double),
      GuestAbi::kLp64>
      f1_args(&arguments.buffer);
  EXPECT_EQ(0x00000000, f1_args.GuestArgument<0>());
  EXPECT_EQ(1.0, f1_args.GuestArgument<1>());
  EXPECT_EQ(0x00000000, f1_args.GuestArgument<2>());
  EXPECT_EQ(1.0 / 3.0, f1_args.GuestArgument<3>());
  EXPECT_EQ(0x00000000, f1_args.GuestArgument<4>());
  EXPECT_EQ(1.0 / 5.0, f1_args.GuestArgument<5>());
  EXPECT_EQ(0x55555555, f1_args.GuestArgument<6>());
  EXPECT_EQ(1.0 / 12.0, f1_args.GuestArgument<7>());
  EXPECT_EQ(0x00000000, f1_args.GuestArgument<8>());
  EXPECT_EQ(1.0 / 9.0, f1_args.GuestArgument<9>());
  EXPECT_EQ(static_cast<int>(0x9999999a), f1_args.GuestArgument<10>());
  EXPECT_EQ(1.0 / 11.0, f1_args.GuestArgument<11>());
  EXPECT_EQ(-0.0, f1_args.GuestResult());

  GuestArgumentsAndResult<
      int(double, int, double, int, double, int, double, int, double, int, double, int),
      GuestAbi::kLp64>
      f2_args(&arguments.buffer);
  EXPECT_EQ(-0.0, f2_args.GuestArgument<0>());
  EXPECT_EQ(0x00000000, f2_args.GuestArgument<1>());
  EXPECT_EQ(1.0 / 2.0, f2_args.GuestArgument<2>());
  EXPECT_EQ(0x55555555, f2_args.GuestArgument<3>());
  EXPECT_EQ(1.0 / 4.0, f2_args.GuestArgument<4>());
  EXPECT_EQ(static_cast<int>(0x9999999a), f2_args.GuestArgument<5>());
  EXPECT_EQ(1.0 / 6.0, f2_args.GuestArgument<6>());
  EXPECT_EQ(0x55555555, f2_args.GuestArgument<7>());
  EXPECT_EQ(1.0 / 8.0, f2_args.GuestArgument<8>());
  EXPECT_EQ(0x1c71c71c, f2_args.GuestArgument<9>());
  EXPECT_EQ(1.0 / 10.0, f2_args.GuestArgument<10>());
  EXPECT_EQ(0x745d1746, f2_args.GuestArgument<11>());
  EXPECT_EQ(0, f2_args.GuestResult());
}

TEST(GuestArguments_riscv64_lp64d, Smoke) {
  constexpr std::size_t padding_size =
      AlignUp(offsetof(GuestArgumentBuffer, stack_argv), sizeof(uint64_t)) / sizeof(uint64_t) + 4;
  union {
    GuestArgumentBuffer buffer;
    uint64_t padding[padding_size];
  } arguments = {{.argv = {1, 2, 3, 4, 5, 6, 7, 8},
                  .fp_argv = {0x3ff0000000000000,
                              0x3fe0000000000000,
                              0x3fd5555555555555,
                              0x3fd0000000000000,
                              0x3fc999999999999a,
                              0x3fc5555555555555,
                              0x3fc2492492492492,
                              0x3fc0000000000000},
                  .stack_argv = {0x3fbc71c71c71c71c}}};
  arguments.buffer.stack_argv[1] = 0x3fb999999999999a;
  arguments.buffer.stack_argv[2] = 0x3fb745d1745d1746;
  arguments.buffer.stack_argv[3] = 0x3fb5555555555555;

  GuestArgumentsAndResult<double(int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double,
                                 int,
                                 double),
                          GuestAbi::kLp64d>
      f1_args(&arguments.buffer);
  EXPECT_EQ(1, f1_args.GuestArgument<0>());
  EXPECT_EQ(1.0, f1_args.GuestArgument<1>());
  EXPECT_EQ(2, f1_args.GuestArgument<2>());
  EXPECT_EQ(1.0 / 2.0, f1_args.GuestArgument<3>());
  EXPECT_EQ(3, f1_args.GuestArgument<4>());
  EXPECT_EQ(1.0 / 3.0, f1_args.GuestArgument<5>());
  EXPECT_EQ(4, f1_args.GuestArgument<6>());
  EXPECT_EQ(1.0 / 4.0, f1_args.GuestArgument<7>());
  EXPECT_EQ(5, f1_args.GuestArgument<8>());
  EXPECT_EQ(1.0 / 5.0, f1_args.GuestArgument<9>());
  EXPECT_EQ(6, f1_args.GuestArgument<10>());
  EXPECT_EQ(1.0 / 6.0, f1_args.GuestArgument<11>());
  EXPECT_EQ(7, f1_args.GuestArgument<12>());
  EXPECT_EQ(1.0 / 7.0, f1_args.GuestArgument<13>());
  EXPECT_EQ(8, f1_args.GuestArgument<14>());
  EXPECT_EQ(1.0 / 8.0, f1_args.GuestArgument<15>());
  EXPECT_EQ(0x1c71c71c, f1_args.GuestArgument<16>());
  EXPECT_EQ(1.0 / 10.0, f1_args.GuestArgument<17>());
  EXPECT_EQ(0x745d1746, f1_args.GuestArgument<18>());
  EXPECT_EQ(1.0 / 12.0, f1_args.GuestArgument<19>());
  EXPECT_EQ(1.0, f1_args.GuestResult());

  GuestArgumentsAndResult<int(double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int,
                              double,
                              int),
                          GuestAbi::kLp64d>
      f2_args(&arguments.buffer);
  EXPECT_EQ(1.0, f2_args.GuestArgument<0>());
  EXPECT_EQ(1, f2_args.GuestArgument<1>());
  EXPECT_EQ(1.0 / 2.0, f2_args.GuestArgument<2>());
  EXPECT_EQ(2, f2_args.GuestArgument<3>());
  EXPECT_EQ(1.0 / 3.0, f2_args.GuestArgument<4>());
  EXPECT_EQ(3, f2_args.GuestArgument<5>());
  EXPECT_EQ(1.0 / 4.0, f2_args.GuestArgument<6>());
  EXPECT_EQ(4, f2_args.GuestArgument<7>());
  EXPECT_EQ(1.0 / 5.0, f2_args.GuestArgument<8>());
  EXPECT_EQ(5, f2_args.GuestArgument<9>());
  EXPECT_EQ(1.0 / 6.0, f2_args.GuestArgument<10>());
  EXPECT_EQ(6, f2_args.GuestArgument<11>());
  EXPECT_EQ(1.0 / 7.0, f2_args.GuestArgument<12>());
  EXPECT_EQ(7, f2_args.GuestArgument<13>());
  EXPECT_EQ(1.0 / 8.0, f2_args.GuestArgument<14>());
  EXPECT_EQ(8, f2_args.GuestArgument<15>());
  EXPECT_EQ(1.0 / 9.0, f2_args.GuestArgument<16>());
  EXPECT_EQ(-0x66666666, f2_args.GuestArgument<17>());
  EXPECT_EQ(1.0 / 11.0, f2_args.GuestArgument<18>());
  EXPECT_EQ(0x55555555, f2_args.GuestArgument<19>());
  EXPECT_EQ(1, f2_args.GuestResult());
}

TEST(GuestArguments_riscv64_lp64d, LongParamLargeStructRes) {
  struct Result {
    uint64_t values[10];
  } result{};
  GuestArgumentBuffer buffer{.argv = {ToGuestAddr(&result), 0xdead0000beef}};

  GuestArgumentsAndResult<Result(uint64_t)> args(&buffer);

  EXPECT_EQ(0xdead0000beefUL, args.GuestArgument<0>());

  args.GuestResult() = Result{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

  EXPECT_EQ(1U, result.values[0]);
  EXPECT_EQ(2U, result.values[1]);
  EXPECT_EQ(3U, result.values[2]);
  EXPECT_EQ(4U, result.values[3]);
  EXPECT_EQ(5U, result.values[4]);
  EXPECT_EQ(6U, result.values[5]);
  EXPECT_EQ(7U, result.values[6]);
  EXPECT_EQ(8U, result.values[7]);
  EXPECT_EQ(9U, result.values[8]);
  EXPECT_EQ(10U, result.values[9]);
}

TEST(GuestArguments_riscv64_lp64d, GuestArgumentsReferences) {
  GuestArgumentBuffer buffer{.argv = {100}, .fp_argv = {0x3fe0000000000000}};

  auto&& [length1, angle1] =
      GuestArgumentsReferences<double(int, double), GuestAbi::kLp64d>(&buffer);
  EXPECT_EQ(length1, 100);
  EXPECT_EQ(angle1, 1.0 / 2.0);
  length1 = 200;
  angle1 = 1.0;
  EXPECT_EQ(length1, 200);
  EXPECT_EQ(angle1, 1.0);

  auto&& [length2, angle2] =
      GuestArgumentsReferences<double(int, double), GuestAbi::kLp64d>(&buffer);
  EXPECT_EQ(length2, 200);
  EXPECT_EQ(angle2, 1.0);
}

TEST(GuestArguments_riscv64_lp64d, HostArgumentsValues) {
  GuestArgumentBuffer buffer{.argv = {100}, .fp_argv = {0x3fe0000000000000}};

  auto [length, angle] = HostArgumentsValues<double(int, double), GuestAbi::kLp64d>(&buffer);
  EXPECT_EQ(length, 100);
  EXPECT_EQ(angle, 1.0 / 2.0);
}

TEST(GuestArguments_riscv64_lp64d, GuestResultValue) {
  GuestArgumentBuffer buffer{.argv = {100}, .fp_argv = {0x3fe0000000000000}};

  auto [result] = GuestResultValue<double(int, double), GuestAbi::kLp64d>(&buffer);
  EXPECT_EQ(result, 1.0 / 2.0);
}

TEST(GuestArguments_riscv64_lp64d, HostResultReference) {
  GuestArgumentBuffer buffer{.argv = {100}, .fp_argv = {0x3fe0000000000000}};

  auto&& [result1] = HostResultReference<double(int, double), GuestAbi::kLp64d>(&buffer);
  EXPECT_EQ(result1, 1.0 / 2.0);
  result1 = 1.0;

  auto&& [result2] = HostResultReference<double(int, double), GuestAbi::kLp64d>(&buffer);
  EXPECT_EQ(result2, 1.0);
}

}  // namespace

}  // namespace berberis
