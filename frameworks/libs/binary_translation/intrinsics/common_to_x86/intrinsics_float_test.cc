/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <cmath>
#include <utility>  // std::forward

#include "berberis/base/bit_util.h"
#include "berberis/intrinsics/common/intrinsics_float.h"
#include "berberis/intrinsics/common_to_x86/intrinsics_float.h"

namespace berberis {

namespace intrinsics {

// On i386 we have problems with returning NaN floats and doubles from function.
// This is ABI issue (passing certain values through 8087 stack corrupts them) and couldn't be
// fixed - but could be hidden by optimized build
//
// This wrapper makes sure function in question is actually called using 8087 stack on IA32 and
// using proper calling conventions on other platforms.
//
// Note: if function is made static or put in the anonymous namespace then clang may change the
// calling convention!  Keep it exportable to prevent that.
template <typename FuncType>
class NonInlineWrapper;

template <typename ResultType, typename... ArgsTypes>
class NonInlineWrapper<ResultType(ArgsTypes...)> {
 public:
  template <ResultType (*function)(ArgsTypes...)>
  static ResultType __attribute__((noinline)) Call(ArgsTypes&&... args) {
    return function(std::forward<ArgsTypes>(args)...);
  }
};

namespace {

// We couldn't use float consts here because of bit_cast.
constexpr uint32_t kBadNegativeNan32 = 0xff811dea;
constexpr uint32_t kBadPositiveNan32 = 0x7f811dea;
constexpr uint64_t kBadNegativeNan64 = 0xfff0deadbeaf0000;
constexpr uint64_t kBadPositiveNan64 = 0x7ff0deadbeaf0000;
// We don't use std::limits because we want to be sure definitions match RISC-V.
constexpr uint32_t kPlusZero32 = 0x00000000;
constexpr uint32_t kPlusOne32 = 0x3f800000;
constexpr uint32_t kMinusZero32 = 0x80000000;
constexpr uint32_t kMinusOne32 = 0xbf800000;
constexpr uint32_t kPlusInfinity32 = 0x7f800000;
constexpr uint32_t kMinusInfinity32 = 0xff800000;
// Default NaN created as result of math operations (when NaN wasn't an input).
#if defined(__i386__) || defined(__x86_64__)
constexpr uint32_t kDefaultNan32 = 0xffc00000;
#else
constexpr uint32_t kDefaultNan32 = 0x7fc00000;
#endif
constexpr uint64_t kPlusZero64 = 0x0000000000000000;
constexpr uint64_t kPlusOne64 = 0x3ff0000000000000;
constexpr uint64_t kMinusZero64 = 0x8000000000000000;
constexpr uint64_t kMinusOne64 = 0xbff0000000000000;
constexpr uint64_t kPlusInfinity64 = 0x7ff0000000000000;
constexpr uint64_t kMinusInfinity64 = 0xfff0000000000000;
#if defined(__i386__) || defined(__x86_64__)
constexpr uint64_t kDefaultNan64 = 0xfff8000000000000;
#else
constexpr uint64_t kDefaultNan64 = 0x7ff8000000000000;
#endif

#ifdef __i386__

// Please note: tests below are NOT GUARANTEED to succeed on ALL IA32 platforms.
//
// They are only relevant for platforms where 8087 stack is used to pass float/double results.
//
// If they fail in your platforms then you don't need complex dance in intrinsics_float_x86.h

TEST(FPU, float_std_fabs) {
  uint32_t fabs_result = bit_cast<uint32_t, float>(NonInlineWrapper<float(float)>::Call<std::fabs>(
      bit_cast<float, uint32_t>(kBadNegativeNan32)));
  EXPECT_NE(fabs_result, kBadPositiveNan32);
}

TEST(FPU, float_fabsf) {
  uint32_t fabsf_result = bit_cast<uint32_t, float>(
      NonInlineWrapper<float(float)>::Call<::fabsf>(bit_cast<float, uint32_t>(kBadNegativeNan32)));
  EXPECT_NE(fabsf_result, kBadPositiveNan32);
}

TEST(FPU, double_std_fabs) {
  uint64_t fabs_result =
      bit_cast<uint64_t, double>(NonInlineWrapper<double(double)>::Call<std::fabs>(
          bit_cast<double, uint64_t>(kBadNegativeNan64)));
  EXPECT_NE(fabs_result, kBadPositiveNan64);
}

TEST(FPU, double_fabs) {
  uint64_t fabs_result = bit_cast<uint64_t, double>(NonInlineWrapper<double(double)>::Call<::fabs>(
      bit_cast<double, uint64_t>(kBadNegativeNan64)));
  EXPECT_NE(fabs_result, kBadPositiveNan64);
}

#endif

TEST(FPU, Float32_fabs) {
  uint32_t fabs_result =
      bit_cast<uint32_t, Float32>(NonInlineWrapper<Float32(const Float32&)>::Call<Absolute>(
          bit_cast<Float32, uint32_t>(kBadNegativeNan32)));
  EXPECT_EQ(fabs_result, kBadPositiveNan32);
}

TEST(FPU, Float64_fabs) {
  uint64_t fabs_result =
      bit_cast<uint64_t, Float64>(NonInlineWrapper<Float64(const Float64&)>::Call<Absolute>(
          bit_cast<Float64, uint64_t>(kBadNegativeNan64)));
  EXPECT_EQ(fabs_result, kBadPositiveNan64);
}

TEST(FPU, Float32_fneg) {
  uint32_t fabs_result =
      bit_cast<uint32_t, Float32>(NonInlineWrapper<Float32(const Float32&)>::Call<Negative>(
          bit_cast<Float32, uint32_t>(kBadNegativeNan32)));
  EXPECT_EQ(fabs_result, kBadPositiveNan32);
}

TEST(FPU, Float64_fneg) {
  uint64_t fabs_result =
      bit_cast<uint64_t, Float64>(NonInlineWrapper<Float64(const Float64&)>::Call<Negative>(
          bit_cast<Float64, uint64_t>(kBadNegativeNan64)));
  EXPECT_EQ(fabs_result, kBadPositiveNan64);
}

TEST(FPU, Float32_InfPlusMinusInf) {
  // +inf + +inf => +inf
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) +
                                                bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kPlusInfinity32);
  // -inf + -inf => -inf
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) +
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kMinusInfinity32);
  // +inf + -inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) +
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // -inf + +inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) +
                                       bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
}

TEST(FPU, Float64_InfPlusMinusInf) {
  // +inf + +inf => +inf
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) +
                                                bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kPlusInfinity64);
  // -inf + -inf => -inf
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) +
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kMinusInfinity64);
  // +inf + -inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) +
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // -inf + +inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) +
                                       bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
}

TEST(FPU, Float32_ZeroPlusMinusZero) {
  // +0.f + +0.f => +0.f
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) +
                                                bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kPlusZero32);
  // +0.f + -0.f => +0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) +
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kPlusZero32);
  // -0.f + +0.f => +0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) +
                                       bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kPlusZero32);
  // -0.f + -0.f => -0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) +
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kMinusZero32);
}

TEST(FPU, Float64_ZeroPlusMinusZero) {
  // +0.f + +0.f => +0.f
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) +
                                                bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kPlusZero64);
  // +0.f + -0.f => +0.f
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) +
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kPlusZero64);
  // -0.f + +0.f => +0.f
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) +
                                       bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kPlusZero64);
  // -0.f + -0.f => -0.f
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) +
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kMinusZero64);
}

TEST(FPU, Float32_InfMinusInf) {
  // +inf - -inf => +inf
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) -
                                                bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kPlusInfinity32);
  // -inf - +inf => -inf
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) -
                                       bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kMinusInfinity32);
  // +inf - +inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) -
                                       bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // -inf - -inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) -
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
}

TEST(FPU, Float64_InfMinusInf) {
  // +inf - -inf => +inf
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) -
                                                bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kPlusInfinity64);
  // -inf - +inf => -inf
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) -
                                       bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kMinusInfinity64);
  // +inf - +inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) -
                                       bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // -inf - -inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) -
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
}

TEST(FPU, Float32_ZeroMinusZero) {
  // +0.f - +0.f => +0.f
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) -
                                                bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kPlusZero32);
  // +0.f - -0.f => +0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) -
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kPlusZero32);
  // -0.f - +0.f => -0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) -
                                       bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kMinusZero32);
  // -0.f - +0.f => +0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) -
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kPlusZero32);
}

TEST(FPU, Float64_ZeroMinusZero) {
  // +0.0 - +0.0 => +0.0
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) -
                                                bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kPlusZero64);
  // +0.0 - -0.0 => +0.0
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) -
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kPlusZero64);
  // -0.0 - +0.0 => -0.0
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) -
                                       bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kMinusZero64);
  // -0.0 - +0.0 => +0.0
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) -
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kPlusZero64);
}

TEST(FPU, Float32_InfMultiplyByZero) {
  // +inf * +0.f => dNaN
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) *
                                                bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // +0.f * +inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) *
                                       bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // +inf * -0.f => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) *
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // -0.f * +inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) *
                                       bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // -inf * +0.f => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) *
                                       bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // +0.f * -inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) *
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // -inf * -0.f => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) *
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // -0.f * -inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) *
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
}

TEST(FPU, Float64_InfMultiplyByZero) {
  // +inf * +0.0 => dNaN
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) *
                                                bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // +0.0 * +inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) *
                                       bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // +inf * -0.0 => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) *
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // -0.0 * +inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) *
                                       bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // -inf * +0.0 => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) *
                                       bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // +0.0 * -inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) *
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // -inf * -0.0 => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) *
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // -0.0 * -inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) *
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
}

TEST(FPU, Float32_ZeroMultiplyByZero) {
  // +0.f * +0.f => +0.f
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) *
                                                bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kPlusZero32);
  // +0.f * -0.f => -0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) *
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kMinusZero32);
  // -0.f * +0.f => -0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) *
                                       bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kMinusZero32);
  // -0.f * -0.f => +0.f
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) *
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kPlusZero32);
}

TEST(FPU, Float64_ZeroMultiplyByZero) {
  // +0.0 * +0.0 => +0.0
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) *
                                                bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kPlusZero64);
  // +0.0 * -0.0 => -0.0
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) *
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kMinusZero64);
  // -0.0 * +0.0 => -0.0
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) *
                                       bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kMinusZero64);
  // -0.0 * -0.0 => +0.0
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) *
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kPlusZero64);
}

TEST(FPU, Float32_InfDivideByInf) {
  // +inf / +inf => dNaN
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) /
                                                bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // +inf / -inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusInfinity32) /
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // -inf / +inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) /
                                       bit_cast<Float32, uint32_t>(kPlusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
  // -inf / -inf => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusInfinity32) /
                                       bit_cast<Float32, uint32_t>(kMinusInfinity32));
  EXPECT_EQ(result, kDefaultNan32);
}

TEST(FPU, Float64_InfDivideByInf) {
  // +inf / +inf => dNaN
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) /
                                                bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // +inf / -inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusInfinity64) /
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // -inf / +inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) /
                                       bit_cast<Float64, uint64_t>(kPlusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
  // -inf / -inf => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusInfinity64) /
                                       bit_cast<Float64, uint64_t>(kMinusInfinity64));
  EXPECT_EQ(result, kDefaultNan64);
}

TEST(FPU, Float32_ZeroDivideByZero) {
  // +0.f - +0.f => dNaN
  uint32_t result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) /
                                                bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // +0.f - -0.f => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kPlusZero32) /
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // -0.f - +0.f => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) /
                                       bit_cast<Float32, uint32_t>(kPlusZero32));
  EXPECT_EQ(result, kDefaultNan32);
  // -0.f - +0.f => dNaN
  result = bit_cast<uint32_t, Float32>(bit_cast<Float32, uint32_t>(kMinusZero32) /
                                       bit_cast<Float32, uint32_t>(kMinusZero32));
  EXPECT_EQ(result, kDefaultNan32);
}

TEST(FPU, Float64_ZeroDivideByZero) {
  // +0.0 - +0.0 => dNaN
  uint64_t result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) /
                                                bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // +0.0 - -0.0 => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kPlusZero64) /
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // -0.0 - +0.0 => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) /
                                       bit_cast<Float64, uint64_t>(kPlusZero64));
  EXPECT_EQ(result, kDefaultNan64);
  // -0.0 - +0.0 => dNaN
  result = bit_cast<uint64_t, Float64>(bit_cast<Float64, uint64_t>(kMinusZero64) /
                                       bit_cast<Float64, uint64_t>(kMinusZero64));
  EXPECT_EQ(result, kDefaultNan64);
}

TEST(FPU, Float32_Sqrt) {
  // +0.0 => +0.0
  uint32_t result = bit_cast<uint32_t, Float32>(Sqrt(bit_cast<Float32, uint32_t>(kPlusZero32)));
  EXPECT_EQ(result, kPlusZero32);
  // -0.0 => -0.0
  result = bit_cast<uint32_t, Float32>(Sqrt(bit_cast<Float32, uint32_t>(kMinusZero32)));
  EXPECT_EQ(result, kMinusZero32);
  // +1.0 => +1.0
  result = bit_cast<uint32_t, Float32>(Sqrt(bit_cast<Float32, uint32_t>(kPlusOne32)));
  EXPECT_EQ(result, kPlusOne32);
  // -1.0 => dNaN
  result = bit_cast<uint32_t, Float32>(Sqrt(bit_cast<Float32, uint32_t>(kMinusOne32)));
  EXPECT_EQ(result, kDefaultNan32);
}

TEST(FPU, Float64_Sqrt) {
  // +0.0 => +0.0
  uint64_t result = bit_cast<uint64_t, Float64>(Sqrt(bit_cast<Float64, uint64_t>(kPlusZero64)));
  EXPECT_EQ(result, kPlusZero64);
  // -0.0 => -0.0
  result = bit_cast<uint64_t, Float64>(Sqrt(bit_cast<Float64, uint64_t>(kMinusZero64)));
  EXPECT_EQ(result, kMinusZero64);
  // +1.0 => +1.0
  result = bit_cast<uint64_t, Float64>(Sqrt(bit_cast<Float64, uint64_t>(kPlusOne64)));
  EXPECT_EQ(result, kPlusOne64);
  // -1.0 => dNaN
  result = bit_cast<uint64_t, Float64>(Sqrt(bit_cast<Float64, uint64_t>(kMinusOne64)));
  EXPECT_EQ(result, kDefaultNan64);
}

}  // namespace

}  // namespace intrinsics

}  // namespace berberis
