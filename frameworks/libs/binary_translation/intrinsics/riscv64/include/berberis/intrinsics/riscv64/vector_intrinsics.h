/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_RISCV64_VECTOR_INTRINSICS_H_
#define BERBERIS_INTRINSICS_RISCV64_VECTOR_INTRINSICS_H_

#include <algorithm>
#include <climits>  // CHAR_BIT
#include <cstdint>
#include <limits>
#include <tuple>
#include <type_traits>

#include "berberis/base/bit_util.h"
#include "berberis/base/dependent_false.h"
#include "berberis/intrinsics/intrinsics.h"        // PreferredIntrinsicsImplementation
#include "berberis/intrinsics/intrinsics_float.h"  // Float32/Float64
#include "berberis/intrinsics/simd_register.h"
#include "berberis/intrinsics/type_traits.h"

namespace berberis::intrinsics {

enum class TailProcessing {
  kUndisturbed = 0,
  kAgnostic = 1,
};

enum class InactiveProcessing {
  kUndisturbed = 0,
  kAgnostic = 1,
};

enum class NoInactiveProcessing {
  kNoInactiveProcessing = 0,
};

template <typename ElementType>
[[nodiscard]] inline std::tuple<NoInactiveProcessing> FullMaskForRegister(NoInactiveProcessing) {
  return {NoInactiveProcessing{}};
}

template <typename ElementType>
[[nodiscard]] inline std::tuple<
    std::conditional_t<sizeof(ElementType) == sizeof(Int8), RawInt16, RawInt8>>
FullMaskForRegister(SIMD128Register) {
  if constexpr (sizeof(ElementType) == sizeof(uint8_t)) {
    return {{0xffff}};
  } else if constexpr (sizeof(ElementType) == sizeof(uint16_t)) {
    return {{0xff}};
  } else if constexpr (sizeof(ElementType) == sizeof(uint32_t)) {
    return {{0xf}};
  } else if constexpr (sizeof(ElementType) == sizeof(uint64_t)) {
    return {{0x3}};
  } else {
    static_assert(kDependentTypeFalse<ElementType>, "Unsupported vector element type");
  }
}

template <typename ElementType>
[[nodiscard]] inline std::tuple<NoInactiveProcessing> MaskForRegisterInSequence(
    NoInactiveProcessing,
    size_t) {
  return {NoInactiveProcessing{}};
}

template <typename ElementType>
[[nodiscard]] inline std::tuple<
    std::conditional_t<sizeof(ElementType) == sizeof(Int8), RawInt16, RawInt8>>
MaskForRegisterInSequence(SIMD128Register mask, size_t register_in_sequence) {
  if constexpr (sizeof(ElementType) == sizeof(uint8_t)) {
    return {mask.Get<RawInt16>(register_in_sequence)};
  } else if constexpr (sizeof(ElementType) == sizeof(uint16_t)) {
    return {mask.Get<RawInt8>(register_in_sequence)};
  } else if constexpr (sizeof(ElementType) == sizeof(uint32_t)) {
    return {RawInt8{TruncateTo<UInt8>(mask.Get<UInt32>(0) >> UInt64(register_in_sequence * 4)) &
                    UInt8{0b1111}}};
  } else if constexpr (sizeof(ElementType) == sizeof(uint64_t)) {
    return {RawInt8{TruncateTo<UInt8>(mask.Get<UInt32>(0) >> UInt64(register_in_sequence * 2)) &
                    UInt8{0b11}}};
  } else {
    static_assert(kDependentTypeFalse<ElementType>, "Unsupported vector element type");
  }
}

// Naïve implementation for tests.  Also used on not-x86 platforms.
[[nodiscard]] inline std::tuple<SIMD128Register> MakeBitmaskFromVlForTests(size_t vl) {
  if (vl == 128) {
    return {SIMD128Register(__int128(0))};
  } else {
    return {SIMD128Register((~__int128(0)) << vl)};
  }
}

#ifndef __x86_64__
[[nodiscard]] inline std::tuple<SIMD128Register> MakeBitmaskFromVl(size_t vl) {
  return {MakeBitmaskFromVlForTests(vl)};
}
#endif

template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> MakeBitmaskFromVl(size_t vl) {
  return MakeBitmaskFromVl(vl * sizeof(ElementType) * CHAR_BIT);
}

// Naïve implementation for tests.  Also used on not-x86 platforms.
template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> BitMaskToSimdMaskForTests(size_t mask) {
  constexpr ElementType kZeroValue = ElementType{0};
  constexpr ElementType kFillValue = ~ElementType{0};
  SIMD128Register result;
  for (size_t index = 0; index < 16 / sizeof(ElementType); ++index) {
    size_t bit = 1 << index;
    if (mask & bit) {
      result.Set(kFillValue, index);
    } else {
      result.Set(kZeroValue, index);
    }
  }
  return {result};
}

#ifndef __x86_64__
template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> BitMaskToSimdMask(size_t mask) {
  return {BitMaskToSimdMaskForTests<ElementType>(mask)};
}
#endif

// Naïve implementation for tests.  Also used on not-x86 platforms.
template <typename ElementType>
[[nodiscard]] inline std::tuple<
    std::conditional_t<sizeof(ElementType) == sizeof(Int8), RawInt16, RawInt8>>
SimdMaskToBitMaskForTests(SIMD128Register simd_mask) {
  using ResultType = std::conditional_t<sizeof(ElementType) == sizeof(Int8), UInt16, UInt8>;
  ResultType mask{0};
  constexpr ResultType kElementsCount{static_cast<uint8_t>(16 / sizeof(ElementType))};
  for (ResultType index{0}; index < kElementsCount; index += ResultType{1}) {
    if (simd_mask.Get<ElementType>(static_cast<int>(index)) != ElementType{0}) {
      mask |= ResultType{1} << ResultType{index};
    }
  }
  return mask;
}

#ifndef __SSSE3__
template <typename ElementType>
[[nodiscard]] inline std::tuple<
    std::conditional_t<sizeof(ElementType) == sizeof(Int8), RawInt16, RawInt8>>
SimdMaskToBitMask(SIMD128Register simd_mask) {
  return SimdMaskToBitMaskForTests<ElementType>(simd_mask);
}
#endif

template <auto kElement>
[[nodiscard]] inline std::tuple<SIMD128Register> VectorMaskedElementToForTests(
    SIMD128Register simd_mask,
    SIMD128Register result) {
  using ElementType = decltype(kElement);
  constexpr int kElementsCount = static_cast<int>(16 / sizeof(ElementType));
  for (int index = 0; index < kElementsCount; ++index) {
    if (!simd_mask.Get<ElementType>(index)) {
      result.Set(kElement, index);
    }
  }
  return result;
}

#ifndef __x86_64__
template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> VectorMaskedElementTo(SIMD128Register simd_mask,
                                                                       SIMD128Register result) {
  return VectorMaskedElementToForTests(simd_mask, result);
}
#endif

template <typename ElementType>
[[nodiscard]] inline ElementType VectorElement(SIMD128Register src, int index) {
  return src.Get<ElementType>(index);
}

template <typename ElementType>
[[nodiscard]] inline ElementType VectorElement(ElementType src, int) {
  return src;
}

template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> VMovTopHalfToBottom(SIMD128Register src) {
  return {SIMD128Register{src.Get<uint64_t>(1)}};
}

template <typename ElementType>
[[nodiscard]] inline std::tuple<SIMD128Register> VMergeBottomHalfToTop(SIMD128Register bottom,
                                                                       SIMD128Register top) {
  SIMD128Register result{bottom};
  result.Set<uint64_t>(top.Get<uint64_t>(0), 1);
  return result;
}

template <typename ElementType, TailProcessing vta, NoInactiveProcessing = NoInactiveProcessing{}>
[[nodiscard]] inline std::tuple<SIMD128Register> VectorMasking(
    SIMD128Register dest,
    SIMD128Register result,
    int vstart,
    int vl,
    NoInactiveProcessing /*mask*/ = NoInactiveProcessing{}) {
  constexpr int kElementsCount = static_cast<int>(16 / sizeof(ElementType));
  if (vstart < 0) {
    vstart = 0;
  }
  if (vl < 0) {
    vl = 0;
  }
  if (vl > kElementsCount) {
    vl = kElementsCount;
  }
  if (vstart == 0) [[likely]] {
    if (vl == 16) [[likely]] {
      return result;
    }
    const auto [tail_bitmask] = MakeBitmaskFromVl<ElementType>(vl);
    if constexpr (vta == TailProcessing::kAgnostic) {
      dest = result | tail_bitmask;
    } else {
      dest = (dest & tail_bitmask) | (result & ~tail_bitmask);
    }
  } else if (vstart > vl) [[unlikely]] {
    if (vl == 16) [[likely]] {
      return dest;
    }
    if constexpr (vta == TailProcessing::kAgnostic) {
      const auto [tail_bitmask] = MakeBitmaskFromVl<ElementType>(vl);
      dest |= tail_bitmask;
    }
  } else {
    const auto [start_bitmask] = MakeBitmaskFromVl<ElementType>(vstart);
    const auto [tail_bitmask] = MakeBitmaskFromVl<ElementType>(vl);
    if constexpr (vta == TailProcessing::kAgnostic) {
      dest = (dest & ~start_bitmask) | (result & start_bitmask) | tail_bitmask;
    } else {
      dest = (dest & (~start_bitmask | tail_bitmask)) | (result & start_bitmask & ~tail_bitmask);
    }
  }
  return {dest};
}

template <typename ElementType,
          TailProcessing vta,
          auto vma = NoInactiveProcessing{},
          typename MaskType = NoInactiveProcessing>
[[nodiscard]] inline std::tuple<SIMD128Register> VectorMasking(
    SIMD128Register dest,
    SIMD128Register result,
    SIMD128Register result_mask,
    int vstart,
    int vl,
    MaskType mask = NoInactiveProcessing{}) {
  static_assert((std::is_same_v<decltype(vma), NoInactiveProcessing> &&
                 std::is_same_v<MaskType, NoInactiveProcessing>) ||
                (std::is_same_v<decltype(vma), InactiveProcessing> &&
                 (std::is_same_v<MaskType, RawInt8> || std::is_same_v<MaskType, RawInt16>)));
  if constexpr (std::is_same_v<decltype(vma), InactiveProcessing>) {
    const auto [simd_mask] =
        BitMaskToSimdMask<ElementType>(static_cast<typename MaskType::BaseType>(mask));
    if (vma == InactiveProcessing::kAgnostic) {
      result |= ~simd_mask;
    } else {
      result = (result & simd_mask) | (result_mask & ~simd_mask);
    }
  }
  return VectorMasking<ElementType, vta>(dest, result, vstart, vl);
}

template <typename ElementType, TailProcessing vta, InactiveProcessing vma, typename MaskType>
[[nodiscard]] inline std::tuple<SIMD128Register> VectorMasking(SIMD128Register dest,
                                                               SIMD128Register result,
                                                               int vstart,
                                                               int vl,
                                                               MaskType mask) {
  return VectorMasking<ElementType, vta, vma>(dest,
                                              result,
                                              /*result_mask=*/dest,
                                              vstart,
                                              vl,
                                              mask);
}

// TODO(b/260725458): Pass lambda as template argument after C++20 would become available.
template <typename ElementType, typename Lambda, typename... ParameterType>
inline std::tuple<SIMD128Register> VectorProcessing(Lambda lambda, ParameterType... parameters) {
  static_assert(((std::is_same_v<ParameterType, SIMD128Register> ||
                  std::is_same_v<ParameterType, ElementType>)&&...));
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(16 / sizeof(ElementType));
  for (int index = 0; index < kElementsCount; ++index) {
    result.Set(lambda(VectorElement<ElementType>(parameters, index)...), index);
  }
  return result;
}

// 2*SEW = SEW op SEW
// TODO(b/260725458): Pass lambda as template argument after C++20 would become available.
template <typename ElementType, typename Lambda, typename... ParameterType>
inline std::tuple<SIMD128Register> VectorArithmeticWidenvv(Lambda lambda,
                                                           ParameterType... parameters) {
  static_assert(((std::is_same_v<ParameterType, SIMD128Register> ||
                  std::is_same_v<ParameterType, ElementType>)&&...));
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(8 / sizeof(ElementType));
  for (int index = 0; index < kElementsCount; ++index) {
    result.Set(lambda(Widen(VectorElement<ElementType>(parameters, index))...), index);
  }
  return result;
}

template <typename ElementType>
SIMD128Register VectorExtend(SIMD128Register src) {
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(8 / sizeof(ElementType));
  for (int index = 0; index < kElementsCount; ++index) {
    result.Set(Widen(VectorElement<ElementType>(src, index)), index);
  }
  return result;
}

template <typename ElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vextf2(SIMD128Register src) {
  using SourceElementType = decltype(Narrow(ElementType{0}));
  return {VectorExtend<SourceElementType>(src)};
}

template <typename ElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vextf4(SIMD128Register src) {
  using WideSourceElementType = decltype(Narrow(ElementType{0}));
  using SourceElementType = decltype(Narrow(WideSourceElementType{0}));
  return {VectorExtend<WideSourceElementType>(VectorExtend<SourceElementType>(src))};
}

template <typename ElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vextf8(SIMD128Register src) {
  using WideWideSourceElementType = decltype(Narrow(ElementType{0}));
  return {
      VectorExtend<WideWideSourceElementType>(std::get<0>(Vextf4<WideWideSourceElementType>(src)))};
}

// SEW = 2*SEW op SEW
// TODO(b/260725458): Pass lambda as template argument after C++20 would become available.
template <typename ElementType, typename Lambda, typename... ParameterType>
inline std::tuple<SIMD128Register> VectorArithmeticNarrowwv(Lambda lambda,
                                                            ParameterType... parameters) {
  static_assert(((std::is_same_v<ParameterType, SIMD128Register> ||
                  std::is_same_v<ParameterType, ElementType>)&&...));
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(8 / sizeof(ElementType));
  for (int index = 0; index < kElementsCount; ++index) {
    auto [src1, src2] = std::tuple{parameters...};
    result.Set(Narrow(lambda(VectorElement<decltype(Widen(ElementType{0}))>(src1, index),
                             Widen(VectorElement<ElementType>(src2, index)))),
               index);
  }
  return result;
}

template <typename ElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> VidvForTests(size_t index) {
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(16 / sizeof(ElementType));
  ElementType element = {static_cast<typename ElementType::BaseType>(index * kElementsCount)};
  for (int index = 0; index < kElementsCount; ++index) {
    result.Set(element, index);
    element += ElementType{1};
  }
  return result;
}

// Handles "slide up" for a single destination register. Effectively copies the last offset elements
// in [kElementsCount - offset, kElementsCount) of src1 followed by the first [0, kElementsCount -
// offset) elements of src2 into the result.
//
// This leaves result looking like
//
//     result = {
//         src1[kElementsCount-offset+0],
//         src1[kElementsCount-offset+1],
//         ...,
//         src1[kElementsCount-offset+(offset-1),
//         src2[0],
//         src2[1],
//         ...,
//         src2[kElementsCount-offset-1]
//     };
template <typename ElementType>
inline std::tuple<SIMD128Register> VectorSlideUp(size_t offset,
                                                 SIMD128Register src1,
                                                 SIMD128Register src2) {
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(16 / sizeof(ElementType));
  CHECK_LT(offset, kElementsCount);
  for (size_t index = 0; index < offset; ++index) {
    result.Set(VectorElement<ElementType>(src1, kElementsCount - offset + index), index);
  }
  for (size_t index = offset; index < kElementsCount; ++index) {
    result.Set(VectorElement<ElementType>(src2, index - offset), index);
  }
  return result;
}

// Handles "slide down" for a single destination register. Effectively copies the elements in
// [offset, kElementsCount) of src1 followed by the [0, kElementsCount - offset) elements of src2
// into the result.
//
// This leaves result looking like
//
//     result = {
//         [0] = src1[offset+0],
//         [1] = src1[offset+1],
//         ...,
//         [kElementsCount-offset-1] = src1[kElementsCount-1],
//         [kElementsCount-offset] = src2[0],
//         [kElementsCount-offset+1] = src2[1],
//         ...,
//         [kElementsCount-offset+(offset-1)] = src2[kElementsCount-offset-1]
//     };
template <typename ElementType>
inline std::tuple<SIMD128Register> VectorSlideDown(size_t offset,
                                                   SIMD128Register src1,
                                                   SIMD128Register src2) {
  SIMD128Register result;
  constexpr int kElementsCount = static_cast<int>(16 / sizeof(ElementType));
  CHECK_LT(offset, kElementsCount);
  for (size_t index = 0; index < kElementsCount - offset; ++index) {
    result.Set(VectorElement<ElementType>(src1, offset + index), index);
  }
  for (size_t index = kElementsCount - offset; index < kElementsCount; ++index) {
    result.Set(VectorElement<ElementType>(src2, index - (kElementsCount - offset)), index);
  }
  return result;
}

#ifndef __x86_64__
template <typename ElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vidv(size_t index) {
  return VidvForTests<ElementType>(index);
}
#endif

template <enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vmsifm(SIMD128Register simd_src) {
  Int128 src = simd_src.Get<Int128>();
  return {(src - Int128{1}) ^ src};
}

template <enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vmsbfm(SIMD128Register simd_src) {
  Int128 src = simd_src.Get<Int128>();
  if (src == Int128{0}) {
    return {~Int128{0}};
  }
  return {std::get<0>(Vmsifm(simd_src)).Get<Int128>() >> Int128{1}};
}

template <enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vmsofm(SIMD128Register simd_src) {
  return {std::get<0>(Vmsbfm(simd_src)) ^ std::get<0>(Vmsifm(simd_src))};
}

template <typename TargetElementType,
          typename SourceElementType,
          enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
inline std::tuple<SIMD128Register> Vfcvtv(int8_t rm, int8_t frm, SIMD128Register src) {
  SIMD128Register result;
  constexpr int kElementsCount =
      std::min(static_cast<int>(sizeof(SIMD128Register) / sizeof(TargetElementType)),
               static_cast<int>(sizeof(SIMD128Register) / sizeof(SourceElementType)));
  for (int index = 0; index < kElementsCount; ++index) {
    if constexpr (std::is_integral_v<TargetElementType>) {
      result.Set(std::get<0>(FCvtFloatToInteger<TargetElementType, SourceElementType>(
                     rm, frm, src.Get<SourceElementType>(index))),
                 index);
    } else if constexpr (std::is_integral_v<SourceElementType>) {
      result.Set(std::get<0>(FCvtIntegerToFloat<TargetElementType, SourceElementType>(
                     rm, frm, src.Get<SourceElementType>(index))),
                 index);
    } else {
      result.Set(std::get<0>(FCvtFloatToFloat<TargetElementType, SourceElementType>(
                     rm, frm, src.Get<SourceElementType>(index))),
                 index);
    }
  }
  return result;
}

#define DEFINE_ARITHMETIC_PARAMETERS_OR_ARGUMENTS(...) __VA_ARGS__
#define DEFINE_ARITHMETIC_INTRINSIC(Name, arithmetic, parameters, arguments)                      \
                                                                                                  \
  template <typename ElementType,                                                                 \
            enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>       \
  inline std::tuple<SIMD128Register> Name(DEFINE_ARITHMETIC_PARAMETERS_OR_ARGUMENTS parameters) { \
    return VectorProcessing<ElementType>(                                                         \
        [](auto... args) {                                                                        \
          static_assert((std::is_same_v<decltype(args), ElementType> && ...));                    \
          arithmetic;                                                                             \
        },                                                                                        \
        DEFINE_ARITHMETIC_PARAMETERS_OR_ARGUMENTS arguments);                                     \
  }

#define DEFINE_1OP_ARITHMETIC_INTRINSIC_M(name, ...) \
  DEFINE_ARITHMETIC_INTRINSIC(V##name##m, return ({ __VA_ARGS__; });, (Int128 src), (src))
#define DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(name, ...)                 \
  DEFINE_ARITHMETIC_INTRINSIC(V##name##vs, return ({ __VA_ARGS__; }); \
                              , (ElementType src1, ElementType src2), (src1, src2))

#define DEFINE_W_ARITHMETIC_INTRINSIC(Name, Pattern, arithmetic, parameters, arguments)            \
                                                                                                   \
  template <typename ElementType,                                                                  \
            enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>        \
  inline std::tuple<SIMD128Register> Name(DEFINE_ARITHMETIC_PARAMETERS_OR_ARGUMENTS parameters) {  \
    return VectorArithmetic##Pattern<ElementType>(                                                 \
        [](auto... args) {                                                                         \
          static_assert((std::is_same_v<decltype(args), decltype(Widen(ElementType{0}))> && ...)); \
          arithmetic;                                                                              \
        },                                                                                         \
        DEFINE_ARITHMETIC_PARAMETERS_OR_ARGUMENTS arguments);                                      \
  }

#define DEFINE_1OP_ARITHMETIC_INTRINSIC_V(name, ...) \
  DEFINE_ARITHMETIC_INTRINSIC(V##name##v, return ({ __VA_ARGS__; });, (SIMD128Register src), (src))
#define DEFINE_1OP_ARITHMETIC_INTRINSIC_X(name, ...) \
  DEFINE_ARITHMETIC_INTRINSIC(V##name##x, return ({ __VA_ARGS__; });, (ElementType src), (src))
#define DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(name, ...)                 \
  DEFINE_ARITHMETIC_INTRINSIC(V##name##vv, return ({ __VA_ARGS__; }); \
                              , (SIMD128Register src1, SIMD128Register src2), (src1, src2))
#define DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(name, ...)                 \
  DEFINE_ARITHMETIC_INTRINSIC(V##name##vx, return ({ __VA_ARGS__; }); \
                              , (SIMD128Register src1, ElementType src2), (src1, src2))
#define DEFINE_3OP_ARITHMETIC_INTRINSIC_VV(name, ...) \
  DEFINE_ARITHMETIC_INTRINSIC(                        \
      V##name##vv, return ({ __VA_ARGS__; });         \
      , (SIMD128Register src1, SIMD128Register src2, SIMD128Register src3), (src1, src2, src3))
#define DEFINE_3OP_ARITHMETIC_INTRINSIC_VX(name, ...) \
  DEFINE_ARITHMETIC_INTRINSIC(                        \
      V##name##vx, return ({ __VA_ARGS__; });         \
      , (SIMD128Register src1, ElementType src2, SIMD128Register src3), (src1, src2, src3))

#define DEFINE_2OP_ARITHMETIC_INTRINSIC_WVV(name, pattern, ...)                  \
  DEFINE_W_ARITHMETIC_INTRINSIC(V##name##vv, pattern, return ({ __VA_ARGS__; }); \
                                , (SIMD128Register src1, SIMD128Register src2), (src1, src2))

#define DEFINE_2OP_ARITHMETIC_INTRINSIC_WV(name, pattern, ...)                   \
  DEFINE_W_ARITHMETIC_INTRINSIC(V##name##wv, pattern, return ({ __VA_ARGS__; }); \
                                , (SIMD128Register src1, SIMD128Register src2), (src1, src2))

#define DEFINE_2OP_ARITHMETIC_INTRINSIC_WX(name, pattern, ...)                   \
  DEFINE_W_ARITHMETIC_INTRINSIC(V##name##wx, pattern, return ({ __VA_ARGS__; }); \
                                , (SIMD128Register src1, ElementType src2), (src1, src2))

DEFINE_1OP_ARITHMETIC_INTRINSIC_V(copy, auto [arg] = std::tuple{args...}; arg)
DEFINE_1OP_ARITHMETIC_INTRINSIC_X(copy, auto [arg] = std::tuple{args...}; arg)
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(add, (args + ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(add, (args + ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(rsub, auto [arg1, arg2] = std::tuple{args...}; (arg2 - arg1))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(sub, (args - ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(sub, (args - ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(and, (args & ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(and, (args & ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(or, (args | ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(or, (args | ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(xor, (args ^ ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(xor, (args ^ ...))
// SIMD mask either includes results with all bits set to 0 or all bits set to 1.
// This way it may be used with VAnd and VAndN operations to perform masking.
// Such comparison is effectively one instruction of x86-64 (via SSE or AVX) but
// to achieve it we need to multiply bool result on (~ElementType{0}).
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(
    seq,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args == ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(
    seq,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args == ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(
    sne,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args != ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(
    sne,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args != ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(
    slt,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args < ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(
    slt,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args < ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(
    sle,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args <= ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(
    sle,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args <= ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(
    sgt,
    (~ElementType{0}) * ElementType{static_cast<typename ElementType::BaseType>((args > ...))})
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(sl, auto [arg1, arg2] = std::tuple{args...}; (arg1 << arg2))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(sl, auto [arg1, arg2] = std::tuple{args...}; (arg1 << arg2))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(sr, auto [arg1, arg2] = std::tuple{args...}; (arg1 >> arg2))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(sr, auto [arg1, arg2] = std::tuple{args...}; (arg1 >> arg2))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VV(macc, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   ((arg2 * arg1) + arg3))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VX(macc, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   ((arg2 * arg1) + arg3))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VV(nmsac, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   (-(arg2 * arg1) + arg3))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VX(nmsac, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   (-(arg2 * arg1) + arg3))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VV(madd, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   ((arg2 * arg3) + arg1))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VX(madd, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   ((arg2 * arg3) + arg1))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VV(nmsub, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   (-(arg2 * arg3) + arg1))
DEFINE_3OP_ARITHMETIC_INTRINSIC_VX(nmsub, auto [arg1, arg2, arg3] = std::tuple{args...};
                                   (-(arg2 * arg3) + arg1))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(fmin, std::get<0>(FMin(args...)))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(fmax, std::get<0>(FMax(args...)))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(min, std::min(args...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(min, std::min(args...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(max, std::max(args...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(max, std::max(args...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(redsum, (args + ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(redand, (args & ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(redor, (args | ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(redxor, (args ^ ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(redmin, std::min(args...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VS(redmax, std::max(args...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(mul, auto [arg1, arg2] = std::tuple{args...}; (arg2 * arg1))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(mul, auto [arg1, arg2] = std::tuple{args...}; (arg2 * arg1))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(mulh, auto [arg1, arg2] = std::tuple{args...};
                                   NarrowTopHalf(Widen(arg2) * Widen(arg1)))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(mulh, auto [arg1, arg2] = std::tuple{args...};
                                   NarrowTopHalf(Widen(arg2) * Widen(arg1)))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VV(mulhsu, auto [arg1, arg2] = std::tuple{args...};
                                   NarrowTopHalf(BitCastToUnsigned(Widen(BitCastToSigned(arg2))) *
                                                 Widen(BitCastToUnsigned(arg1))))
DEFINE_2OP_ARITHMETIC_INTRINSIC_VX(mulhsu, auto [arg1, arg2] = std::tuple{args...};
                                   NarrowTopHalf(BitCastToUnsigned(Widen(BitCastToSigned(arg2))) *
                                                 Widen(BitCastToUnsigned(arg1))))
DEFINE_1OP_ARITHMETIC_INTRINSIC_M(cpop, Popcount(args...))
DEFINE_1OP_ARITHMETIC_INTRINSIC_M(first, auto [arg] = std::tuple{args...};
                                  (arg == Int128{0})
                                      ? Int128{-1}
                                      : Popcount(arg ^ (arg - Int128{1})) - Int128{1})
DEFINE_2OP_ARITHMETIC_INTRINSIC_WVV(wadd, Widenvv, (args + ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_WVV(wsub, Widenvv, (args - ...))
DEFINE_2OP_ARITHMETIC_INTRINSIC_WV(nsr, Narrowwv, auto [arg1, arg2] = std::tuple{args...};
                                   (arg1 >> arg2))
DEFINE_2OP_ARITHMETIC_INTRINSIC_WX(nsr, Narrowwv, auto [arg1, arg2] = std::tuple{args...};
                                   (arg1 >> arg2))

#undef DEFINE_ARITHMETIC_INTRINSIC
#undef DEFINE_W_ARITHMETIC_INTRINSIC
#undef DEFINE_ARITHMETIC_PARAMETERS_OR_ARGUMENTS
#undef DEFINE_1OP_ARITHMETIC_INTRINSIC_M
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_VS
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_VV
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_VX
#undef DEFINE_3OP_ARITHMETIC_INTRINSIC_VV
#undef DEFINE_3OP_ARITHMETIC_INTRINSIC_VX
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_VV
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_WVV
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_WV
#undef DEFINE_2OP_ARITHMETIC_INTRINSIC_WX

}  // namespace berberis::intrinsics

#endif  // BERBERIS_INTRINSICS_RISCV64_VECTOR_INTRINSICS_H_