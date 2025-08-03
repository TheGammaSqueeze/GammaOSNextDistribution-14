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

#pragma once

#include <bit>
#include <cmath>

namespace android::audio_utils {

/*
 * The compilation option
 * -ffast-math
 *
 * https://gcc.gnu.org/wiki/FloatingPointMath
 *
 * enables the following flags:
 *
 * -fno-trapping-math
 * -funsafe-math-optimizations
 * -ffinite-math-only
 * -fno-errno-math
 * -fno-signaling-nans
 * -fno-rounding-math
 * -fcx-limited-range
 * -fno-signed-zeros.
 *
 * -ffinite-math-only means isnan() and isinf() detection may not work properly.
 */

/**
 * Returns the unsigned integer layout of a float.
 */
inline constexpr unsigned float_as_unsigned(float f) {
    // gnu++20 does not include std::bit_cast, so we use the builtin
    // supported by gnu and clang. That being said, the latest gnu, clang, and msvc
    // compilers all support std::bit_cast so update when language support is upgraded.
    // return std::bit_cast<unsigned>(f);

    return __builtin_bit_cast(unsigned, f);
}

/**
 * Returns true if the float is nan regardless of -ffast-math compilation.
 */
inline constexpr bool safe_isnan(float f) {
    // float is signed-magnitude, so shift sign bit out to do nan comparison.
    // (This works for ILP32 / LP64 as the sign bit is removed by the shift).
    // https://en.wikipedia.org/wiki/Single-precision_floating-point_format
    return float_as_unsigned(f) << 1 >= 0xff800001 << 1;
}

/**
 * Returns true if the float is infinite (pos or neg) regardless of -ffast-math compilation.
 */
inline constexpr bool safe_isinf(float f) {
    // float is signed-magnitude, so shift sign bit out to do inf comparison.
    // (This works for ILP32 / LP64 as the sign bit is removed by the shift).
    // https://en.wikipedia.org/wiki/Single-precision_floating-point_format
    return float_as_unsigned(f) << 1 == 0xff800000 << 1;
}

// safe_sub_overflow is used ensure that subtraction occurs in the same native
// type with proper 2's complement overflow.  Without calling this function, it
// is possible, for example, that optimizing compilers may elect to treat 32 bit
// subtraction as 64 bit subtraction when storing into a 64 bit destination as
// integer overflow is technically undefined.
template <typename T, typename U,
          typename = std::enable_if_t<
              std::is_same<std::decay_t<T>, std::decay_t<U>>{}>>
// ensure arguments are same type (ignoring volatile, which is used in cblk
// variables).
auto safe_sub_overflow(const T& a, const U& b) {
  std::decay_t<T> result;
  (void)__builtin_sub_overflow(a, b, &result);
  // note if __builtin_sub_overflow returns true, an overflow occurred.
  return result;
}

// similar to safe_sub_overflow but for add operator.
template <typename T, typename U,
          typename = std::enable_if_t<
              std::is_same<std::decay_t<T>, std::decay_t<U>>{}>>
// ensure arguments are same type (ignoring volatile, which is used in cblk
// variables).
auto safe_add_overflow(const T& a, const U& b) {
  std::decay_t<T> result;
  (void)__builtin_add_overflow(a, b, &result);
  // note if __builtin_add_overflow returns true, an overflow occurred.
  return result;
}

} // namespace android::audio_utils
