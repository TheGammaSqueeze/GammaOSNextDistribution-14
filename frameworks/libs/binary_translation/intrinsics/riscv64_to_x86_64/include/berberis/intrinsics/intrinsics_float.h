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

#ifndef BERBERIS_INTRINSICS_RISCV64_TO_X86_64_INTRINSICS_FLOAT_H_
#define BERBERIS_INTRINSICS_RISCV64_TO_X86_64_INTRINSICS_FLOAT_H_

#include <limits>

#include "berberis/base/bit_util.h"
#include "berberis/intrinsics/common/intrinsics_float.h"
#include "berberis/intrinsics/common_to_x86/intrinsics_float.h"
#include "berberis/intrinsics/guest_fp_flags.h"        // ToHostRoundingMode
#include "berberis/intrinsics/guest_rounding_modes.h"  // ScopedRoundingMode
#include "berberis/intrinsics/type_traits.h"

namespace berberis::intrinsics {

// x86 architecture doesn't support RMM (aka FE_TIESAWAY), but it can be easily emulated since it
// have support for 80bit floats: if calculations are done with one bit (or more) of extra precision
// in the FE_TOWARDZERO mode then we can easily adjust fraction part and would only need to remember
// this addition may overflow.
template <typename FloatType, typename OperationType, typename... Args>
inline FloatType ExecuteFloatOperationRmm(OperationType operation, Args... args) {
  using Wide = typename TypeTraits<FloatType>::Wide;
  Wide wide_result = operation(static_cast<typename TypeTraits<Args>::Wide>(args)...);
  if constexpr (std::is_same_v<FloatType, Float32>) {
    // In the 32bit->64bit case everything happens almost automatically, we just need to clear low
    // bits to ensure that we are getting ±∞ and not NaN.
    auto int_result = bit_cast<std::make_unsigned_t<typename TypeTraits<Wide>::Int>>(wide_result);
    if ((int_result & 0x7ff0'0000'0000'0000) == 0x7ff0'0000'0000'0000) {
      return FloatType(wide_result);
    }
    int_result += 0x0000'0000'1000'0000;
    int_result &= 0xffff'ffff'e000'0000;
    wide_result = bit_cast<Wide>(int_result);
  } else if constexpr (std::is_same_v<FloatType, Float64>) {
    // In 64bit->80bit case we need to adjust significand bits to ensure we are creating ±∞ and not
    // pseudo-infinity (supported on 8087/80287, but not on modern CPUs).
    struct {
      uint64_t significand;
      uint16_t exponent;
      uint8_t padding[sizeof(Wide) - sizeof(uint64_t) - sizeof(uint16_t)];
    } fp80_parts;
    static_assert(sizeof fp80_parts == sizeof(Wide));
    memcpy(&fp80_parts, &wide_result, sizeof(wide_result));
    // Don't try to round ±∞, NaNs and ±0 (denormals are not supported by RISC-V).
    if ((fp80_parts.exponent & 0x7fff) == 0x7fff ||
        (fp80_parts.significand & 0x8000'0000'0000'0000) == 0) {
      return FloatType(wide_result);
    }
    fp80_parts.significand += 0x0000'0000'0000'0400;
    fp80_parts.significand &= 0xffff'ffff'ffff'f800;
    if (fp80_parts.significand == 0) {
      fp80_parts.exponent++;
      fp80_parts.significand = 0x8000'0000'0000'0000;
    }
    memcpy(&wide_result, &fp80_parts, sizeof(wide_result));
  }
  return FloatType(wide_result);
}

// Note: first round of rm/frm verification must happen before that function because RISC-V
// postulates that invalid rm or frm should trigger illegal instruction exception.
// Here we can assume both rm and frm fields are valid.
template <typename FloatType, typename OperationType, typename... Args>
inline FloatType ExecuteFloatOperation(uint8_t requested_rm,
                                       uint8_t current_rm,
                                       OperationType operation,
                                       Args... args) {
  int host_requested_rm = ToHostRoundingMode(requested_rm);
  int host_current_rm = ToHostRoundingMode(current_rm);
  if (requested_rm == FPFlags::DYN || host_requested_rm == host_current_rm) {
    uint8_t rm = requested_rm == FPFlags::DYN ? current_rm : requested_rm;
    if (rm == FPFlags::RMM) {
      return ExecuteFloatOperationRmm<FloatType>(operation, args...);
    }
    return operation(args...);
  }
  ScopedRoundingMode scoped_rounding_mode{host_requested_rm};
  if (requested_rm == FPFlags::RMM) {
    return ExecuteFloatOperationRmm<FloatType>(operation, args...);
  }
  return operation(args...);
}

// We only need Negative(long double) for FMA, b/120563432 doesn't affect this function.
inline long double Negative(const long double& v) {
  return -v;
}

inline long double Sqrt(const long double& v) {
  return sqrt(v);
}

inline long double MulAdd(const long double& v1, const long double& v2, const long double& v3) {
  return fma(v1, v2, v3);
}

}  // namespace berberis::intrinsics

#endif  // BERBERIS_INTRINSICS_RISCV64_TO_X86_64_INTRINSICS_FLOAT_H_
