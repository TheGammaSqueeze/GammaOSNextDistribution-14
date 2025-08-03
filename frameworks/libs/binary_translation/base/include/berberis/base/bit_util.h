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

#ifndef BERBERIS_BASE_BIT_UTIL_H_
#define BERBERIS_BASE_BIT_UTIL_H_

#include <climits>
#include <cstdint>
#include <cstring>
#include <limits>
#include <type_traits>

#include "berberis/base/checks.h"
#include "berberis/base/dependent_false.h"

namespace berberis {

template <typename BaseType>
class Raw;

template <typename BaseType>
class Saturating;

template <typename BaseType>
class Wrapping;

template <typename T>
constexpr bool IsPowerOf2(T x) {
  static_assert(std::is_integral_v<T>, "IsPowerOf2: T must be integral");
  DCHECK(x != 0);
  return (x & (x - 1)) == 0;
}

template <typename T>
constexpr bool IsPowerOf2(Raw<T> x) {
  return IsPowerOf2(x.value);
}

template <typename T>
constexpr bool IsPowerOf2(Saturating<T> x) {
  return IsPowerOf2(x.value);
}

template <typename T>
constexpr bool IsPowerOf2(Wrapping<T> x) {
  return IsPowerOf2(x.value);
}

template <size_t kAlign, typename T>
constexpr T AlignDown(T x) {
  static_assert(std::is_integral_v<T>);
  static_assert(IsPowerOf2(kAlign));
  static_assert(static_cast<T>(kAlign) > 0);
  return x & ~(kAlign - 1);
}

template <typename T>
constexpr T AlignDown(T x, size_t align) {
  static_assert(std::is_integral_v<T>, "AlignDown: T must be integral");
  DCHECK(IsPowerOf2(align));
  return x & ~(align - 1);
}

template <size_t kAlign, typename T>
constexpr Raw<T> AlignDown(Raw<T> x) {
  return {AlignDown<kAlign>(x.value)};
}

template <size_t kAlign, typename T>
constexpr Saturating<T> AlignDown(Saturating<T> x) {
  return {AlignDown<kAlign>(x.value)};
}

template <size_t kAlign, typename T>
constexpr Wrapping<T> AlignDown(Wrapping<T> x) {
  return {AlignDown<kAlign>(x.value)};
}

// Helper to align pointers.
template <size_t kAlign, typename T>
constexpr T* AlignDown(T* p) {
  return reinterpret_cast<T*>(AlignDown<kAlign>(reinterpret_cast<uintptr_t>(p)));
}

template <typename T>
constexpr T* AlignDown(T* p, size_t align) {
  return reinterpret_cast<T*>(AlignDown(reinterpret_cast<uintptr_t>(p), align));
}

template <size_t kAlign, typename T>
constexpr T AlignUp(T x) {
  return AlignDown<kAlign>(x + kAlign - 1);
}

template <typename T>
constexpr T AlignUp(T x, size_t align) {
  return AlignDown(x + align - 1, align);
}

template <size_t kAlign, typename T>
constexpr Raw<T> AlignUp(Raw<T> x) {
  return {AlignUp<kAlign>(x.value)};
}

template <size_t kAlign, typename T>
constexpr Saturating<T> AlignUp(Saturating<T> x) {
  return {AlignUp<kAlign>(x.value)};
}

template <size_t kAlign, typename T>
constexpr Wrapping<T> AlignUp(Wrapping<T> x) {
  return {AlignUp<kAlign>(x.value)};
}

// Helper to align pointers.
template <size_t kAlign, typename T>
constexpr T* AlignUp(T* p) {
  return reinterpret_cast<T*>(AlignUp<kAlign>(reinterpret_cast<uintptr_t>(p)));
}

template <typename T>
constexpr T* AlignUp(T* p, size_t align) {
  return reinterpret_cast<T*>(AlignUp(reinterpret_cast<uintptr_t>(p), align));
}

template <size_t kAlign, typename T>
constexpr bool IsAligned(T x) {
  return AlignDown<kAlign>(x) == x;
}

template <typename T>
constexpr bool IsAligned(T x, size_t align) {
  return AlignDown(x, align) == x;
}

template <size_t kAlign, typename T>
constexpr bool IsAligned(Raw<T> x) {
  return IsAligned<kAlign>(x.value);
}

template <size_t kAlign, typename T>
constexpr bool IsAligned(Saturating<T> x) {
  return IsAligned<kAlign>(x.value);
}

template <size_t kAlign, typename T>
constexpr bool IsAligned(Wrapping<T> x) {
  return IsAligned<kAlign>(x.value);
}

// Helper to align pointers.
template <size_t kAlign, typename T>
constexpr bool IsAligned(T* p, size_t align) {
  return IsAligned<kAlign>(reinterpret_cast<uintptr_t>(p), align);
}

template <typename T>
constexpr bool IsAligned(T* p, size_t align) {
  return IsAligned(reinterpret_cast<uintptr_t>(p), align);
}

template <typename T>
constexpr T BitUtilLog2(T x) {
  static_assert(std::is_integral_v<T>, "Log2: T must be integral");
  CHECK(IsPowerOf2(x));
  // TODO(b/260725458): Use std::countr_zero after C++20 becomes available
  return __builtin_ctz(x);
}
// Signextend bits from size to the corresponding signed type of sizeof(Type) size.
// If the result of this function is assigned to a wider signed type it'll automatically
// sign-extend.
template <unsigned size, typename Type>
static auto SignExtend(const Type val) {
  static_assert(std::is_integral_v<Type>, "Only integral types are supported");
  static_assert(size > 0 && size < (sizeof(Type) * CHAR_BIT), "Invalid size value");
  using SignedType = std::make_signed_t<Type>;
  struct {
    SignedType val : size;
  } holder = {.val = static_cast<SignedType>(val)};
  // Compiler takes care of sign-extension of the field with the specified bit-length.
  return static_cast<SignedType>(holder.val);
}

// Verify that argument value fits into a target.
template <typename ResultType, typename ArgumentType>
inline bool IsInRange(ArgumentType x) {
  // Note: conversion from wider integer type into narrow integer type is always
  // defined.  Conversion to unsigned produces well-defined result while conversion
  // to signed type produces implementation-defined result but in both cases value
  // is guaranteed to be unchanged if it can be represented in the destination type
  // and is *some* valid value if it's unrepesentable.
  //
  // Quote from the standard (including "note" in the standard):
  //   If the destination type is unsigned, the resulting value is the least unsigned
  // integer congruent to the source integer (modulo 2ⁿ where n is the number of bits
  // used to represent the unsigned type). [ Note: In a two’s complement representation,
  // this conversion is conceptual and there is no change in the bit pattern (if there
  // is no truncation). — end note ]
  //   If the destination type is signed, the value is unchanged if it can be represented
  // in the destination type; otherwise, the value is implementation-defined.

  return static_cast<ResultType>(x) == x;
}

template <typename T>
[[nodiscard]] constexpr T Popcount(T x) {
  // We couldn't use std::popcount yet ( http://b/318678905 ) for __uint128_t .
  // Switch to std::popcount when/if that bug would be fixed.
#if defined(__x86_64__)
  if constexpr (sizeof(T) == sizeof(unsigned __int128)) {
    return __builtin_popcountll(x) + __builtin_popcountll(x >> 64);
  } else
#endif
      if constexpr (sizeof(T) == sizeof(uint64_t)) {
    return __builtin_popcountll(x);
  } else if constexpr (sizeof(T) == sizeof(uint32_t)) {
    return __builtin_popcount(x);
  } else {
    static_assert(kDependentTypeFalse<T>);
  }
}

template <typename T>
[[nodiscard]] constexpr Raw<T> Popcount(Raw<T> x) {
  return {Popcount(x.value)};
}

template <typename T>
[[nodiscard]] constexpr Saturating<T> Popcount(Saturating<T> x) {
  return {Popcount(x.value)};
}

template <typename T>
[[nodiscard]] constexpr Wrapping<T> Popcount(Wrapping<T> x) {
  return {Popcount(x.value)};
}

// bit_cast<Dest, Source> is a well-defined equivalent of address-casting:
//   *reinterpret_cast<Dest*>(&source)
// See chromium base/macros.h for details.
template <class Dest, class Source>
inline Dest bit_cast(const Source& source) {
  static_assert(sizeof(Dest) == sizeof(Source),
                "bit_cast: source and destination must be of same size");
  static_assert(std::is_trivially_copyable_v<Dest>,
                "bit_cast: destination must be trivially copyable");
  static_assert(std::is_trivially_copyable_v<Source>,
                "bit_cast: source must be trivially copyable");
  Dest dest;
  memcpy(&dest, &source, sizeof(dest));
  return dest;
}

namespace intrinsics {

template <typename BaseType>
class WrappedFloatType;

}  // namespace intrinsics

template <typename T>
struct TypeTraits;

// Raw integers.  Used to carry payload, which may be be EXPLICITLY converted to Saturating
// integer, Wrapping integer, or WrappedFloatType.
//
// 𝐃𝐨𝐞𝐬𝐧'𝐭 suppopt any actual operations, arithmetic, etc.
// Use bitcast or convert to one of three types listed above!

template <typename Base>
class Raw {
 public:
  using BaseType = Base;

  static_assert(std::is_integral_v<BaseType>);
  static_assert(!std::is_signed_v<BaseType>);

  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(IntType) == sizeof(BaseType)>>
  [[nodiscard]] constexpr operator IntType() const {
    return static_cast<IntType>(value);
  }
  template <typename IntType,
            typename = std::enable_if_t<
                std::is_integral_v<IntType> && sizeof(BaseType) == sizeof(IntType) &&
                !std::is_signed_v<IntType> && !std::is_same_v<IntType, BaseType>>>
  [[nodiscard]] constexpr operator Raw<IntType>() const {
    return {static_cast<IntType>(value)};
  }
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Saturating<IntType>() const {
    return {static_cast<IntType>(value)};
  }
  template <typename FloatType,
            typename = std::enable_if_t<!std::numeric_limits<FloatType>::is_exact &&
                                        sizeof(BaseType) == sizeof(FloatType)>>
  [[nodiscard]] constexpr operator intrinsics::WrappedFloatType<FloatType>() const {
    // Can't use bit_cast here because of IA32 ABI!
    intrinsics::WrappedFloatType<FloatType> result;
    memcpy(&result, &value, sizeof(BaseType));
    return result;
  }
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Wrapping<IntType>() const {
    return {static_cast<IntType>(value)};
  }

  template <typename ResultType>
  friend auto constexpr MaybeTruncateTo(Raw src)
      -> std::enable_if_t<sizeof(typename ResultType::BaseType) <= sizeof(BaseType), ResultType> {
    return ResultType{static_cast<ResultType::BaseType>(src.value)};
  }
  template <typename ResultType>
  friend auto constexpr TruncateTo(Raw src)
      -> std::enable_if_t<sizeof(typename ResultType::BaseType) < sizeof(BaseType), ResultType> {
    return ResultType{static_cast<ResultType::BaseType>(src.value)};
  }

  [[nodiscard]] friend constexpr bool operator==(Raw lhs, Raw rhs) {
    return lhs.value == rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator!=(Raw lhs, Raw rhs) {
    return lhs.value != rhs.value;
  }

  BaseType value = 0;
};

// Saturating and wrapping integers.
//   1. Never trigger UB, even in case of overflow.
//   2. Only support mixed types when both are of the same type (e.g. SatInt8 and SatInt16 or
//      Int8 and Int64 are allowed, but SatInt8 and Int8 are forbidden and Int32 and Uint32
//      require explicit casting, too).
//   3. Results are performed after type expansion.

template <typename Base>
class Saturating {
 public:
  using BaseType = Base;
  using SignedType = Saturating<std::make_signed_t<BaseType>>;
  using UnsignedType = Saturating<std::make_unsigned_t<BaseType>>;
  static constexpr bool kIsSigned = std::is_signed_v<BaseType>;

  static_assert(std::is_integral_v<BaseType>);

  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        ((sizeof(BaseType) < sizeof(IntType) &&
                                          (std::is_signed_v<IntType> ||
                                           kIsSigned == std::is_signed_v<IntType>)) ||
                                         sizeof(IntType) == sizeof(BaseType))>>
  [[nodiscard]] constexpr operator IntType() const {
    return static_cast<IntType>(value);
  }
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Raw<IntType>() const {
    return {static_cast<IntType>(value)};
  }
  template <typename IntType,
            typename = std::enable_if_t<
                std::is_integral_v<IntType> && sizeof(BaseType) <= sizeof(IntType) &&
                std::is_signed_v<IntType> == kIsSigned && !std::is_same_v<IntType, BaseType>>>
  [[nodiscard]] constexpr operator Saturating<IntType>() const {
    return {static_cast<IntType>(value)};
  }
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Wrapping<IntType>() const {
    return {static_cast<IntType>(value)};
  }

  [[nodiscard]] friend constexpr bool operator==(Saturating lhs, Saturating rhs) {
    return lhs.value == rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator!=(Saturating lhs, Saturating rhs) {
    return lhs.value != rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator<(Saturating lhs, Saturating rhs) {
    return lhs.value < rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator<=(Saturating lhs, Saturating rhs) {
    return lhs.value <= rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator>(Saturating lhs, Saturating rhs) {
    return lhs.value > rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator>=(Saturating lhs, Saturating rhs) {
    return lhs.value >= rhs.value;
  }
  friend constexpr Saturating& operator+=(Saturating& lhs, Saturating rhs) {
    lhs = lhs + rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Saturating operator+(Saturating lhs, Saturating rhs) {
    BaseType result;
    bool overflow = __builtin_add_overflow(lhs.value, rhs.value, &result);
    if (overflow) {
      if constexpr (kIsSigned) {
        if (result < 0) {
          result = std::numeric_limits<BaseType>::max();
        } else {
          result = std::numeric_limits<BaseType>::min();
        }
      } else {
        result = std::numeric_limits<BaseType>::max();
      }
    }
    return {result};
  }
  friend constexpr Saturating& operator-=(Saturating& lhs, Saturating rhs) {
    lhs = lhs - rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Saturating operator-(Saturating lhs) {
    if constexpr (kIsSigned) {
      if (lhs.value == std::numeric_limits<BaseType>::min()) {
        return {std::numeric_limits<BaseType>::max()};
      }
      return {-lhs.value};
    }
    return 0;
  }
  [[nodiscard]] friend constexpr Saturating operator-(Saturating lhs, Saturating rhs) {
    BaseType result;
    bool overflow = __builtin_sub_overflow(lhs.value, rhs.value, &result);
    if (overflow) {
      if constexpr (kIsSigned) {
        if (result < 0) {
          result = std::numeric_limits<BaseType>::max();
        } else {
          result = std::numeric_limits<BaseType>::min();
        }
      } else {
        result = 0;
      }
    }
    return {result};
  }
  friend constexpr Saturating& operator*=(Saturating& lhs, Saturating rhs) {
    lhs = lhs * rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Saturating operator*(Saturating lhs, Saturating rhs) {
    BaseType result;
    bool overflow = __builtin_mul_overflow(lhs.value, rhs.value, &result);
    if (overflow) {
      if constexpr (kIsSigned) {
        if (lhs.value < 0 != rhs.value < 0) {
          result = std::numeric_limits<BaseType>::min();
        } else {
          result = std::numeric_limits<BaseType>::max();
        }
      } else {
        result = std::numeric_limits<BaseType>::max();
      }
    }
    return {result};
  }
  friend constexpr Saturating& operator/=(Saturating& lhs, Saturating rhs) {
    lhs = lhs / rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Saturating operator/(Saturating lhs, Saturating rhs) {
    if constexpr (kIsSigned) {
      if (lhs.value == std::numeric_limits<BaseType>::min() && rhs.value == -1) {
        return {std::numeric_limits<BaseType>::max()};
      }
    }
    return {BaseType(lhs.value / rhs.value)};
  }
  friend constexpr Saturating& operator%=(Saturating& lhs, Saturating rhs) {
    lhs = lhs % rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Saturating operator%(Saturating lhs, Saturating rhs) {
    if constexpr (kIsSigned) {
      if (lhs.value == std::numeric_limits<BaseType>::min() && rhs.value == -1) {
        return {1};
      }
    }
    return {BaseType(lhs.value % rhs.value)};
  }
  BaseType value = 0;
};

template <typename Base>
class Wrapping {
 public:
  using BaseType = Base;
  using SignedType = Wrapping<std::make_signed_t<BaseType>>;
  using UnsignedType = Wrapping<std::make_unsigned_t<BaseType>>;
  static constexpr bool kIsSigned = std::is_signed_v<BaseType>;

  static_assert(std::is_integral_v<BaseType>);

  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        ((sizeof(BaseType) < sizeof(IntType) &&
                                          (std::is_signed_v<IntType> ||
                                           kIsSigned == std::is_signed_v<IntType>)) ||
                                         sizeof(IntType) == sizeof(BaseType))>>
  [[nodiscard]] constexpr operator IntType() const {
    return static_cast<IntType>(value);
  }
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Raw<IntType>() const {
    return {static_cast<IntType>(value)};
  }
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<IntType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Saturating<IntType>() const {
    return {static_cast<IntType>(value)};
  }
  template <typename IntType,
            typename = std::enable_if_t<
                std::is_integral_v<IntType> && sizeof(BaseType) <= sizeof(IntType) &&
                std::is_signed_v<IntType> == kIsSigned && !std::is_same_v<IntType, BaseType>>>
  [[nodiscard]] constexpr operator Wrapping<IntType>() const {
    return {static_cast<IntType>(value)};
  }

  [[nodiscard]] friend constexpr bool operator==(Wrapping lhs, Wrapping rhs) {
    return lhs.value == rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator!=(Wrapping lhs, Wrapping rhs) {
    return lhs.value != rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator<(Wrapping lhs, Wrapping rhs) {
    return lhs.value < rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator<=(Wrapping lhs, Wrapping rhs) {
    return lhs.value <= rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator>(Wrapping lhs, Wrapping rhs) {
    return lhs.value > rhs.value;
  }
  [[nodiscard]] friend constexpr bool operator>=(Wrapping lhs, Wrapping rhs) {
    return lhs.value >= rhs.value;
  }
  // Note:
  //   1. We use __builtin_xxx_overflow instead of simple +, -, or * operators because
  //      __builtin_xxx_overflow produces well-defined result in case of overflow while
  //      +, -, * are triggering undefined behavior conditions.
  //   2. All operator xxx= are implemented in terms of opernator xxx
  friend constexpr Wrapping& operator+=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs + rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator+(Wrapping lhs, Wrapping rhs) {
    BaseType result;
    __builtin_add_overflow(lhs.value, rhs.value, &result);
    return {result};
  }
  friend constexpr Wrapping& operator-=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs - rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator-(Wrapping lhs) {
    BaseType result;
    __builtin_sub_overflow(BaseType{0}, lhs.value, &result);
    return {result};
  }
  [[nodiscard]] friend constexpr Wrapping operator-(Wrapping lhs, Wrapping rhs) {
    BaseType result;
    __builtin_sub_overflow(lhs.value, rhs.value, &result);
    return {result};
  }
  friend constexpr Wrapping& operator*=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs * rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator*(Wrapping lhs, Wrapping rhs) {
    BaseType result;
    __builtin_mul_overflow(lhs.value, rhs.value, &result);
    return {result};
  }
  friend constexpr Wrapping& operator/=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs / rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator/(Wrapping lhs, Wrapping rhs) {
    if constexpr (kIsSigned) {
      if (lhs.value == std::numeric_limits<BaseType>::min() && rhs.value == -1) {
        return {std::numeric_limits<BaseType>::min()};
      }
    }
    return {BaseType(lhs.value / rhs.value)};
  }
  friend constexpr Wrapping& operator%=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs % rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator%(Wrapping lhs, Wrapping rhs) {
    if constexpr (kIsSigned) {
      if (lhs.value == std::numeric_limits<BaseType>::min() && rhs.value == -1) {
        return {0};
      }
    }
    return {BaseType(lhs.value % rhs.value)};
  }
  friend constexpr Wrapping& operator<<=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs << rhs;
    return lhs;
  }
  template <typename IntType>
  [[nodiscard]] friend constexpr Wrapping operator<<(Wrapping lhs, Wrapping<IntType> rhs) {
    return {BaseType(lhs.value << (rhs.value & (sizeof(BaseType) * CHAR_BIT - 1)))};
  }
  friend constexpr Wrapping& operator>>=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs >> rhs;
    return lhs;
  }
  template <typename IntType>
  [[nodiscard]] friend constexpr Wrapping operator>>(Wrapping lhs, Wrapping<IntType> rhs) {
    return {BaseType(lhs.value >> (rhs.value & (sizeof(BaseType) * CHAR_BIT - 1)))};
  }
  friend constexpr Wrapping& operator&=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs & rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator&(Wrapping lhs, Wrapping rhs) {
    return {BaseType(lhs.value & rhs.value)};
  }
  friend constexpr Wrapping& operator|=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs | rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator|(Wrapping lhs, Wrapping rhs) {
    return {BaseType(lhs.value | rhs.value)};
  }
  friend constexpr Wrapping& operator^=(Wrapping& lhs, Wrapping rhs) {
    lhs = lhs ^ rhs;
    return lhs;
  }
  [[nodiscard]] friend constexpr Wrapping operator^(Wrapping lhs, Wrapping rhs) {
    return {BaseType(lhs.value ^ rhs.value)};
  }
  [[nodiscard]] friend constexpr Wrapping operator~(Wrapping lhs) { return {BaseType(~lhs.value)}; }
  BaseType value = 0;
};

using RawInt8 = Raw<uint8_t>;
using RawInt16 = Raw<uint16_t>;
using RawInt32 = Raw<uint32_t>;
using RawInt64 = Raw<uint64_t>;
#if defined(__x86_64__)
using RawInt128 = Raw<unsigned __int128>;
#endif

using SatInt8 = Saturating<int8_t>;
using SatUInt8 = Saturating<uint8_t>;
using SatInt16 = Saturating<int16_t>;
using SatUInt16 = Saturating<uint16_t>;
using SatInt32 = Saturating<int32_t>;
using SatUInt32 = Saturating<uint32_t>;
using SatInt64 = Saturating<int64_t>;
using SatUInt64 = Saturating<uint64_t>;
#if defined(__x86_64__)
using SatInt128 = Saturating<__int128>;
using SatUInt128 = Saturating<unsigned __int128>;
#endif

using Int8 = Wrapping<int8_t>;
using UInt8 = Wrapping<uint8_t>;
using Int16 = Wrapping<int16_t>;
using UInt16 = Wrapping<uint16_t>;
using Int32 = Wrapping<int32_t>;
using UInt32 = Wrapping<uint32_t>;
using Int64 = Wrapping<int64_t>;
using UInt64 = Wrapping<uint64_t>;
using IntPtr = Wrapping<intptr_t>;
using UIntPtr = Wrapping<uintptr_t>;
#if defined(__x86_64__)
using Int128 = Wrapping<__int128>;
using UInt128 = Wrapping<unsigned __int128>;
#endif

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToSigned(Raw<IntType> src) ->
    typename Wrapping<IntType>::SignedType {
  return {static_cast<std::make_signed_t<IntType>>(src.value)};
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToSigned(Saturating<IntType> src) ->
    typename Saturating<IntType>::SignedType {
  return {static_cast<std::make_signed_t<IntType>>(src.value)};
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToSigned(Wrapping<IntType> src) ->
    typename Wrapping<IntType>::SignedType {
  return {static_cast<std::make_signed_t<IntType>>(src.value)};
}

template <typename T>
using SignedType = decltype(BitCastToSigned(std::declval<T>()));

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToUnsigned(Raw<IntType> src) ->
    typename Wrapping<IntType>::UnsignedType {
  return {static_cast<std::make_unsigned_t<IntType>>(src.value)};
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToUnsigned(Saturating<IntType> src) ->
    typename Saturating<IntType>::UnsignedType {
  return {static_cast<std::make_unsigned_t<IntType>>(src.value)};
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToUnsigned(Wrapping<IntType> src) ->
    typename Wrapping<IntType>::UnsignedType {
  return {static_cast<std::make_unsigned_t<IntType>>(src.value)};
}

template <typename T>
using UnsignedType = decltype(BitCastToUnsigned(std::declval<T>()));

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToSaturating(Saturating<IntType> src) -> Saturating<IntType> {
  return src;
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToSaturating(Wrapping<IntType> src) -> Saturating<IntType> {
  return {src.value};
}

template <typename T>
using SaturatingType = decltype(BitCastToSaturating(std::declval<T>()));

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToWrapping(Saturating<IntType> src) -> Wrapping<IntType> {
  return {src.value};
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToWrapping(Wrapping<IntType> src) -> Wrapping<IntType> {
  return src;
}

template <typename T>
using WrappingType = decltype(BitCastToWrapping(std::declval<T>()));

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToRaw(Raw<IntType> src) -> Raw<IntType> {
  return src;
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToRaw(Saturating<IntType> src)
    -> Raw<std::make_unsigned_t<IntType>> {
  return {static_cast<std::make_unsigned_t<IntType>>(src.value)};
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToRaw(Wrapping<IntType> src)
    -> Raw<std::make_unsigned_t<IntType>> {
  return {static_cast<std::make_unsigned_t<IntType>>(src.value)};
}

template <typename BaseType>
[[nodiscard]] constexpr auto BitCastToRaw(intrinsics::WrappedFloatType<BaseType> src)
    -> Raw<std::make_unsigned_t<typename TypeTraits<intrinsics::WrappedFloatType<BaseType>>::Int>> {
  return {bit_cast<
      std::make_unsigned_t<typename TypeTraits<intrinsics::WrappedFloatType<BaseType>>::Int>>(src)};
}

template <typename T>
using RawType = decltype(BitCastToRaw(std::declval<T>()));

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToFloat(Raw<IntType> src) ->
    typename TypeTraits<IntType>::Float {
  return bit_cast<typename TypeTraits<IntType>::Float>(src.value);
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToFloat(Saturating<IntType> src) ->
    typename TypeTraits<IntType>::Float {
  return bit_cast<typename TypeTraits<IntType>::Float>(src.value);
}

template <typename IntType>
[[nodiscard]] auto constexpr BitCastToFloat(Wrapping<IntType> src) ->
    typename TypeTraits<IntType>::Float {
  return bit_cast<typename TypeTraits<IntType>::Float>(src.value);
}

template <typename BaseType>
[[nodiscard]] constexpr auto BitCastToFloat(intrinsics::WrappedFloatType<BaseType> src)
    -> intrinsics::WrappedFloatType<BaseType> {
  return src;
}

template <typename T>
using FloatType = decltype(BitCastToFloat(std::declval<T>()));

template <typename ResultType, typename IntType>
[[nodiscard]] auto constexpr MaybeTruncateTo(IntType src)
    -> std::enable_if_t<std::is_integral_v<IntType> &&
                            sizeof(typename ResultType::BaseType) <= sizeof(IntType),
                        ResultType> {
  return ResultType{static_cast<ResultType::BaseType>(src)};
}

template <typename ResultType, typename IntType>
[[nodiscard]] auto constexpr MaybeTruncateTo(Saturating<IntType> src)
    -> std::enable_if_t<std::is_integral_v<IntType> &&
                            sizeof(typename ResultType::BaseType) <= sizeof(IntType),
                        ResultType> {
  return ResultType{static_cast<ResultType::BaseType>(src.value)};
}

template <typename ResultType, typename IntType>
[[nodiscard]] auto constexpr MaybeTruncateTo(Wrapping<IntType> src)
    -> std::enable_if_t<std::is_integral_v<IntType> &&
                            sizeof(typename ResultType::BaseType) <= sizeof(IntType),
                        ResultType> {
  return ResultType{static_cast<ResultType::BaseType>(src.value)};
}

template <typename ResultType, typename IntType>
[[nodiscard]] auto constexpr TruncateTo(IntType src)
    -> std::enable_if_t<std::is_integral_v<IntType> &&
                            sizeof(typename ResultType::BaseType) < sizeof(IntType),
                        ResultType> {
  return ResultType{static_cast<ResultType::BaseType>(src)};
}

template <typename ResultType, typename IntType>
[[nodiscard]] auto constexpr TruncateTo(Saturating<IntType> src)
    -> std::enable_if_t<std::is_integral_v<IntType> &&
                            sizeof(typename ResultType::BaseType) < sizeof(IntType),
                        ResultType> {
  return ResultType{static_cast<ResultType::BaseType>(src.value)};
}

template <typename ResultType, typename IntType>
[[nodiscard]] auto constexpr TruncateTo(Wrapping<IntType> src)
    -> std::enable_if_t<std::is_integral_v<IntType> &&
                            sizeof(typename ResultType::BaseType) < sizeof(IntType),
                        ResultType> {
  return ResultType{static_cast<ResultType::BaseType>(src.value)};
}

template <typename BaseType>
[[nodiscard]] constexpr auto Widen(Saturating<BaseType> source)
    -> Saturating<typename TypeTraits<BaseType>::Wide> {
  return {source.value};
}

template <typename BaseType>
[[nodiscard]] constexpr auto Widen(Wrapping<BaseType> source)
    -> Wrapping<typename TypeTraits<BaseType>::Wide> {
  return {source.value};
}

template <typename BaseType>
[[nodiscard]] constexpr auto Widen(intrinsics::WrappedFloatType<BaseType> source)
    -> Wrapping<typename TypeTraits<intrinsics::WrappedFloatType<BaseType>>::Wide> {
  return {source.value};
}

template <typename T>
using WideType = decltype(Widen(std::declval<T>()));

template <typename BaseType>
[[nodiscard]] constexpr auto Narrow(Saturating<BaseType> source)
    -> Saturating<typename TypeTraits<BaseType>::Narrow> {
  if constexpr (Saturating<BaseType>::kIsSigned) {
    if (source.value < std::numeric_limits<typename TypeTraits<BaseType>::Narrow>::min()) {
      return {std::numeric_limits<typename TypeTraits<BaseType>::Narrow>::min()};
    }
  }
  if (source.value > std::numeric_limits<typename TypeTraits<BaseType>::Narrow>::max()) {
    return {std::numeric_limits<typename TypeTraits<BaseType>::Narrow>::max()};
  }
  return {static_cast<typename TypeTraits<BaseType>::Narrow>(source.value)};
}

template <typename BaseType>
[[nodiscard]] constexpr auto Narrow(Wrapping<BaseType> source)
    -> Wrapping<typename TypeTraits<BaseType>::Narrow> {
  return {static_cast<typename TypeTraits<BaseType>::Narrow>(source.value)};
}

template <typename BaseType>
[[nodiscard]] constexpr auto Narrow(intrinsics::WrappedFloatType<BaseType> source)
    -> Wrapping<typename TypeTraits<intrinsics::WrappedFloatType<BaseType>>::Narrow> {
  return {source.value};
}

template <typename T>
using NarrowType = decltype(Narrow(std::declval<T>()));

// While `Narrow` returns value reduced to smaller data type there are centain algorithms
// which require the top half, too (most ofhen in the context of widening multiplication
// where top half of the product is produced).
// `NarrowTopHalf` returns top half of the value narrowed down to smaller type (overflow is not
// possible in that case).
template <typename BaseType>
[[nodiscard]] constexpr auto NarrowTopHalf(Wrapping<BaseType> source)
    -> Wrapping<typename TypeTraits<BaseType>::Narrow> {
  return {static_cast<typename TypeTraits<BaseType>::Narrow>(
      source.value >> (sizeof(typename TypeTraits<BaseType>::Narrow) * CHAR_BIT))};
}

}  // namespace berberis

#endif  // BERBERIS_BASE_BIT_UTIL_H_
