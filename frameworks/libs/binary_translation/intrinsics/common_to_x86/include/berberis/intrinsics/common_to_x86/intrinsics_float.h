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

#ifndef COMMON_TO_X86_BERBERIS_INTRINSICS_INTRINSICS_FLOAT_H_
#define COMMON_TO_X86_BERBERIS_INTRINSICS_INTRINSICS_FLOAT_H_

#include <cmath>

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"
#include "berberis/intrinsics/common/intrinsics_float.h"  // Float32/Float64
#include "berberis/intrinsics/guest_rounding_modes.h"     // FE_HOSTROUND/FE_TIESAWAY

namespace berberis::intrinsics {

#define MAKE_BINARY_OPERATOR(guest_name, operator_name, assignment_name)                \
                                                                                        \
  inline Float32 operator operator_name(const Float32& v1, const Float32& v2) {         \
    Float32 result;                                                                     \
    asm(#guest_name "ss %2,%0" : "=x"(result.value_) : "0"(v1.value_), "x"(v2.value_)); \
    return result;                                                                      \
  }                                                                                     \
                                                                                        \
  inline Float32& operator assignment_name(Float32& v1, const Float32& v2) {            \
    asm(#guest_name "ss %2,%0" : "=x"(v1.value_) : "0"(v1.value_), "x"(v2.value_));     \
    return v1;                                                                          \
  }                                                                                     \
                                                                                        \
  inline Float64 operator operator_name(const Float64& v1, const Float64& v2) {         \
    Float64 result;                                                                     \
    asm(#guest_name "sd %2,%0" : "=x"(result.value_) : "0"(v1.value_), "x"(v2.value_)); \
    return result;                                                                      \
  }                                                                                     \
                                                                                        \
  inline Float64& operator assignment_name(Float64& v1, const Float64& v2) {            \
    asm(#guest_name "sd %2,%0" : "=x"(v1.value_) : "0"(v1.value_), "x"(v2.value_));     \
    return v1;                                                                          \
  }

MAKE_BINARY_OPERATOR(add, +, +=)
MAKE_BINARY_OPERATOR(sub, -, -=)
MAKE_BINARY_OPERATOR(mul, *, *=)
MAKE_BINARY_OPERATOR(div, /, /=)

#undef MAKE_BINARY_OPERATOR

inline bool operator<(const Float32& v1, const Float32& v2) {
  bool result;
  asm("ucomiss %1,%2\n seta %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator<(const Float64& v1, const Float64& v2) {
  bool result;
  asm("ucomisd %1,%2\n seta %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator>(const Float32& v1, const Float32& v2) {
  bool result;
  asm("ucomiss %2,%1\n seta %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator>(const Float64& v1, const Float64& v2) {
  bool result;
  asm("ucomisd %2,%1\n seta %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator<=(const Float32& v1, const Float32& v2) {
  bool result;
  asm("ucomiss %1,%2\n setnb %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator<=(const Float64& v1, const Float64& v2) {
  bool result;
  asm("ucomisd %1,%2\n setnb %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator>=(const Float32& v1, const Float32& v2) {
  bool result;
  asm("ucomiss %2,%1\n setnb %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator>=(const Float64& v1, const Float64& v2) {
  bool result;
  asm("ucomisd %2,%1\n setnb %0" : "=q"(result) : "x"(v1.value_), "x"(v2.value_) : "cc");
  return result;
}

inline bool operator==(const Float32& v1, const Float32& v2) {
  float result;
  asm("cmpeqss %2,%0" : "=x"(result) : "0"(v1.value_), "x"(v2.value_));
  return bit_cast<uint32_t, float>(result) & 0x1;
}

inline bool operator==(const Float64& v1, const Float64& v2) {
  double result;
  asm("cmpeqsd %2,%0" : "=x"(result) : "0"(v1.value_), "x"(v2.value_));
  return bit_cast<uint64_t, double>(result) & 0x1;
}

inline bool operator!=(const Float32& v1, const Float32& v2) {
  float result;
  asm("cmpneqss %2,%0" : "=x"(result) : "0"(v1.value_), "x"(v2.value_));
  return bit_cast<uint32_t, float>(result) & 0x1;
}

inline bool operator!=(const Float64& v1, const Float64& v2) {
  double result;
  asm("cmpneqsd %2,%0" : "=x"(result) : "0"(v1.value_), "x"(v2.value_));
  return bit_cast<uint64_t, double>(result) & 0x1;
}

// It's NOT safe to use ANY functions which return float or double.  That's because IA32 ABI uses
// x87 stack to pass arguments (and does that even with -mfpmath=sse) and NaN float and
// double values would be corrupted if pushed on it.
//
// It's safe to use builtins here if that file is compiled with -mfpmath=sse (clang does not have
// such flag but uses SSE whenever possible, GCC needs both -msse2 and -mfpmath=sse) since builtins
// DON'T use an official calling conventions but are instead embedded in the function - even if all
// optimizations are disabled.

inline Float32 CopySignBit(const Float32& v1, const Float32& v2) {
  return Float32(__builtin_copysignf(v1.value_, v2.value_));
}

inline Float64 CopySignBit(const Float64& v1, const Float64& v2) {
  return Float64(__builtin_copysign(v1.value_, v2.value_));
}

inline Float32 Absolute(const Float32& v) {
  return Float32(__builtin_fabsf(v.value_));
}

inline Float64 Absolute(const Float64& v) {
  return Float64(__builtin_fabs(v.value_));
}

inline Float32 Negative(const Float32& v) {
  // TODO(b/120563432): Simple -v.value_ doesn't work after a clang update.
  Float32 result;
  uint64_t sign_bit = 0x80000000U;
  asm("pxor %2, %0" : "=x"(result.value_) : "0"(v.value_), "x"(sign_bit));
  return result;
}

inline Float64 Negative(const Float64& v) {
  // TODO(b/120563432): Simple -v.value_ doesn't work after a clang update.
  Float64 result;
  uint64_t sign_bit = 0x8000000000000000ULL;
  asm("pxor %2, %0" : "=x"(result.value_) : "0"(v.value_), "x"(sign_bit));
  return result;
}

inline FPInfo FPClassify(const Float32& v) {
  return static_cast<FPInfo>(__builtin_fpclassify(static_cast<int>(FPInfo::kNaN),
                                                  static_cast<int>(FPInfo::kInfinite),
                                                  static_cast<int>(FPInfo::kNormal),
                                                  static_cast<int>(FPInfo::kSubnormal),
                                                  static_cast<int>(FPInfo::kZero),
                                                  v.value_));
}

inline FPInfo FPClassify(const Float64& v) {
  return static_cast<FPInfo>(__builtin_fpclassify(static_cast<int>(FPInfo::kNaN),
                                                  static_cast<int>(FPInfo::kInfinite),
                                                  static_cast<int>(FPInfo::kNormal),
                                                  static_cast<int>(FPInfo::kSubnormal),
                                                  static_cast<int>(FPInfo::kZero),
                                                  v.value_));
}

inline Float32 FPRound(const Float32& value, uint32_t round_control) {
  Float32 result;
  switch (round_control) {
    case FE_HOSTROUND:
      asm("roundss $4,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_TONEAREST:
      asm("roundss $0,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_DOWNWARD:
      asm("roundss $1,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_UPWARD:
      asm("roundss $2,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_TOWARDZERO:
      asm("roundss $3,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_TIESAWAY:
      // TODO(b/146437763): Might fail if value doesn't have a floating part.
      if (value == FPRound(value, FE_DOWNWARD) + Float32(0.5)) {
        result = value > Float32(0.0) ? FPRound(value, FE_UPWARD) : FPRound(value, FE_DOWNWARD);
      } else {
        result = FPRound(value, FE_TONEAREST);
      }
      break;
    default:
      LOG_ALWAYS_FATAL("Internal error: unknown round_control in FPRound!");
      result.value_ = 0.f;
  }
  return result;
}

inline Float64 FPRound(const Float64& value, uint32_t round_control) {
  Float64 result;
  switch (round_control) {
    case FE_HOSTROUND:
      asm("roundsd $4,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_TONEAREST:
      asm("roundsd $0,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_DOWNWARD:
      asm("roundsd $1,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_UPWARD:
      asm("roundsd $2,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_TOWARDZERO:
      asm("roundsd $3,%1,%0" : "=x"(result.value_) : "x"(value.value_));
      break;
    case FE_TIESAWAY:
      // Since x86 does not support this rounding mode exactly, we must manually handle the
      // tie-aways (from (-)x.5)
      if (value == FPRound(value, FE_DOWNWARD)) {
        // Value is already an integer and can be returned as-is. Checking this first avoids dealing
        // with numbers too large to be able to have a fractional part.
        return value;
      } else if (value == FPRound(value, FE_DOWNWARD) + Float64(0.5)) {
        // Fraction part is exactly 1/2, in which case we need to tie-away
        result = value > Float64(0.0) ? FPRound(value, FE_UPWARD) : FPRound(value, FE_DOWNWARD);
      } else {
        // Any other case can be handled by to-nearest rounding.
        result = FPRound(value, FE_TONEAREST);
      }
      break;
    default:
      LOG_ALWAYS_FATAL("Internal error: unknown round_control in FPRound!");
      result.value_ = 0.;
  }
  return result;
}

inline int IsNan(const Float32& v) {
  return __builtin_isnan(v.value_);
}

inline int IsNan(const Float64& v) {
  return __builtin_isnan(v.value_);
}

inline int SignBit(const Float32& v) {
  return __builtin_signbitf(v.value_);
}

inline int SignBit(const Float64& v) {
  return __builtin_signbit(v.value_);
}

inline Float32 Sqrt(const Float32& v) {
  return Float32(__builtin_sqrtf(v.value_));
}

inline Float64 Sqrt(const Float64& v) {
  return Float64(__builtin_sqrt(v.value_));
}

// x*y + z
inline Float32 MulAdd(const Float32& v1, const Float32& v2, const Float32& v3) {
  return Float32(fmaf(v1.value_, v2.value_, v3.value_));
}

inline Float64 MulAdd(const Float64& v1, const Float64& v2, const Float64& v3) {
  return Float64(fma(v1.value_, v2.value_, v3.value_));
}

}  // namespace berberis::intrinsics

#endif  // COMMON_TO_X86_BERBERIS_INTRINSICS_INTRINSICS_FLOAT_H_
