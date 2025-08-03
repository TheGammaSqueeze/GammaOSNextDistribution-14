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

#ifndef BERBERIS_TESTS_INLINE_ASM_TESTS_UTILITY_H_
#define BERBERIS_TESTS_INLINE_ASM_TESTS_UTILITY_H_

#include <cstdint>
#include <cstring>
#include <tuple>

extern "C" uint64_t get_fp64_literal();

template <class Dest, class Source>
inline Dest bit_cast(const Source& source) {
  static_assert(sizeof(Dest) == sizeof(Source));
  Dest dest;
  memcpy(&dest, &source, sizeof(dest));
  return dest;
}

inline __uint128_t MakeF32x4(float f1, float f2, float f3, float f4) {
  float array[] = {f1, f2, f3, f4};
  return bit_cast<__uint128_t>(array);
}

inline __uint128_t MakeF64x2(double d1, double d2) {
  double array[] = {d1, d2};
  return bit_cast<__uint128_t>(array);
}

constexpr __uint128_t MakeUInt128(uint64_t low, uint64_t high) {
  return (static_cast<__uint128_t>(high) << 64) | static_cast<__uint128_t>(low);
}

constexpr __uint128_t MakeU32x4(uint32_t u0, uint32_t u1, uint32_t u2, uint32_t u3) {
  return (static_cast<__uint128_t>(u3) << 96) | (static_cast<__uint128_t>(u2) << 64) |
         (static_cast<__uint128_t>(u1) << 32) | static_cast<__uint128_t>(u0);
}

// Floating-point literals
constexpr uint32_t kOneF32 = 0x3f800000U;
constexpr uint64_t kOneF64 = 0x3ff0000000000000ULL;
constexpr uint32_t kDefaultNaN32 = 0x7fc00000U;
constexpr uint64_t kDefaultNaN64 = 0x7ff8000000000000ULL;
constexpr uint32_t kQuietNaN32 = kDefaultNaN32;
constexpr uint64_t kQuietNaN64 = kDefaultNaN64;
// There are multiple quiet and signaling NaNs. These are the ones that have the LSB "on".
constexpr uint32_t kSignalingNaN32_1 = 0x7f800001U;
constexpr uint64_t kSignalingNaN64_1 = 0x7ff0000000000001ULL;
constexpr uint32_t kQuietNaN32_1 = kQuietNaN32 | 1;
constexpr uint64_t kQuietNaN64_1 = kQuietNaN64 | 1;

constexpr uint32_t kFpcrFzBit = 1U << 24;
constexpr uint32_t kFpcrDnBit = 1U << 25;
constexpr uint32_t kFpcrRModeTieEven = 0b00U << 22;
constexpr uint32_t kFpcrRModePosInf = 0b01U << 22;
constexpr uint32_t kFpcrRModeNegInf = 0b10U << 22;
constexpr uint32_t kFpcrRModeZero = 0b11U << 22;
constexpr uint32_t kFpcrIdeBit = 1 << 15;
constexpr uint32_t kFpcrIxeBit = 1 << 12;
constexpr uint32_t kFpcrUfeBit = 1 << 11;
constexpr uint32_t kFpcrOfeBit = 1 << 10;
constexpr uint32_t kFpcrDzeBit = 1 << 9;
constexpr uint32_t kFpcrIoeBit = 1 << 8;

constexpr uint32_t kFpsrQcBit = 1U << 27;
constexpr uint32_t kFpsrIdcBit = 1 << 7;  // Input Denormal cumulative exception flag.
constexpr uint32_t kFpsrIxcBit = 1 << 4;  // Inexact cumulative exception flag.
constexpr uint32_t kFpsrUfcBit = 1 << 3;  // Underflow cumulative exception flag.
constexpr uint32_t kFpsrOfcBit = 1 << 2;  // Overflow cumulative exception flag.
constexpr uint32_t kFpsrDzcBit = 1 << 1;  // Division by Zero cumulative exception flag.
constexpr uint32_t kFpsrIocBit = 1 << 0;  // Invalid Operation cumulative exception flag.

#define ASM_INSN_WRAP_FUNC_W_RES(ASM) \
  []() -> __uint128_t {               \
    __uint128_t res;                  \
    asm(ASM : "=w"(res));             \
    return res;                       \
  }

#define ASM_INSN_WRAP_FUNC_R_RES_W_ARG(ASM) \
  [](__uint128_t arg) -> uint64_t {         \
    uint64_t res;                           \
    asm(ASM : "=r"(res) : "w"(arg));        \
    return res;                             \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_R_ARG(ASM) \
  [](uint64_t arg) -> __uint128_t {         \
    __uint128_t res;                        \
    asm(ASM : "=w"(res) : "r"(arg));        \
    return res;                             \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_W_ARG(ASM) \
  [](__uint128_t arg) -> __uint128_t {      \
    __uint128_t res;                        \
    asm(ASM : "=w"(res) : "w"(arg));        \
    return res;                             \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_WW_ARG(ASM)              \
  [](__uint128_t arg1, __uint128_t arg2) -> __uint128_t { \
    __uint128_t res;                                      \
    asm(ASM : "=w"(res) : "w"(arg1), "w"(arg2));          \
    return res;                                           \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_W0_ARG(ASM)              \
  [](__uint128_t arg1, __uint128_t arg2) -> __uint128_t { \
    __uint128_t res;                                      \
    asm(ASM : "=w"(res) : "w"(arg1), "0"(arg2));          \
    return res;                                           \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_WWW_ARG(ASM)                               \
  [](__uint128_t arg1, __uint128_t arg2, __uint128_t arg3) -> __uint128_t { \
    __uint128_t res;                                                        \
    asm(ASM : "=w"(res) : "w"(arg1), "w"(arg2), "w"(arg3));                 \
    return res;                                                             \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_WW0_ARG(ASM)                               \
  [](__uint128_t arg1, __uint128_t arg2, __uint128_t arg3) -> __uint128_t { \
    __uint128_t res;                                                        \
    asm(ASM : "=w"(res) : "w"(arg1), "w"(arg2), "0"(arg3));                 \
    return res;                                                             \
  }

// clang-format off
// We turn off clang-format here because it would place ASM like so:
//
//   asm("msr fpsr, xzr\n\t" ASM
//       "\n\t"
//       "mrs %1, fpsr"
//       : "=w"(res), "=r"(fpsr)
//       : "w"(arg));
#define ASM_INSN_WRAP_FUNC_WQ_RES_W_ARG(ASM)                 \
  [](__uint128_t arg) -> std::tuple<__uint128_t, uint32_t> { \
    __uint128_t res;                                         \
    uint64_t fpsr;                                           \
    asm("msr fpsr, xzr\n\t"                                  \
        ASM "\n\t"                                           \
        "mrs %1, fpsr"                                       \
        : "=w"(res), "=r"(fpsr)                              \
        : "w"(arg));                                         \
    return {res, fpsr};                                      \
  }

#define ASM_INSN_WRAP_FUNC_WQ_RES_W0_ARG(ASM)                                   \
  [](__uint128_t arg1, __uint128_t arg2) -> std::tuple<__uint128_t, uint32_t> { \
    __uint128_t res;                                                            \
    uint64_t fpsr;                                                              \
    asm("msr fpsr, xzr\n\t"                                                     \
        ASM "\n\t"                                                              \
        "mrs %1, fpsr"                                                          \
        : "=w"(res), "=r"(fpsr)                                                 \
        : "w"(arg1), "0"(arg2));                                                \
    return {res, fpsr};                                                         \
  }

#define ASM_INSN_WRAP_FUNC_WQ_RES_WW_ARG(ASM)                                   \
  [](__uint128_t arg1, __uint128_t arg2) -> std::tuple<__uint128_t, uint32_t> { \
    __uint128_t res;                                                            \
    uint64_t fpsr;                                                              \
    asm("msr fpsr, xzr\n\t"                                                     \
        ASM "\n\t"                                                              \
        "mrs %1, fpsr"                                                          \
        : "=w"(res), "=r"(fpsr)                                                 \
        : "w"(arg1), "w"(arg2));                                                \
    return {res, fpsr};                                                         \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_WC_ARG(ASM)            \
  [](__uint128_t arg, uint32_t fpcr) -> __uint128_t {   \
    __uint128_t res;                                    \
    asm("msr fpcr, %x2\n\t"                             \
        ASM "\n\t"                                      \
        "msr fpcr, xzr"                                 \
        : "=w"(res)                                     \
        : "w"(arg), "r"(fpcr));                         \
    return res;                                         \
  }

#define ASM_INSN_WRAP_FUNC_WQ_RES_WW0_ARG(ASM)                                   \
  [](__uint128_t arg1, __uint128_t arg2, __uint128_t arg3) -> std::tuple<__uint128_t, uint32_t> { \
    __uint128_t res;                                                            \
    uint64_t fpsr;                                                              \
    asm("msr fpsr, xzr\n\t"                                                     \
        ASM "\n\t"                                                              \
        "mrs %1, fpsr"                                                          \
        : "=w"(res), "=r"(fpsr)                                                 \
        : "w"(arg1), "w"(arg2), "0"(arg3));                                     \
    return {res, fpsr};                                                         \
  }

#define ASM_INSN_WRAP_FUNC_W_RES_WWC_ARG(ASM)                            \
  [](__uint128_t arg1, __uint128_t arg2, uint32_t fpcr) -> __uint128_t { \
    __uint128_t res;                                                     \
    asm("msr fpcr, %x3\n\t"                                              \
        ASM "\n\t"                                                       \
        "msr fpcr, xzr"                                                  \
        : "=w"(res)                                                      \
        : "w"(arg1), "w"(arg2), "r"(fpcr));                              \
    return res;                                                          \
  }

// clang-format on

#endif  // BERBERIS_TESTS_INLINE_ASM_TESTS_UTILITY_H_
