/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <audio_utils/intrinsic_utils.h>

#include <gtest/gtest.h>

template <typename D>
class IntrisicUtilsTest : public ::testing::Test { };

// Basic intrinsic tests which are run on the simple scalar types (no NEON SIMD vector registers).
using FloatTypes = ::testing::Types<float, double>;
TYPED_TEST_CASE(IntrisicUtilsTest, FloatTypes);

TYPED_TEST(IntrisicUtilsTest, vadd) {
    constexpr TypeParam a = 0.25f;
    constexpr TypeParam b = 0.5f;
    constexpr TypeParam result = a + b;
    ASSERT_EQ(result, android::audio_utils::intrinsics::vadd(a, b));
}

TYPED_TEST(IntrisicUtilsTest, vdupn) {
    constexpr TypeParam value = 1.f;
    ASSERT_EQ(value, android::audio_utils::intrinsics::vdupn<TypeParam>(value));
}

TYPED_TEST(IntrisicUtilsTest, vld1) {
    constexpr TypeParam value = 2.f;
    ASSERT_EQ(value, android::audio_utils::intrinsics::vld1<TypeParam>(&value));
}

TYPED_TEST(IntrisicUtilsTest, vmla) {
    constexpr TypeParam a = 2.125f;
    constexpr TypeParam b = 2.25f;
    constexpr TypeParam c = 2.5f;
    constexpr TypeParam result = c + a * b;
    ASSERT_EQ(result, android::audio_utils::intrinsics::vmla(c, a, b));
}

TYPED_TEST(IntrisicUtilsTest, vmul) {
    constexpr TypeParam a = 2.25f;
    constexpr TypeParam b = 2.5f;
    constexpr TypeParam result = a * b;
    ASSERT_EQ(result, android::audio_utils::intrinsics::vmul(a, b));
}

TYPED_TEST(IntrisicUtilsTest, vneg) {
    constexpr TypeParam value = 3.125f;
    ASSERT_EQ(-value, android::audio_utils::intrinsics::vneg(value));
}

TYPED_TEST(IntrisicUtilsTest, vst1) {
    constexpr TypeParam value = 2.f;
    TypeParam destination = 1.f;
    android::audio_utils::intrinsics::vst1(
            &destination, android::audio_utils::intrinsics::vdupn<TypeParam>(value));
    ASSERT_EQ(value, destination);
}

TYPED_TEST(IntrisicUtilsTest, vsub) {
    constexpr TypeParam a = 1.25f;
    constexpr TypeParam b = 1.5f;
    constexpr TypeParam result = a - b;
    ASSERT_EQ(result, android::audio_utils::intrinsics::vsub(a, b));
}
