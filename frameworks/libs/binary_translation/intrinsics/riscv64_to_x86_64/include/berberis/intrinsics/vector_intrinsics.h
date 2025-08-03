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

#ifndef RISCV64_TO_X86_64_BERBERIS_INTRINSICS_VECTOR_INTRINSICS_H_
#define RISCV64_TO_X86_64_BERBERIS_INTRINSICS_VECTOR_INTRINSICS_H_

#include <tmmintrin.h>
#include <xmmintrin.h>

#include "berberis/base/dependent_false.h"
#include "berberis/intrinsics/common/intrinsics.h"
#include "berberis/intrinsics/macro_assembler_constants_pool.h"
#include "berberis/intrinsics/simd_register.h"

// Define function to use in host-agnostic code.

namespace berberis::intrinsics {

[[nodiscard]] inline std::tuple<SIMD128Register> MakeBitmaskFromVl(size_t vl) {
  return {_mm_loadu_si128(reinterpret_cast<__m128i_u const*>(
      bit_cast<const uint8_t*>(static_cast<uintptr_t>(constants_pool::kBitMaskTable)) +
      (vl & 7U) * 32 + 16 - ((vl & (~7ULL)) >> 3)))};
}

template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> BitMaskToSimdMask(size_t mask) {
  SIMD128Register result;
  if constexpr (sizeof(ElementType) == sizeof(Int8)) {
    uint64_t low_mask = bit_cast<const uint64_t*>(
        static_cast<uintptr_t>(constants_pool::kBitMaskTo8bitMask))[mask & 0xff];
    uint64_t high_mask = bit_cast<const uint64_t*>(
        static_cast<uintptr_t>(constants_pool::kBitMaskTo8bitMask))[(mask >> 8) & 0xff];
    result.Set(low_mask, 0);
    result.Set(high_mask, 1);
  } else {
    __m128i simd_half_mask;
    if constexpr (sizeof(ElementType) == sizeof(Int16)) {
      uint64_t half_size_mask = bit_cast<const uint64_t*>(
          static_cast<uintptr_t>(constants_pool::kBitMaskTo8bitMask))[mask & 0xff];
      simd_half_mask =
          _mm_unpacklo_epi8(_mm_cvtsi64_si128(half_size_mask), _mm_cvtsi64_si128(half_size_mask));
    } else if constexpr (sizeof(ElementType) == sizeof(Int32)) {
      uint64_t half_size_mask = bit_cast<const uint64_t*>(
          static_cast<uintptr_t>(constants_pool::kBitMaskTo16bitMask))[mask & 0xf];
      simd_half_mask =
          _mm_unpacklo_epi16(_mm_cvtsi64_si128(half_size_mask), _mm_cvtsi64_si128(half_size_mask));
    } else if constexpr (sizeof(ElementType) == sizeof(Int64)) {
      uint64_t half_size_mask = bit_cast<const uint64_t*>(
          static_cast<uintptr_t>(constants_pool::kBitMaskTo32bitMask))[mask & 0x3];
      simd_half_mask =
          _mm_unpacklo_epi32(_mm_cvtsi64_si128(half_size_mask), _mm_cvtsi64_si128(half_size_mask));
    } else {
      static_assert(kDependentTypeFalse<ElementType>, "Unsupported vector element type");
    }
    result.Set(simd_half_mask);
  }
  return {result};
}

template <auto kElement>
[[nodiscard]] inline std::tuple<SIMD128Register> VectorMaskedElementTo(SIMD128Register simd_mask,
                                                                       SIMD128Register src) {
  using ElementType = decltype(kElement);
  if constexpr (kElement == ElementType{0}) {
    return {src & simd_mask};
  } else if constexpr (kElement == static_cast<ElementType>(~ElementType{0})) {
    return {src | ~simd_mask};
  } else {
    return {(*bit_cast<const SIMD128Register*>(
                 static_cast<uintptr_t>(constants_pool::kVectorConst<kElement>)) &
             ~simd_mask) |
            (src & simd_mask)};
  }
}

#ifdef __SSSE3__
template <typename ElementType>
[[nodiscard]] inline std::tuple<
    std::conditional_t<sizeof(ElementType) == sizeof(Int8), RawInt16, RawInt8>>
SimdMaskToBitMask(SIMD128Register simd_mask) {
  if constexpr (sizeof(ElementType) == sizeof(Int8)) {
    return {RawInt16{static_cast<uint16_t>(_mm_movemask_epi8(simd_mask.Get<__m128i>()))}};
  } else {
    static_assert(sizeof(ElementType) == sizeof(Int16) || sizeof(ElementType) == sizeof(Int32) ||
                  sizeof(ElementType) == sizeof(Int64));
    const __m128i kPMovmskXToPMovmskb = *bit_cast<const __m128i*>(static_cast<uintptr_t>(
        sizeof(ElementType) == sizeof(Int16)   ? constants_pool::kPMovmskwToPMovmskb
        : sizeof(ElementType) == sizeof(Int32) ? constants_pool::kPMovmskdToPMovmskb
                                               : constants_pool::kPMovmskqToPMovmskb));
    return {RawInt8{static_cast<uint8_t>(
        _mm_movemask_epi8(_mm_shuffle_epi8(simd_mask.Get<__m128i>(), kPMovmskXToPMovmskb)))}};
  }
}
#endif

template <typename ElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vidv(size_t index) {
  static_assert(sizeof(ElementType) == sizeof(UInt8) || sizeof(ElementType) == sizeof(UInt16) ||
                sizeof(ElementType) == sizeof(UInt32) || sizeof(ElementType) == sizeof(UInt64));
  const int32_t kVid = (sizeof(ElementType) == sizeof(UInt8))    ? constants_pool::kVid8Bit
                       : (sizeof(ElementType) == sizeof(UInt16)) ? constants_pool::kVid16Bit
                       : (sizeof(ElementType) == sizeof(UInt32)) ? constants_pool::kVid32Bit
                                                                 : constants_pool::kVid64Bit;
  const SIMD128Register* const kVidPtr =
      bit_cast<const SIMD128Register*>(static_cast<uintptr_t>(kVid));
  return kVidPtr[index];
}

}  // namespace berberis::intrinsics

// Include host-agnostic code.

#include "berberis/intrinsics/riscv64/vector_intrinsics.h"

#endif  // RISCV64_TO_X86_64_BERBERIS_INTRINSICS_VECTOR_INTRINSICS_H_
