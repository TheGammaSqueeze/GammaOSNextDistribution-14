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

#ifndef RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_CONSTANTS_POOL_H_
#define RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_CONSTANTS_POOL_H_

#include <cinttypes>

#include "berberis/intrinsics/common/intrinsics_float.h"

namespace berberis::constants_pool {

// Vector constants, that is: constants are repeated to fill 128bit SIMD register.
template <auto Value>
extern const int32_t kVectorConst;
template <>
extern const int32_t kVectorConst<int8_t{-128}>;
template <>
extern const int32_t kVectorConst<int8_t{127}>;
template <>
extern const int32_t kVectorConst<int16_t{-0x8000}>;
template <>
extern const int32_t kVectorConst<int16_t{0x7fff}>;
template <>
extern const int32_t kVectorConst<int32_t{static_cast<int32_t>(-0x8000'0000)}>;
template <>
extern const int32_t kVectorConst<int32_t{0x7fff'ffff}>;
template <>
extern const int32_t kVectorConst<int64_t{static_cast<int64_t>(-0x8000'0000'0000'0000)}>;
template <>
extern const int32_t kVectorConst<int64_t{0x7fff'ffff'ffff'ffff}>;
template <>
extern const int32_t kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<int8_t{0x00}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<uint8_t{0x00}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<int16_t{0x0000}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<uint16_t{0x0000}> =
    kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<int32_t{0x0000'0000}> =
    kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<uint32_t{0x0000'0000}> =
    kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kVectorConst<int64_t{0x0000'0000'0000'0000}> =
    kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
extern const int32_t kVectorConst<uint64_t{0x7fc'00000'7fc'00000}>;
template <>
extern const int32_t kVectorConst<uint64_t{0x7ff8'0000'0000'0000}>;
template <>
extern const int32_t kVectorConst<uint64_t{0xffff'ffff'0000'0000}>;
template <>
extern const int32_t kVectorConst<uint64_t{0xffff'ffff'7fc0'0000}>;
template <>
extern const int32_t kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<int8_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<uint8_t{0xff}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<int16_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<uint16_t{0xffff}> =
    kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<int32_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<uint32_t{0xffff'ffff}> =
    kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kVectorConst<int64_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;

// 64 bit constants for use with arithmetic operations.
// Used because only 32 bit immediates are supported on x86-64.
template <auto Value>
extern const int32_t kConst;
template <>
extern const int32_t kConst<uint32_t{32}>;
template <>
extern const int32_t kConst<uint32_t{63}>;
template <>
extern const int32_t kConst<uint64_t{64}>;
template <>
extern const int32_t kConst<uint64_t{127}>;
template <>
extern const int32_t kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<int8_t{0x00}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<uint8_t{0x00}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<int16_t{0x0000}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<uint16_t{0x0000}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<int32_t{0x0000'0000}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<uint32_t{0x0000'0000}> = kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<int64_t{0x0000'0000'0000'0000}> =
    kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
inline const int32_t& kConst<uint64_t{0x0000'0000'0000'0000}> =
    kVectorConst<uint64_t{0x0000'0000'0000'0000}>;
template <>
extern const int32_t kConst<uint64_t{0x8000'0000'0000'00ff}>;
template <>
extern const int32_t kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<int8_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<uint8_t{0xff}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<int16_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<uint16_t{0xffff}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<int32_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<uint32_t{0xffff'ffff}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<int64_t{-1}> = kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;
template <>
inline const int32_t& kConst<uint64_t{0xffff'ffff'ffff'ffff}> =
    kVectorConst<uint64_t{0xffff'ffff'ffff'ffff}>;

// Constant suitable for NaN boxing of RISC-V 32bit float with PXor.
// Note: technically we only need to Nan-box Float32 since we don't support Float16 yet.
template <typename FloatType>
extern const int32_t kNanBox;
template <>
inline const int32_t& kNanBox<intrinsics::Float32> = kVectorConst<uint64_t{0xffff'ffff'0000'0000}>;

// Canonically Nan boxed canonical NaN.
// Note: technically we only need to Nan-box Float32 since we don't support Float16 yet.
template <typename FloatType>
extern const int32_t kNanBoxedNans;
template <>
inline const int32_t& kNanBoxedNans<intrinsics::Float32> =
    kVectorConst<uint64_t{0xffff'ffff'7fc0'0000}>;

// Canonical NaNs. Float32 and Float64 are supported.
template <typename FloatType>
extern const int32_t kCanonicalNans;
template <>
inline const int32_t& kCanonicalNans<intrinsics::Float32> =
    kVectorConst<uint64_t{0x7fc0'0000'7fc0'0000}>;
template <>
inline const int32_t& kCanonicalNans<intrinsics::Float64> =
    kVectorConst<uint64_t{0x7ff8'0000'0000'0000}>;

// Helper constant for BsrToClz conversion. 63 for int32_t, 127 for int64_t.
template <typename IntType>
extern const int32_t kBsrToClz;
template <>
inline const int32_t kBsrToClz<int32_t> = kConst<uint32_t{63}>;
template <>
inline const int32_t kBsrToClz<int64_t> = kConst<uint64_t{127}>;

// Helper constant for width of the type. 32 for int32_t, 64 for int64_t.
template <typename IntType>
extern const int32_t kWidthInBits;
template <>
inline const int32_t kWidthInBits<int32_t> = kConst<uint32_t{32}>;
template <>
inline const int32_t kWidthInBits<int64_t> = kConst<uint64_t{64}>;

extern const int32_t kRiscVToX87Exceptions;
extern const int32_t kX87ToRiscVExceptions;

extern const int32_t kVid64Bit;
extern const int32_t kVid32Bit;
extern const int32_t kVid16Bit;
extern const int32_t kVid8Bit;

extern const int32_t kBitMaskTable;
extern const int32_t kBitMaskTo32bitMask;
extern const int32_t kBitMaskTo16bitMask;
extern const int32_t kBitMaskTo8bitMask;

extern const int32_t kPMovmskwToPMovmskb;
extern const int32_t kPMovmskdToPMovmskb;
extern const int32_t kPMovmskqToPMovmskb;

}  // namespace berberis::constants_pool

#endif  // RISCV64_TO_X86_64_BERBERIS_INTRINSICS_MACRO_ASSEMBLER_CONSTANTS_POOL_H_
