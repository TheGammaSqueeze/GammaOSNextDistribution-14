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

#include "xmmintrin.h"

#include <array>
#include <cstdint>
#include <tuple>

#include "berberis/base/bit_util.h"
#include "berberis/intrinsics/vector_intrinsics.h"

namespace berberis::intrinsics {

namespace {

TEST(VectorIntrinsics, MakeBitmaskFromVl) {
  for (size_t vl = 0; vl < 128; ++vl) {
    ASSERT_EQ(MakeBitmaskFromVlForTests(vl), MakeBitmaskFromVl(vl));
  }
}

TEST(VectorIntrinsics, Make8bitMaskFromBitmask) {
  for (size_t mask = 0; mask < 131071; ++mask) {
    ASSERT_EQ(BitMaskToSimdMaskForTests<Int8>(mask), BitMaskToSimdMask<Int8>(mask));
    const auto [simd_mask] = BitMaskToSimdMask<Int8>(mask);
    ASSERT_EQ(SimdMaskToBitMaskForTests<Int8>(simd_mask), SimdMaskToBitMask<Int8>(simd_mask));
  }
}

TEST(VectorIntrinsics, Make16bitMaskFromBitmask) {
  for (size_t mask = 0; mask < 511; ++mask) {
    ASSERT_EQ(BitMaskToSimdMaskForTests<Int16>(mask), BitMaskToSimdMask<Int16>(mask));
    const auto [simd_mask] = BitMaskToSimdMask<Int16>(mask);
    ASSERT_EQ(SimdMaskToBitMaskForTests<Int16>(simd_mask), SimdMaskToBitMask<Int16>(simd_mask));
  }
}

TEST(VectorIntrinsics, Make32bitMaskFromBitmask) {
  for (size_t mask = 0; mask < 31; ++mask) {
    ASSERT_EQ(BitMaskToSimdMaskForTests<Int32>(mask), BitMaskToSimdMask<Int32>(mask));
    const auto [simd_mask] = BitMaskToSimdMask<Int32>(mask);
    ASSERT_EQ(SimdMaskToBitMaskForTests<Int32>(simd_mask), SimdMaskToBitMask<Int32>(simd_mask));
  }
}

TEST(VectorIntrinsics, Make64bitMaskFromBitmask) {
  for (size_t mask = 0; mask < 7; ++mask) {
    ASSERT_EQ(BitMaskToSimdMaskForTests<Int64>(mask), BitMaskToSimdMask<Int64>(mask));
    const auto [simd_mask] = BitMaskToSimdMask<Int64>(mask);
    ASSERT_EQ(SimdMaskToBitMaskForTests<Int64>(simd_mask), SimdMaskToBitMask<Int64>(simd_mask));
  }
}
template <typename ElementType>
void TestVidv() {
  for (size_t index = 0; index < 8; ++index) {
    ASSERT_EQ(VidvForTests<ElementType>(index), Vidv<ElementType>(index));
  }
}
TEST(VectorIntrinsics, Vidv) {
  TestVidv<Int8>();
  TestVidv<Int16>();
  TestVidv<Int32>();
  TestVidv<Int64>();
  TestVidv<UInt8>();
  TestVidv<UInt16>();
  TestVidv<UInt32>();
  TestVidv<UInt64>();
}
// Easily recognizable bit pattern for target register.
constexpr __m128i kUndisturbedResult = {0x5555'5555'5555'5555, 0x5555'5555'5555'5555};

template <auto kElement>
void TestVectorMaskedElementTo() {
  size_t max_mask = sizeof(kElement) == sizeof(uint8_t)    ? 131071
                    : sizeof(kElement) == sizeof(uint16_t) ? 511
                    : sizeof(kElement) == sizeof(uint32_t) ? 31
                                                           : 7;
  for (size_t mask = 0; mask < max_mask; ++mask) {
    const SIMD128Register src = kUndisturbedResult;
    const auto [simd_mask] = BitMaskToSimdMask<decltype(kElement)>(mask);
    ASSERT_EQ(VectorMaskedElementToForTests<kElement>(simd_mask, src),
              VectorMaskedElementTo<kElement>(simd_mask, src));
  }
}

TEST(VectorIntrinsics, VectorMaskedElementTo) {
  TestVectorMaskedElementTo<std::numeric_limits<int8_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<int8_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint8_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint8_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<int16_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<int16_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint16_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint16_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<int32_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<int32_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint32_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint32_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<int64_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<int64_t>::max()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint64_t>::min()>();
  TestVectorMaskedElementTo<std::numeric_limits<uint64_t>::max()>();
}

TEST(VectorIntrinsics, Vaddvv) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvv,
                    SIMD128Register arg2,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check) {
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)), 0, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      0,
                                                      16,
                                                      RawInt16{0xffff})),
        std::tuple{result_to_check});
  };
  Verify(Vaddvv<UInt8>,
         __v16qu{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0});
  Verify(Vaddvv<UInt8>,
         __v16qu{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255});
  Verify(Vaddvv<UInt16>,
         __v8hu{0, 1, 0, 1, 0, 1, 0, 1},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000});
  Verify(Vaddvv<UInt16>,
         __v8hu{1, 0, 1, 0, 1, 0, 1, 0},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff});
  Verify(Vaddvv<UInt32>,
         __v4su{0, 1, 0, 1},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0x0000'0000});
  Verify(Vaddvv<UInt32>,
         __v4su{1, 0, 1, 0},
         __v4su{0x0000'0000, 0xffff'ffff, 0x0000'0000, 0xffff'ffff});
  Verify(Vaddvv<UInt64>, __v2du{0, 1}, __v2du{0xffff'ffff'ffff'ffff, 0x0000'0000'0000'0000});
  Verify(Vaddvv<UInt64>, __v2du{1, 0}, __v2du{0x0000'0000'0000'0000, 0xffff'ffff'ffff'ffff});
}

TEST(VectorIntrinsics, Vaddvx) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvx,
                    SIMD128Register arg1,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check) {
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 0, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(
            kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 0, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
  };
  Verify(Vaddvx<UInt8>,
         __v16qu{254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0});
  Verify(Vaddvx<UInt8>,
         __v16qu{255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'fffe, 0xffff'ffff, 0xffff'fffe, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0x0000'0000});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'ffff, 0xffff'fffe, 0xffff'ffff, 0xffff'fffe},
         __v4su{0x0000'0000, 0xffff'ffff, 0x0000'0000, 0xffff'ffff});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'fffe, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x0000'0000'0000'0000});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'fffe},
         __v2du{0x0000'0000'0000'0000, 0xffff'ffff'ffff'ffff});
}

TEST(VectorIntrinsics, VlArgForVv) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvv,
                    SIMD128Register arg2,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check_agnostic,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_undisturbed) {
    constexpr size_t kHalfLen = sizeof(SIMD128Register) / sizeof(ElementType) / 2;
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)), 0, kHalfLen)),
              std::tuple{result_to_check_agnostic});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kUndisturbed>(
                  kUndisturbedResult, std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)), 0, kHalfLen)),
              std::tuple{result_to_check_undisturbed});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      0,
                                                      kHalfLen,
                                                      RawInt16{0xffff})),
        std::tuple{result_to_check_agnostic});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                         std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                         0,
                                                         kHalfLen,
                                                         RawInt16{0xffff})),
        std::tuple{result_to_check_agnostic});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      0,
                                                      kHalfLen,
                                                      RawInt16{0xffff})),
        std::tuple{result_to_check_undisturbed});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                         std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                         0,
                                                         kHalfLen,
                                                         RawInt16{0xffff})),
        std::tuple{result_to_check_undisturbed});
  };
  Verify(Vaddvv<UInt8>,
         __v16qu{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(Vaddvv<UInt8>,
         __v16qu{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(Vaddvv<UInt16>,
         __v8hu{0, 1, 0, 1, 0, 1, 0, 1},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvv<UInt16>,
         __v8hu{1, 0, 1, 0, 1, 0, 1, 0},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvv<UInt32>,
         __v4su{0, 1, 0, 1},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvv<UInt32>,
         __v4su{1, 0, 1, 0},
         __v4su{0x0000'0000, 0xffff'ffff, 0xffff'ffff, 0xffff'ffff},
         __v4su{0x0000'0000, 0xffff'ffff, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvv<UInt64>,
         __v2du{0, 1},
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x5555'5555'5555'5555});
  Verify(Vaddvv<UInt64>,
         __v2du{1, 0},
         __v2du{0x0000'0000'0000'0000, 0xffff'ffff'ffff'ffff},
         __v2du{0x0000'0000'0000'0000, 0x5555'5555'5555'5555});
}

TEST(VectorIntrinsics, VlArgForVx) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvx,
                    SIMD128Register arg1,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check_agnostic,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_undisturbed) {
    constexpr size_t kHalfLen = sizeof(SIMD128Register) / sizeof(ElementType) / 2;
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 0, kHalfLen)),
              std::tuple{result_to_check_agnostic});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kUndisturbed>(
                  kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 0, kHalfLen)),
              std::tuple{result_to_check_undisturbed});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kAgnostic,
                             InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                            std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                            0,
                                                            kHalfLen,
                                                            RawInt16{0xffff})),
              std::tuple{result_to_check_agnostic});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kAgnostic,
                             InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                               std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                               0,
                                                               kHalfLen,
                                                               RawInt16{0xffff})),
              std::tuple{result_to_check_agnostic});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kUndisturbed,
                             InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                            std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                            0,
                                                            kHalfLen,
                                                            RawInt16{0xffff})),
              std::tuple{result_to_check_undisturbed});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kUndisturbed,
                             InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                               std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                               0,
                                                               kHalfLen,
                                                               RawInt16{0xffff})),
              std::tuple{result_to_check_undisturbed});
  };
  Verify(Vaddvx<UInt8>,
         __v16qu{254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(Vaddvx<UInt8>,
         __v16qu{255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'fffe, 0xffff'ffff, 0xffff'fffe, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'ffff, 0xffff'fffe, 0xffff'ffff, 0xffff'fffe},
         __v4su{0x0000'0000, 0xffff'ffff, 0xffff'ffff, 0xffff'ffff},
         __v4su{0x0000'0000, 0xffff'ffff, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'fffe, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x5555'5555'5555'5555});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'fffe},
         __v2du{0x0000'0000'0000'0000, 0xffff'ffff'ffff'ffff},
         __v2du{0x0000'0000'0000'0000, 0x5555'5555'5555'5555});
}

TEST(VectorIntrinsics, VmaskArgForVvv) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvv,
                    SIMD128Register arg2,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_agnostic_agnostic,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_agnostic_undisturbed,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_undisturbed_agnostic,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_undisturbed_undisturbed) {
    constexpr size_t kHalfLen = sizeof(SIMD128Register) / sizeof(ElementType) / 2;
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      0,
                                                      kHalfLen,
                                                      RawInt16{0xfdda})),
        std::tuple{result_to_check_agnostic_agnostic});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                         std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                         0,
                                                         kHalfLen,
                                                         RawInt16{0xfdda})),
        std::tuple{result_to_check_agnostic_undisturbed});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      0,
                                                      kHalfLen,
                                                      RawInt16{0xfdda})),
        std::tuple{result_to_check_undisturbed_agnostic});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                         std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                         0,
                                                         kHalfLen,
                                                         RawInt16{0xfdda})),
        std::tuple{result_to_check_undisturbed_undisturbed});
  };
  Verify(
      Vaddvv<UInt8>,
      __v16qu{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
      __v16qu{255, 0, 255, 0, 255, 255, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{0x55, 0, 0x55, 0, 255, 0x55, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{255, 0, 255, 0, 255, 255, 255, 0, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55},
      __v16qu{0x55, 0, 0x55, 0, 255, 0x55, 255, 0, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(
      Vaddvv<UInt8>,
      __v16qu{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0},
      __v16qu{255, 255, 255, 255, 0, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{0x55, 255, 0x55, 255, 0, 0x55, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{255, 255, 255, 255, 0, 255, 0, 255, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55},
      __v16qu{
          0x55, 255, 0x55, 255, 0, 0x55, 0, 255, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(Vaddvv<UInt16>,
         __v8hu{0, 1, 0, 1, 0, 1, 0, 1},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0x5555, 0x0000, 0x5555, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0x5555, 0x5555, 0x5555, 0x5555},
         __v8hu{0x5555, 0x0000, 0x5555, 0x0000, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvv<UInt16>,
         __v8hu{1, 0, 1, 0, 1, 0, 1, 0},
         __v8hu{0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0x5555, 0xffff, 0x5555, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0xffff, 0xffff, 0xffff, 0xffff, 0x5555, 0x5555, 0x5555, 0x5555},
         __v8hu{0x5555, 0xffff, 0x5555, 0xffff, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvv<UInt32>,
         __v4su{0, 1, 0, 1},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0xffff'ffff},
         __v4su{0x5555'5555, 0x0000'0000, 0xffff'ffff, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0x5555'5555, 0x5555'5555},
         __v4su{0x5555'5555, 0x0000'0000, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvv<UInt32>,
         __v4su{1, 0, 1, 0},
         __v4su{0xffff'ffff, 0xffff'ffff, 0xffff'ffff, 0xffff'ffff},
         __v4su{0x5555'5555, 0xffff'ffff, 0xffff'ffff, 0xffff'ffff},
         __v4su{0xffff'ffff, 0xffff'ffff, 0x5555'5555, 0x5555'5555},
         __v4su{0x5555'5555, 0xffff'ffff, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvv<UInt64>,
         __v2du{0, 1},
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'ffff},
         __v2du{0x5555'5555'5555'5555, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x5555'5555'5555'5555},
         __v2du{0x5555'5555'5555'5555, 0x5555'5555'5555'5555});
  Verify(Vaddvv<UInt64>,
         __v2du{1, 0},
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'ffff},
         __v2du{0x5555'5555'5555'5555, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x5555'5555'5555'5555},
         __v2du{0x5555'5555'5555'5555, 0x5555'5555'5555'5555});
}

TEST(VectorIntrinsics, VmaskArgForVvx) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvx,
                    SIMD128Register arg1,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_agnostic_agnostic,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_agnostic_undisturbed,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_undisturbed_agnostic,
                    [[gnu::vector_size(16),
                      gnu::may_alias]] ElementType result_to_check_undisturbed_undisturbed) {
    constexpr size_t kHalfLen = sizeof(SIMD128Register) / sizeof(ElementType) / 2;
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kAgnostic,
                             InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                            std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                            0,
                                                            kHalfLen,
                                                            RawInt16{0xfdda})),
              std::tuple{result_to_check_agnostic_agnostic});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kAgnostic,
                             InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                               std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                               0,
                                                               kHalfLen,
                                                               RawInt16{0xfdda})),
              std::tuple{result_to_check_agnostic_undisturbed});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kUndisturbed,
                             InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                            std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                            0,
                                                            kHalfLen,
                                                            RawInt16{0xfdda})),
              std::tuple{result_to_check_undisturbed_agnostic});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>,
                             TailProcessing::kUndisturbed,
                             InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                               std::get<0>(Vaddvx(arg1, UInt8{1})),
                                                               0,
                                                               kHalfLen,
                                                               RawInt16{0xfdda})),
              std::tuple{result_to_check_undisturbed_undisturbed});
  };
  Verify(
      Vaddvx<UInt8>,
      __v16qu{254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255},
      __v16qu{255, 0, 255, 0, 255, 255, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{0x55, 0, 0x55, 0, 255, 0x55, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{255, 0, 255, 0, 255, 255, 255, 0, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55},
      __v16qu{0x55, 0, 0x55, 0, 255, 0x55, 255, 0, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(
      Vaddvx<UInt8>,
      __v16qu{255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254},
      __v16qu{255, 255, 255, 255, 0, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{0x55, 255, 0x55, 255, 0, 0x55, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255},
      __v16qu{255, 255, 255, 255, 0, 255, 0, 255, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55},
      __v16qu{
          0x55, 255, 0x55, 255, 0, 0x55, 0, 255, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0x5555, 0x0000, 0x5555, 0x0000, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0x5555, 0x5555, 0x5555, 0x5555},
         __v8hu{0x5555, 0x0000, 0x5555, 0x0000, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe},
         __v8hu{0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0x5555, 0xffff, 0x5555, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff},
         __v8hu{0xffff, 0xffff, 0xffff, 0xffff, 0x5555, 0x5555, 0x5555, 0x5555},
         __v8hu{0x5555, 0xffff, 0x5555, 0xffff, 0x5555, 0x5555, 0x5555, 0x5555});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'fffe, 0xffff'ffff, 0xffff'fffe, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0xffff'ffff},
         __v4su{0x5555'5555, 0x0000'0000, 0xffff'ffff, 0xffff'ffff},
         __v4su{0xffff'ffff, 0x0000'0000, 0x5555'5555, 0x5555'5555},
         __v4su{0x5555'5555, 0x0000'0000, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'ffff, 0xffff'fffe, 0xffff'ffff, 0xffff'fffe},
         __v4su{0xffff'ffff, 0xffff'ffff, 0xffff'ffff, 0xffff'ffff},
         __v4su{0x5555'5555, 0xffff'ffff, 0xffff'ffff, 0xffff'ffff},
         __v4su{0xffff'ffff, 0xffff'ffff, 0x5555'5555, 0x5555'5555},
         __v4su{0x5555'5555, 0xffff'ffff, 0x5555'5555, 0x5555'5555});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'fffe, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'ffff},
         __v2du{0x5555'5555'5555'5555, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x5555'5555'5555'5555},
         __v2du{0x5555'5555'5555'5555, 0x5555'5555'5555'5555});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'fffe},
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'ffff},
         __v2du{0x5555'5555'5555'5555, 0xffff'ffff'ffff'ffff},
         __v2du{0xffff'ffff'ffff'ffff, 0x5555'5555'5555'5555},
         __v2du{0x5555'5555'5555'5555, 0x5555'5555'5555'5555});
}

TEST(VectorIntrinsics, VstartArgVv) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvv,
                    SIMD128Register arg2,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check) {
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)), 1, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kUndisturbed>(
                  kUndisturbedResult, std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)), 1, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      1,
                                                      16,
                                                      RawInt16{0xffff})),
        std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                         std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                         1,
                                                         16,
                                                         RawInt16{0xffff})),
        std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kAgnostic>(kUndisturbedResult,
                                                      std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                      1,
                                                      16,
                                                      RawInt16{0xffff})),
        std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kUndisturbed>(kUndisturbedResult,
                                                         std::get<0>(Vaddvv(__m128i{-1, -1}, arg2)),
                                                         1,
                                                         16,
                                                         RawInt16{0xffff})),
        std::tuple{result_to_check});
  };
  Verify(Vaddvv<UInt8>,
         __v16qu{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
         __v16qu{0x55, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0});
  Verify(Vaddvv<UInt8>,
         __v16qu{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0},
         __v16qu{0x55, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255});
  Verify(Vaddvv<UInt16>,
         __v8hu{0, 1, 0, 1, 0, 1, 0, 1},
         __v8hu{0x5555, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000});
  Verify(Vaddvv<UInt16>,
         __v8hu{1, 0, 1, 0, 1, 0, 1, 0},
         __v8hu{0x5555, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff});
  Verify(Vaddvv<UInt32>,
         __v4su{0, 1, 0, 1},
         __v4su{0x5555'5555, 0x0000'0000, 0xffff'ffff, 0x0000'0000});
  Verify(Vaddvv<UInt32>,
         __v4su{1, 0, 1, 0},
         __v4su{0x5555'5555, 0xffff'ffff, 0x0000'0000, 0xffff'ffff});
  Verify(Vaddvv<UInt64>, __v2du{0, 1}, __v2du{0x5555'5555'5555'5555, 0x0000'0000'0000'0000});
  Verify(Vaddvv<UInt64>, __v2du{1, 0}, __v2du{0x5555'5555'5555'5555, 0xffff'ffff'ffff'ffff});
}

TEST(VectorIntrinsics, VstartArgVx) {
  auto Verify = []<typename ElementType>(
                    auto Vaddvx,
                    SIMD128Register arg1,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check) {
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 1, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kUndisturbed>(
                  kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 1, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(
            kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 1, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kUndisturbed>(
            kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 1, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kAgnostic>(
            kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 1, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kUndisturbed,
                       InactiveProcessing::kUndisturbed>(
            kUndisturbedResult, std::get<0>(Vaddvx(arg1, UInt8{1})), 1, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
  };
  Verify(Vaddvx<UInt8>,
         __v16qu{254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255},
         __v16qu{0x55, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0});
  Verify(Vaddvx<UInt8>,
         __v16qu{255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254, 255, 254},
         __v16qu{0x55, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff},
         __v8hu{0x5555, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000});
  Verify(Vaddvx<UInt16>,
         __v8hu{0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe, 0xffff, 0xfffe},
         __v8hu{0x5555, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'fffe, 0xffff'ffff, 0xffff'fffe, 0xffff'ffff},
         __v4su{0x5555'5555, 0x0000'0000, 0xffff'ffff, 0x0000'0000});
  Verify(Vaddvx<UInt32>,
         __v4su{0xffff'ffff, 0xffff'fffe, 0xffff'ffff, 0xffff'fffe},
         __v4su{0x5555'5555, 0xffff'ffff, 0x0000'0000, 0xffff'ffff});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'fffe, 0xffff'ffff'ffff'ffff},
         __v2du{0x5555'5555'5555'5555, 0x0000'0000'0000'0000});
  Verify(Vaddvx<UInt64>,
         __v2du{0xffff'ffff'ffff'ffff, 0xffff'ffff'ffff'fffe},
         __v2du{0x5555'5555'5555'5555, 0xffff'ffff'ffff'ffff});
}

TEST(VectorIntrinsics, Vsubvv) {
  auto Verify = []<typename ElementType>(
                    auto Vsubvv,
                    SIMD128Register arg2,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check) {
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vsubvv(__m128i{0, 0}, arg2)), 0, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(
            kUndisturbedResult, std::get<0>(Vsubvv(__m128i{0, 0}, arg2)), 0, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
  };
  Verify(Vsubvv<UInt8>,
         __v16qu{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255});
  Verify(Vsubvv<UInt8>,
         __v16qu{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0});
  Verify(Vsubvv<UInt16>,
         __v8hu{0, 1, 0, 1, 0, 1, 0, 1},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff});
  Verify(Vsubvv<UInt16>,
         __v8hu{1, 0, 1, 0, 1, 0, 1, 0},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000});
  Verify(Vsubvv<UInt32>,
         __v4su{0, 1, 0, 1},
         __v4su{0x0000'0000, 0xffff'ffff, 0x0000'0000, 0xffff'ffff});
  Verify(Vsubvv<UInt64>, __v2du{0, 1}, __v2du{0x0000'0000'0000'0000, 0xffff'ffff'ffff'ffff});
  Verify(Vsubvv<UInt64>, __v2du{1, 0}, __v2du{0xffff'ffff'ffff'ffff, 0x0000'0000'0000'0000});
}

TEST(VectorIntrinsics, Vsubvx) {
  auto Verify = []<typename ElementType>(
                    auto Vsubvx,
                    SIMD128Register arg1,
                    [[gnu::vector_size(16), gnu::may_alias]] ElementType result_to_check) {
    ASSERT_EQ((VectorMasking<Wrapping<ElementType>, TailProcessing::kAgnostic>(
                  kUndisturbedResult, std::get<0>(Vsubvx(arg1, UInt8{1})), 0, 16)),
              std::tuple{result_to_check});
    ASSERT_EQ(
        (VectorMasking<Wrapping<ElementType>,
                       TailProcessing::kAgnostic,
                       InactiveProcessing::kAgnostic>(
            kUndisturbedResult, std::get<0>(Vsubvx(arg1, UInt8{1})), 0, 16, RawInt16{0xffff})),
        std::tuple{result_to_check});
  };
  Verify(Vsubvx<UInt8>,
         __v16qu{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0},
         __v16qu{0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255});
  Verify(Vsubvx<UInt8>,
         __v16qu{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
         __v16qu{255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0});
  Verify(Vsubvx<UInt16>,
         __v8hu{1, 0, 1, 0, 1, 0, 1, 0},
         __v8hu{0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff});
  Verify(Vsubvx<UInt16>,
         __v8hu{0, 1, 0, 1, 0, 1, 0, 1},
         __v8hu{0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000, 0xffff, 0x0000});
  Verify(Vsubvx<UInt32>,
         __v4su{1, 0, 1, 0},
         __v4su{0x0000'0000, 0xffff'ffff, 0x0000'0000, 0xffff'ffff});
  Verify(Vsubvx<UInt32>,
         __v4su{0, 1, 0, 1},
         __v4su{0xffff'ffff, 0x0000'0000, 0xffff'ffff, 0x0000'0000});
  Verify(Vsubvx<UInt64>, __v2du{1, 0}, __v2du{0x0000'0000'0000'0000, 0xffff'ffff'ffff'ffff});
  Verify(Vsubvx<UInt64>, __v2du{0, 1}, __v2du{0xffff'ffff'ffff'ffff, 0x0000'0000'0000'0000});
}

}  // namespace

}  // namespace berberis::intrinsics
