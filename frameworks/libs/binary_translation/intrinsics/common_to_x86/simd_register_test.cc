/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <utility>  // std::forward

#include "berberis/intrinsics/simd_register.h"

namespace berberis {

namespace {

constexpr __m128i kLhs = {0x5555'5555'5555'5555, 0x5555'5555'5555'5555};
constexpr __m128i kRhs = {0x3333'3333'3333'3333, 0x3333'3333'3333'3333};

TEST(SIMD_REGISTER, TestEq) {
  SIMD128Register lhs = kLhs;
  ASSERT_EQ(lhs, lhs);
  ASSERT_EQ(lhs, kLhs);
  ASSERT_EQ(kLhs, lhs);
}

TEST(SIMD_REGISTER, TestNe) {
  SIMD128Register lhs = kLhs;
  SIMD128Register rhs = kRhs;
  ASSERT_NE(lhs, rhs);
  ASSERT_NE(lhs, kRhs);
  ASSERT_NE(kLhs, rhs);
}

TEST(SIMD_REGISTER, TestAnd) {
  SIMD128Register lhs = kLhs;
  SIMD128Register rhs = kRhs;
  SIMD128Register result = __m128i{0x1111'1111'1111'1111, 0x1111'1111'1111'1111};
  ASSERT_EQ(lhs & rhs, result);
}

TEST(SIMD_REGISTER, TestNot) {
  SIMD128Register lhs = kLhs;
  SIMD128Register result = __m128i{-0x5555'5555'5555'5556, -0x5555'5555'5555'5556};
  ASSERT_EQ(~lhs, result);
}

TEST(SIMD_REGISTER, TestOr) {
  SIMD128Register lhs = kLhs;
  SIMD128Register rhs = kRhs;
  SIMD128Register result = __m128i{0x7777'7777'7777'7777, 0x7777'7777'7777'7777};
  ASSERT_EQ(lhs | rhs, result);
}

TEST(SIMD_REGISTER, TestXor) {
  SIMD128Register lhs = kLhs;
  SIMD128Register rhs = kRhs;
  SIMD128Register result = __m128i{0x6666'6666'6666'6666, 0x6666'6666'6666'6666};
  ASSERT_EQ(lhs ^ rhs, result);
}

}  // namespace

}  // namespace berberis
