/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_TYPE_TRAITS_H_
#define BERBERIS_INTRINSICS_TYPE_TRAITS_H_

#if defined(__i386__) || defined(__x86_64__)
#include <xmmintrin.h>
#endif

#include <cstdint>

#include "berberis/intrinsics/common/intrinsics_float.h"
#include "berberis/intrinsics/simd_register.h"

namespace berberis {

// In specializations we define various derivative types:
//  Wide - type twice as wide, same signedness
template <typename T>
struct TypeTraits;

template <>
struct TypeTraits<uint8_t> {
  using Wide = uint16_t;
  static constexpr int kBits = 8;
  static constexpr char kName[] = "uint8_t";
};

template <>
struct TypeTraits<uint16_t> {
  using Wide = uint32_t;
  using Narrow = uint8_t;
  static constexpr int kBits = 16;
  static constexpr char kName[] = "uint16_t";
};

template <>
struct TypeTraits<uint32_t> {
  using Wide = uint64_t;
  using Narrow = uint16_t;
  using Float = intrinsics::Float32;
  static constexpr int kBits = 32;
  static constexpr char kName[] = "uint32_t";
};

template <>
struct TypeTraits<uint64_t> {
  using Narrow = uint32_t;
#if defined(__x86_64__)
  using Wide = __uint128_t;
#endif
  using Float = intrinsics::Float64;
  static constexpr int kBits = 64;
  static constexpr char kName[] = "uint64_t";
};

template <>
struct TypeTraits<int8_t> {
  using Wide = int16_t;
  static constexpr int kBits = 8;
  static constexpr char kName[] = "int8_t";
};

template <>
struct TypeTraits<int16_t> {
  using Wide = int32_t;
  using Narrow = int8_t;
  static constexpr int kBits = 16;
  static constexpr char kName[] = "int16_t";
};

template <>
struct TypeTraits<int32_t> {
  using Wide = int64_t;
  using Narrow = int16_t;
  using Float = intrinsics::Float32;
  static constexpr int kBits = 32;
  static constexpr char kName[] = "int32_t";
};

template <>
struct TypeTraits<int64_t> {
  using Narrow = int32_t;
#if defined(__x86_64__)
  using Wide = __int128_t;
#endif
  using Float = intrinsics::Float64;
  static constexpr int kBits = 64;
  static constexpr char kName[] = "int64_t";
};

template <>
struct TypeTraits<intrinsics::Float8> {
  using Int = int8_t;
  using Raw = intrinsics::Float8PhonyType;
  using Wide = intrinsics::Float16;
  static constexpr int kBits = 8;
  static constexpr char kName[] = "Float8";
};

template <>
struct TypeTraits<intrinsics::Float16> {
  using Int = int16_t;
  using Raw = _Float16;
  using Narrow = intrinsics::Float8;
  using Wide = intrinsics::Float32;
  static constexpr int kBits = 16;
  static constexpr char kName[] = "Float16";
};

template <>
struct TypeTraits<intrinsics::Float32> {
  using Int = int32_t;
  using Raw = float;
  using Narrow = intrinsics::Float16;
  using Wide = intrinsics::Float64;
  static constexpr int kBits = 32;
  static constexpr char kName[] = "Float32";
};

template <>
struct TypeTraits<intrinsics::Float64> {
  using Int = int64_t;
  using Raw = double;
  using Narrow = intrinsics::Float32;
#if defined(__x86_64__)
  static_assert(sizeof(long double) > sizeof(intrinsics::Float64));
  using Wide = long double;
#endif
  static constexpr int kBits = 64;
  static constexpr char kName[] = "Float64";
};

template <>
struct TypeTraits<float> {
  using Int = int32_t;
  using Wrapped = intrinsics::Float32;
  using Wide = double;
  static constexpr int kBits = 32;
  static constexpr char kName[] = "float";
};

template <>
struct TypeTraits<double> {
  using Int = int64_t;
  using Wrapped = intrinsics::Float64;
#if defined(__x86_64__)
  static_assert(sizeof(long double) > sizeof(intrinsics::Float64));
  using Wide = long double;
#endif
  using Narrow = float;
  static constexpr int kBits = 64;
  static constexpr char kName[] = "double";
};

template <>
struct TypeTraits<SIMD128Register> {
#if defined(__i386__) || defined(__x86_64__)
  using Raw = __m128;
#endif
  static constexpr char kName[] = "SIMD128Register";
};

#if defined(__x86_64__)

template <>
struct TypeTraits<long double> {
  using Narrow = intrinsics::Float64;
  static constexpr char kName[] = "long double";
};

template <>
struct TypeTraits<__int128_t> {
  using Narrow = int64_t;
  static constexpr int kBits = 128;
  static constexpr char kName[] = "__int128_t";
};

template <>
struct TypeTraits<__uint128_t> {
  using Narrow = uint64_t;
  static constexpr int kBits = 128;
  static constexpr char kName[] = "__uint128_t";
};

#endif

#if defined(__i386__) || defined(__x86_64__)

template <>
struct TypeTraits<__m128> {
  static constexpr int kBits = 128;
  static constexpr char kName[] = "__m128";
};

#endif

}  // namespace berberis

#endif  // BERBERIS_INTRINSICS_TYPE_TRAITS_H_
